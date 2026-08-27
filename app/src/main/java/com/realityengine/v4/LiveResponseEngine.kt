package com.realityengine.v4

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.Executors

/** Token-efficient Groq response coach with tone guidance and local chosen-response detection. */
class LiveResponseEngine(private val settings: SettingsStore, private val context: ConversationContext, appContext: Context? = null) {
    data class Suggestion(val mode: String, val tone: String, val text: String, val reason: String = "")
    data class Result(val best: Suggestion, val alternatives: List<Suggestion>, val inputTokens: Int, val outputTokens: Int)
    data class ChosenResponse(val suggestion: Suggestion?, val confidence: Float, val classification: String)
    private data class RequestTicket(val generation: Long, val phoneNumber: String)

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val profileSession = appContext?.let { CallerProfileSession(it.applicationContext) }

    @Volatile private var inFlight = false
    @Volatile private var pendingAnalysis = false
    @Volatile private var sessionGeneration = 0L
    @Volatile private var lastCallerTurn = ""
    @Volatile private var callerTurnsSinceAnalysis = 0
    @Volatile private var activeSuggestions: List<Suggestion> = emptyList()
    @Volatile private var activePhoneNumber = ""
    @Volatile var lastChosenResponse: ChosenResponse? = null
        private set

    @Synchronized
    fun bindCaller(phoneNumber: String) {
        val clean = phoneNumber.trim()
        if (clean == activePhoneNumber) return
        activePhoneNumber = clean
        sessionGeneration++
        lastCallerTurn = ""
        callerTurnsSinceAnalysis = 0
        pendingAnalysis = false
        activeSuggestions = emptyList()
        lastChosenResponse = null
        profileSession?.bind(clean, context)
    }

    @Synchronized
    fun clearCaller() {
        activePhoneNumber = ""
        sessionGeneration++
        lastCallerTurn = ""
        callerTurnsSinceAnalysis = 0
        pendingAnalysis = false
        activeSuggestions = emptyList()
        lastChosenResponse = null
        profileSession?.clear()
    }

    fun onCallerTurn(text: String, callback: (Result?) -> Unit) {
        val clean = normalizeWhitespace(text)
        if (clean.length < 4) return

        var status: Pair<ResponseCoachState.Phase, String>? = null
        var shouldLaunch = false
        synchronized(this) {
            if (clean == lastCallerTurn) return
            lastCallerTurn = clean
            context.addTurn(ConversationContext.Speaker.CALLER, clean)
            callerTurnsSinceAnalysis++

            when {
                !settings.responseCoachEnabled -> {
                    callerTurnsSinceAnalysis = 0
                    status = ResponseCoachState.Phase.DISABLED to "Enable Response Coach in Settings"
                }
                !settings.groqConfigured() -> {
                    callerTurnsSinceAnalysis = 0
                    status = ResponseCoachState.Phase.KEY_REQUIRED to "Groq API key required"
                }
                callerTurnsSinceAnalysis < settings.analysisFrequencyTurns -> {
                    status = ResponseCoachState.Phase.LISTENING to
                        "Caller turn $callerTurnsSinceAnalysis/${settings.analysisFrequencyTurns}"
                }
                inFlight -> {
                    callerTurnsSinceAnalysis = 0
                    pendingAnalysis = true
                    status = ResponseCoachState.Phase.ANALYZING to "New caller turn queued"
                }
                else -> {
                    callerTurnsSinceAnalysis = 0
                    shouldLaunch = true
                }
            }
        }

        status?.let { (phase, message) ->
            ResponseCoachState.publishStatus(phase, message, clearSuggestions = phase != ResponseCoachState.Phase.ANALYZING)
            if (phase != ResponseCoachState.Phase.ANALYZING) main.post { callback(null) }
        }
        if (shouldLaunch) launchAnalysis(callback)
    }

    fun onUserTurn(text: String): ChosenResponse {
        val clean = normalizeWhitespace(text)
        context.addTurn(ConversationContext.Speaker.USER, clean)
        val match = matchSuggestion(clean, activeSuggestions)
        lastChosenResponse = match
        if (match.suggestion != null) {
            context.rememberFact("Last response strategy: ${match.suggestion.mode}; tone: ${match.suggestion.tone}; match: ${match.classification}")
        }
        ResponseCoachState.publishChosen(match)
        ResponseCoachState.clearSuggestions()
        activeSuggestions = emptyList()
        return match
    }

    private fun launchAnalysis(callback: (Result?) -> Unit) {
        val ticket = synchronized(this) {
            if (inFlight) {
                pendingAnalysis = true
                return
            }
            inFlight = true
            RequestTicket(sessionGeneration, activePhoneNumber)
        }
        ResponseCoachState.publishStatus(
            ResponseCoachState.Phase.ANALYZING,
            "Generating replies…",
            clearSuggestions = true
        )
        executor.execute { executeAnalysis(ticket, callback) }
    }

    private fun executeAnalysis(ticket: RequestTicket, callback: (Result?) -> Unit) {
        var result: Result? = null
        var failure: String? = null
        val currentAtStart = isTicketCurrent(ticket)
        if (currentAtStart) {
            try {
                if (ticket.phoneNumber.isNotBlank()) profileSession?.refresh(ticket.phoneNumber, context)
                result = requestSuggestions()
            } catch (t: Throwable) {
                failure = coachFailure(t)
            }
        }

        val stillCurrent = isTicketCurrent(ticket)
        if (stillCurrent) {
            if (result != null) {
                activeSuggestions = listOf(result!!.best) + result!!.alternatives
                ResponseCoachState.publish(result!!)
            } else if (failure != null) {
                ResponseCoachState.publishError(failure!!)
            }
            main.post { callback(result) }
        }

        val nextTicket = synchronized(this) {
            if (pendingAnalysis && settings.responseCoachEnabled && settings.groqConfigured()) {
                pendingAnalysis = false
                RequestTicket(sessionGeneration, activePhoneNumber)
            } else {
                pendingAnalysis = false
                inFlight = false
                null
            }
        }

        if (nextTicket != null) {
            ResponseCoachState.publishStatus(
                ResponseCoachState.Phase.ANALYZING,
                "Refreshing for latest caller turn…",
                clearSuggestions = true
            )
            executor.execute { executeAnalysis(nextTicket, {}) }
        }
    }

    @Synchronized
    private fun isTicketCurrent(ticket: RequestTicket): Boolean =
        ticket.generation == sessionGeneration && ticket.phoneNumber == activePhoneNumber

    private fun matchSuggestion(spoken: String, suggestions: List<Suggestion>): ChosenResponse {
        if (spoken.isBlank() || suggestions.isEmpty()) return ChosenResponse(null, 0f, "OWN_RESPONSE")
        var best: Suggestion? = null
        var bestScore = 0f
        for (candidate in suggestions) {
            val score = similarity(spoken, candidate.text)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return when {
            bestScore >= .72f -> ChosenResponse(best, bestScore, "FOLLOWED")
            bestScore >= .42f -> ChosenResponse(best, bestScore, "MODIFIED")
            else -> ChosenResponse(null, bestScore, "OWN_RESPONSE")
        }
    }

    private fun similarity(a: String, b: String): Float {
        val aa = tokens(a)
        val bb = tokens(b)
        if (aa.isEmpty() || bb.isEmpty()) return 0f
        val intersection = aa.intersect(bb).size.toFloat()
        val union = aa.union(bb).size.toFloat().coerceAtLeast(1f)
        val jaccard = intersection / union
        val containment = intersection / minOf(aa.size, bb.size).toFloat().coerceAtLeast(1f)
        val phraseBonus = if (normalizeForMatch(a).contains(normalizeForMatch(b)) || normalizeForMatch(b).contains(normalizeForMatch(a))) .20f else 0f
        return (jaccard * .55f + containment * .45f + phraseBonus).coerceIn(0f, 1f)
    }

    private fun tokens(value: String): Set<String> =
        normalizeForMatch(value).split(' ').filter { it.length > 1 && it !in STOP }.toSet()

    private fun normalizeForMatch(value: String) = value.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9' ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeWhitespace(value: String) = value.trim().replace(Regex("\\s+"), " ")

    private fun requestSuggestions(): Result {
        val snapshot = context.snapshot()
        val connection = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7_000
            connection.readTimeout = 9_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${settings.groqApiKey}")
            connection.setRequestProperty("Content-Type", "application/json")

            val system = """You are a live phone-call response coach. Suggest concise natural replies for the USER to say to the CALLER. Personalize only from supplied caller profile/context; never invent facts. For each reply choose a strategy and a short delivery tone. Strategies: BONDING, CLARIFY, MIRROR, PIVOT, COGNITIVE_PROBE. Tone examples: warm/relaxed, calm/curious, neutral/firm, light/playful, slow/deliberate. Return JSON only: {\"best\":{\"mode\":\"...\",\"tone\":\"...\",\"text\":\"...\",\"reason\":\"...\"},\"alternatives\":[{\"mode\":\"...\",\"tone\":\"...\",\"text\":\"...\",\"reason\":\"...\"}]}. Keep each spoken reply under 24 words and each reason under 12 words."""
            val body = JSONObject().apply {
                put("model", settings.groqModel)
                put("temperature", .35)
                put("max_completion_tokens", 190)
                put("response_format", JSONObject().put("type", "json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", snapshot.asPromptContext()))
                })
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException(
                    when (code) {
                        401 -> "GROQ AUTH FAILED // CHECK API KEY"
                        403 -> "GROQ ACCESS DENIED // CHECK MODEL PERMISSION"
                        404 -> "GROQ MODEL/ENDPOINT NOT FOUND"
                        429 -> "GROQ RATE LIMITED // TRY AGAIN"
                        in 500..599 -> "GROQ SERVER ERROR // HTTP $code"
                        else -> "GROQ ERROR // HTTP $code"
                    }
                )
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val root = JSONObject(response)
            val content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            val parsed = JSONObject(content)

            fun suggestion(item: JSONObject) = Suggestion(
                item.optString("mode", "CLARIFY"),
                item.optString("tone", "calm/curious").take(48),
                item.optString("text").trim().take(180),
                item.optString("reason").trim().take(100)
            )

            val best = suggestion(parsed.getJSONObject("best"))
            if (best.text.isBlank()) throw IllegalStateException("GROQ RESPONSE INVALID // EMPTY REPLY")
            val array = parsed.optJSONArray("alternatives") ?: JSONArray()
            val alternatives = buildList {
                for (i in 0 until minOf(array.length(), 2)) {
                    val candidate = suggestion(array.getJSONObject(i))
                    if (candidate.text.isNotBlank()) add(candidate)
                }
            }
            val usage = root.optJSONObject("usage")
            return Result(
                best,
                alternatives,
                usage?.optInt("prompt_tokens", snapshot.estimatedTokens) ?: snapshot.estimatedTokens,
                usage?.optInt("completion_tokens", 0) ?: 0
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun coachFailure(t: Throwable): String = when (t) {
        is SocketTimeoutException -> "GROQ TIMEOUT // WILL RETRY ON NEXT CALLER TURN"
        is UnknownHostException -> "NETWORK ERROR // GROQ UNREACHABLE"
        else -> t.message?.takeIf { it.isNotBlank() }?.take(180)
            ?: "RESPONSE COACH ERROR // ${t.javaClass.simpleName}"
    }

    companion object {
        private val STOP = setOf("the", "a", "an", "and", "or", "but", "i", "you", "we", "it", "is", "are", "was", "were", "to", "of", "in", "on", "for", "that", "this", "my", "your")
    }
}

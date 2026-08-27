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

/** Token-efficient multi-provider response coach with local chosen-response detection. */
class LiveResponseEngine(private val settings: SettingsStore, private val context: ConversationContext, appContext: Context? = null) {
    data class Suggestion(val mode: String, val tone: String, val text: String, val reason: String = "")
    data class Result(
        val best: Suggestion,
        val alternatives: List<Suggestion>,
        val inputTokens: Int,
        val outputTokens: Int,
        val model: String = SettingsStore.DEFAULT_GROQ_MODEL
    )
    data class ChosenResponse(val suggestion: Suggestion?, val confidence: Float, val classification: String)
    private data class RequestTicket(val generation: Long, val phoneNumber: String, val quickModeId: String? = null)
    private class GroqHttpException(val code: Int, message: String) : IllegalStateException(message)

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val profileSession = appContext?.let { CallerProfileSession(it.applicationContext) }
    private val gemini = GeminiCoachClient(settings)

    @Volatile private var inFlight = false
    @Volatile private var pendingAnalysis = false
    @Volatile private var pendingQuickModeId: String? = null
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
        pendingQuickModeId = null
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
        pendingQuickModeId = null
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
                !settings.coachConfigured() -> {
                    callerTurnsSinceAnalysis = 0
                    status = ResponseCoachState.Phase.KEY_REQUIRED to missingProviderMessage()
                }
                callerTurnsSinceAnalysis < settings.analysisFrequencyTurns -> {
                    status = ResponseCoachState.Phase.LISTENING to "Caller turn $callerTurnsSinceAnalysis/${settings.analysisFrequencyTurns}"
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

    /** Force a one-shot coach refresh using the current transcript/context and a temporary delivery mode. */
    fun requestQuickMode(modeId: String, callback: (Result?) -> Unit = {}) {
        val mode = CoachQuickModeCatalog.byId(modeId) ?: return
        var status: Pair<ResponseCoachState.Phase, String>? = null
        var shouldLaunch = false
        synchronized(this) {
            when {
                !settings.responseCoachEnabled -> status = ResponseCoachState.Phase.DISABLED to "Enable Response Coach in Settings"
                !settings.coachConfigured() -> status = ResponseCoachState.Phase.KEY_REQUIRED to missingProviderMessage()
                inFlight -> {
                    pendingAnalysis = true
                    pendingQuickModeId = mode.id
                    status = ResponseCoachState.Phase.ANALYZING to "${mode.label} refresh queued"
                }
                else -> shouldLaunch = true
            }
        }
        status?.let { (phase, message) ->
            ResponseCoachState.publishStatus(phase, message, clearSuggestions = phase != ResponseCoachState.Phase.ANALYZING)
            if (phase != ResponseCoachState.Phase.ANALYZING) main.post { callback(null) }
        }
        if (shouldLaunch) launchAnalysis(callback, mode.id)
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

    private fun launchAnalysis(callback: (Result?) -> Unit, quickModeId: String? = null) {
        val ticket = synchronized(this) {
            if (inFlight) {
                pendingAnalysis = true
                if (quickModeId != null) pendingQuickModeId = quickModeId
                return
            }
            inFlight = true
            RequestTicket(sessionGeneration, activePhoneNumber, quickModeId)
        }
        val quick = CoachQuickModeCatalog.byId(ticket.quickModeId)
        ResponseCoachState.publishGroqRateLimit(settings.coachModelLabel(), null, null, null)
        ResponseCoachState.publishStatus(
            ResponseCoachState.Phase.ANALYZING,
            quick?.let { "Generating ${it.label.lowercase()} replies…" } ?: "Generating strategy replies…",
            clearSuggestions = true
        )
        executor.execute { executeAnalysis(ticket, callback) }
    }

    private fun executeAnalysis(ticket: RequestTicket, callback: (Result?) -> Unit) {
        var result: Result? = null
        var failure: String? = null
        if (isTicketCurrent(ticket)) {
            try {
                if (ticket.phoneNumber.isNotBlank()) profileSession?.refresh(ticket.phoneNumber, context)
                result = requestSuggestions(ticket.quickModeId)
            } catch (t: Throwable) {
                failure = coachFailure(t)
            }
        }
        if (isTicketCurrent(ticket)) {
            if (result != null) {
                activeSuggestions = listOf(result!!.best) + result!!.alternatives
                ResponseCoachState.publish(result!!)
            } else if (failure != null) {
                ResponseCoachState.publishError(failure!!)
            }
            main.post { callback(result) }
        }
        val nextTicket = synchronized(this) {
            if (pendingAnalysis && settings.responseCoachEnabled && settings.coachConfigured()) {
                pendingAnalysis = false
                val quick = pendingQuickModeId
                pendingQuickModeId = null
                RequestTicket(sessionGeneration, activePhoneNumber, quick)
            } else {
                pendingAnalysis = false
                pendingQuickModeId = null
                inFlight = false
                null
            }
        }
        if (nextTicket != null) {
            val quick = CoachQuickModeCatalog.byId(nextTicket.quickModeId)
            ResponseCoachState.publishStatus(
                ResponseCoachState.Phase.ANALYZING,
                quick?.let { "Refreshing ${it.label.lowercase()} replies…" } ?: "Refreshing for latest caller turn…",
                clearSuggestions = true
            )
            executor.execute { executeAnalysis(nextTicket, {}) }
        }
    }

    @Synchronized
    private fun isTicketCurrent(ticket: RequestTicket): Boolean =
        ticket.generation == sessionGeneration && ticket.phoneNumber == activePhoneNumber

    private fun requestSuggestions(quickModeId: String?): Result {
        val snapshot = context.snapshot()
        return when (settings.coachProvider) {
            SettingsStore.COACH_PROVIDER_GEMINI -> gemini.request(snapshot, quickModeId)
            SettingsStore.COACH_PROVIDER_GROQ -> requestGroq(snapshot, quickModeId)
            else -> requestAuto(snapshot, quickModeId)
        }
    }

    private fun requestAuto(snapshot: ConversationContext.Snapshot, quickModeId: String?): Result {
        var groqFailure: Throwable? = null
        if (settings.groqConfigured()) {
            try {
                return requestGroq(snapshot, quickModeId)
            } catch (t: Throwable) {
                groqFailure = t
            }
        }
        if (settings.geminiConfigured()) {
            try {
                return gemini.request(snapshot, quickModeId)
            } catch (geminiFailure: Throwable) {
                val first = groqFailure?.message?.take(70)?.let { "Groq: $it · " }.orEmpty()
                throw IllegalStateException("AUTO FAILOVER FAILED // ${first}Gemini: ${geminiFailure.message.orEmpty().take(90)}")
            }
        }
        throw groqFailure ?: IllegalStateException("NO RESPONSE COACH PROVIDER CONFIGURED")
    }

    private fun requestGroq(snapshot: ConversationContext.Snapshot, quickModeId: String?): Result {
        if (!settings.groqConfigured()) throw IllegalStateException("GROQ API KEY REQUIRED")
        val model = settings.groqModel
        val connection = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7_000
            connection.readTimeout = 9_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${settings.groqApiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            val strategyGuide = ResponseStrategyCatalog.promptGuide()
            val quickMode = CoachQuickModeCatalog.byId(quickModeId)
            val quickInstruction = quickMode?.let { "\n\nONE-SHOT QUICK MODE — ${it.label.uppercase()}: ${it.promptInstruction}" }.orEmpty()
            val system = """You are a live phone-call response coach. Suggest concise, natural replies for the USER to say to the CALLER. Personalize only from supplied caller profile/context; never invent facts. Choose exactly five DISTINCT strategies from the catalog below that best fit the current moment. Rank them: one BEST choice plus four alternatives. Do not force a strategy when it does not fit. Keep suggestions non-coercive: do not manipulate, threaten, shame, pressure, or fabricate. COGNITIVE_PROBE must remain a neutral question, never a trap.

STRATEGY CATALOG:
$strategyGuide

Every choice must include a short delivery tone such as warm/relaxed, calm/curious, neutral/firm, light/playful, slow/deliberate, or steady/direct. Return JSON only: {\"best\":{\"mode\":\"...\",\"tone\":\"...\",\"text\":\"...\",\"reason\":\"...\"},\"alternatives\":[{\"mode\":\"...\",\"tone\":\"...\",\"text\":\"...\",\"reason\":\"...\"}]}. Keep each spoken reply under 18 words and each reason under 6 words. No commentary outside the JSON.$quickInstruction"""
            val body = JSONObject().apply {
                put("model", model)
                put("temperature", quickMode?.temperature ?: .25)
                put("max_completion_tokens", 360)
                put("response_format", JSONObject().put("type", "json_object"))
                if (model.startsWith("openai/gpt-oss-")) put("reasoning_effort", "low")
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", snapshot.asPromptContext()))
                })
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            publishRateLimitHeaders(connection, model)
            if (code !in 200..299) {
                val detail = readGroqError(connection)
                throw GroqHttpException(code, groqHttpMessage(code, detail))
            }
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val root = JSONObject(response)
            val content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            val parsed = JSONObject(content)
            fun suggestion(item: JSONObject) = Suggestion(
                ResponseStrategyCatalog.normalizeMode(item.optString("mode", "CLARIFY")),
                item.optString("tone", "calm/curious").take(48),
                item.optString("text").trim().take(180),
                item.optString("reason").trim().take(100)
            )
            val best = suggestion(parsed.getJSONObject("best"))
            if (best.text.isBlank()) throw IllegalStateException("GROQ RESPONSE INVALID // EMPTY REPLY")
            val array = parsed.optJSONArray("alternatives") ?: JSONArray()
            val seenModes = linkedSetOf(best.mode)
            val alternatives = buildList {
                for (i in 0 until array.length()) {
                    if (size >= 4) break
                    val candidate = suggestion(array.getJSONObject(i))
                    if (candidate.text.isNotBlank() && seenModes.add(candidate.mode)) add(candidate)
                }
            }
            val usage = root.optJSONObject("usage")
            return Result(
                best = best,
                alternatives = alternatives,
                inputTokens = usage?.optInt("prompt_tokens", snapshot.estimatedTokens) ?: snapshot.estimatedTokens,
                outputTokens = usage?.optInt("completion_tokens", 0) ?: 0,
                model = model
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun publishRateLimitHeaders(connection: HttpURLConnection, model: String) {
        fun intHeader(name: String): Int? = connection.getHeaderField(name)?.trim()?.toIntOrNull()
        ResponseCoachState.publishGroqRateLimit(
            model = model,
            remainingTokens = intHeader("x-ratelimit-remaining-tokens"),
            limitTokens = intHeader("x-ratelimit-limit-tokens"),
            resetTokens = connection.getHeaderField("x-ratelimit-reset-tokens")?.trim()
        )
    }

    private fun readGroqError(connection: HttpURLConnection): String = runCatching {
        val raw = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (raw.isBlank()) return@runCatching ""
        val root = JSONObject(raw)
        root.optJSONObject("error")?.optString("message").orEmpty().ifBlank { raw }
    }.getOrDefault("").replace(Regex("\\s+"), " ").take(180)

    private fun groqHttpMessage(code: Int, detail: String): String {
        val prefix = when (code) {
            400 -> "GROQ REQUEST REJECTED"
            401 -> "GROQ AUTH FAILED"
            403 -> "GROQ ACCESS DENIED"
            404 -> "GROQ MODEL/ENDPOINT NOT FOUND"
            429 -> "GROQ RATE LIMITED"
            in 500..599 -> "GROQ SERVER ERROR"
            else -> "GROQ ERROR // HTTP $code"
        }
        return if (detail.isBlank()) "$prefix // HTTP $code" else "$prefix // ${detail.take(150)}"
    }

    private fun missingProviderMessage(): String = when (settings.coachProvider) {
        SettingsStore.COACH_PROVIDER_GROQ -> "Groq API key required"
        SettingsStore.COACH_PROVIDER_GEMINI -> "Gemini API key required"
        else -> "Add a Groq or Gemini API key"
    }

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

    private fun coachFailure(t: Throwable): String = when (t) {
        is SocketTimeoutException -> "AI TIMEOUT // WILL RETRY ON NEXT CALLER TURN"
        is UnknownHostException -> "NETWORK ERROR // COACH PROVIDER UNREACHABLE"
        else -> t.message?.takeIf { it.isNotBlank() }?.take(180)
            ?: "RESPONSE COACH ERROR // ${t.javaClass.simpleName}"
    }

    companion object {
        private val STOP = setOf("the", "a", "an", "and", "or", "but", "i", "you", "we", "it", "is", "are", "was", "were", "to", "of", "in", "on", "for", "that", "this", "my", "your")
    }
}

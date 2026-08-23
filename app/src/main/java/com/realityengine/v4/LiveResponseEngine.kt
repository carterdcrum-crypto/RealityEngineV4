package com.realityengine.v4

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/** Token-efficient Groq response coach with tone guidance and local chosen-response detection. */
class LiveResponseEngine(
    private val settings: SettingsStore,
    private val context: ConversationContext
) {
    data class Suggestion(val mode: String, val tone: String, val text: String, val reason: String = "")
    data class Result(val best: Suggestion, val alternatives: List<Suggestion>, val inputTokens: Int, val outputTokens: Int)
    data class ChosenResponse(val suggestion: Suggestion?, val confidence: Float, val classification: String)

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var inFlight = false
    @Volatile private var lastCallerTurn = ""
    @Volatile private var callerTurnsSinceAnalysis = 0
    @Volatile private var activeSuggestions: List<Suggestion> = emptyList()
    @Volatile var lastChosenResponse: ChosenResponse? = null
        private set

    fun onCallerTurn(text: String, callback: (Result?) -> Unit) {
        val clean = normalizeWhitespace(text)
        if (clean.length < 4 || clean == lastCallerTurn) return
        lastCallerTurn = clean
        context.addTurn(ConversationContext.Speaker.CALLER, clean)
        callerTurnsSinceAnalysis++
        if (!settings.responseCoachEnabled || !settings.groqConfigured()) return
        if (inFlight || callerTurnsSinceAnalysis < settings.analysisFrequencyTurns) return
        callerTurnsSinceAnalysis = 0
        inFlight = true
        executor.execute {
            val result = try { requestSuggestions() } catch (_: Throwable) { null }
            if (result != null) {
                activeSuggestions = listOf(result.best) + result.alternatives
                ResponseCoachState.publish(result)
            }
            inFlight = false
            main.post { callback(result) }
        }
    }

    fun onUserTurn(text: String): ChosenResponse {
        val clean = normalizeWhitespace(text)
        context.addTurn(ConversationContext.Speaker.USER, clean)
        val match = matchSuggestion(clean, activeSuggestions)
        lastChosenResponse = match
        if (match.suggestion != null) context.rememberFact("Last response strategy: ${match.suggestion.mode}; tone: ${match.suggestion.tone}; match: ${match.classification}")
        ResponseCoachState.publishChosen(match)
        ResponseCoachState.clearSuggestions()
        activeSuggestions = emptyList()
        return match
    }

    private fun matchSuggestion(spoken: String, suggestions: List<Suggestion>): ChosenResponse {
        if (spoken.isBlank() || suggestions.isEmpty()) return ChosenResponse(null, 0f, "OWN_RESPONSE")
        var best: Suggestion? = null; var bestScore = 0f
        for (candidate in suggestions) {
            val score = similarity(spoken, candidate.text)
            if (score > bestScore) { bestScore = score; best = candidate }
        }
        return when {
            bestScore >= .72f -> ChosenResponse(best, bestScore, "FOLLOWED")
            bestScore >= .42f -> ChosenResponse(best, bestScore, "MODIFIED")
            else -> ChosenResponse(null, bestScore, "OWN_RESPONSE")
        }
    }

    private fun similarity(a: String, b: String): Float {
        val aa = tokens(a); val bb = tokens(b)
        if (aa.isEmpty() || bb.isEmpty()) return 0f
        val intersection = aa.intersect(bb).size.toFloat()
        val union = aa.union(bb).size.toFloat().coerceAtLeast(1f)
        val jaccard = intersection / union
        val containment = intersection / minOf(aa.size, bb.size).toFloat().coerceAtLeast(1f)
        val phraseBonus = if (normalizeForMatch(a).contains(normalizeForMatch(b)) || normalizeForMatch(b).contains(normalizeForMatch(a))) .20f else 0f
        return (jaccard * .55f + containment * .45f + phraseBonus).coerceIn(0f, 1f)
    }

    private fun tokens(value: String): Set<String> = normalizeForMatch(value).split(' ').filter { it.length > 1 && it !in STOP }.toSet()
    private fun normalizeForMatch(value: String) = value.lowercase(Locale.US).replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
    private fun normalizeWhitespace(value: String) = value.trim().replace(Regex("\\s+"), " ")

    private fun requestSuggestions(): Result? {
        val snapshot = context.snapshot()
        val connection = (URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod="POST";connectTimeout=7000;readTimeout=9000;doOutput=true
            setRequestProperty("Authorization","Bearer ${settings.groqApiKey}");setRequestProperty("Content-Type","application/json")
        }
        val system = """You are a live phone-call response coach. Suggest concise natural replies for the USER to say to the CALLER. Use only supplied context; never invent facts. For each reply choose a strategy and a short delivery tone. Strategies: BONDING, CLARIFY, MIRROR, PIVOT, COGNITIVE_PROBE. Tone examples: warm/relaxed, calm/curious, neutral/firm, light/playful, slow/deliberate. Return JSON only: {\"best\":{\"mode\":\"...\",\"tone\":\"...\",\"text\":\"...\",\"reason\":\"...\"},\"alternatives\":[{\"mode\":\"...\",\"tone\":\"...\",\"text\":\"...\",\"reason\":\"...\"}]}. Keep each spoken reply under 24 words and each reason under 12 words."""
        val body=JSONObject().apply{put("model",settings.groqModel);put("temperature",.35);put("max_completion_tokens",190);put("response_format",JSONObject().put("type","json_object"));put("messages",JSONArray().apply{put(JSONObject().put("role","system").put("content",system));put(JSONObject().put("role","user").put("content",snapshot.asPromptContext()))})}
        connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
        if(connection.responseCode !in 200..299){connection.disconnect();return null}
        val response=BufferedReader(InputStreamReader(connection.inputStream)).use{it.readText()};connection.disconnect();val root=JSONObject(response);val content=root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");val parsed=JSONObject(content)
        fun suggestion(item:JSONObject)=Suggestion(item.optString("mode","CLARIFY"),item.optString("tone","calm/curious").take(48),item.optString("text").take(180),item.optString("reason").take(100))
        val best=suggestion(parsed.getJSONObject("best"));val array=parsed.optJSONArray("alternatives")?:JSONArray();val alternatives=buildList{for(i in 0 until minOf(array.length(),2))add(suggestion(array.getJSONObject(i)))};val usage=root.optJSONObject("usage")
        return Result(best,alternatives,usage?.optInt("prompt_tokens",snapshot.estimatedTokens)?:snapshot.estimatedTokens,usage?.optInt("completion_tokens",0)?:0)
    }

    companion object { private val STOP=setOf("the","a","an","and","or","but","i","you","we","it","is","are","was","were","to","of","in","on","for","that","this","my","your") }
}

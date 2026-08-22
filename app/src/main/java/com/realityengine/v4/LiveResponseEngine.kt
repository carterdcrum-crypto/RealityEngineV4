package com.realityengine.v4

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Token-efficient Groq response coach.
 * Calls the model only after a meaningful caller turn and sends the compact
 * ConversationContext snapshot instead of the full transcript.
 */
class LiveResponseEngine(
    private val settings: SettingsStore,
    private val context: ConversationContext
) {
    data class Suggestion(val mode: String, val text: String)
    data class Result(
        val best: Suggestion,
        val alternatives: List<Suggestion>,
        val inputTokens: Int,
        val outputTokens: Int
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var inFlight = false
    @Volatile private var lastCallerTurn = ""
    @Volatile private var lastRequestAt = 0L

    fun onCallerTurn(text: String, callback: (Result?) -> Unit) {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.length < 4 || clean == lastCallerTurn) return
        context.addTurn(ConversationContext.Speaker.CALLER, clean)
        if (!settings.responseCoachEnabled || !settings.groqConfigured()) return

        val now = System.currentTimeMillis()
        val cooldown = settings.analysisFrequencySeconds * 1000L
        if (inFlight || now - lastRequestAt < cooldown) return
        lastCallerTurn = clean
        lastRequestAt = now
        inFlight = true

        executor.execute {
            val result = try { requestSuggestions() } catch (_: Throwable) { null }
            inFlight = false
            main.post { callback(result) }
        }
    }

    fun onUserTurn(text: String) {
        context.addTurn(ConversationContext.Speaker.USER, text)
    }

    private fun requestSuggestions(): Result? {
        val snapshot = context.snapshot()
        val connection = (URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7000
            readTimeout = 9000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${settings.groqApiKey}")
            setRequestProperty("Content-Type", "application/json")
        }

        val system = """You are a live phone-call response coach. Suggest concise natural replies for the USER to say to the CALLER. Use only supplied context. Do not invent facts. Return JSON only: {\"best\":{\"mode\":\"BONDING|CLARIFY|MIRROR|PIVOT\",\"text\":\"...\"},\"alternatives\":[{\"mode\":\"...\",\"text\":\"...\"},{\"mode\":\"...\",\"text\":\"...\"}]}. Keep every reply under 24 words."""
        val body = JSONObject().apply {
            put("model", settings.groqModel)
            put("temperature", 0.35)
            put("max_completion_tokens", 140)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", snapshot.asPromptContext()))
            })
        }

        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            return null
        }
        val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        connection.disconnect()
        val root = JSONObject(response)
        val content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        val parsed = JSONObject(content)
        val bestJson = parsed.getJSONObject("best")
        val alternativesJson = parsed.optJSONArray("alternatives") ?: JSONArray()
        val alternatives = buildList {
            for (i in 0 until minOf(alternativesJson.length(), 2)) {
                val item = alternativesJson.getJSONObject(i)
                add(Suggestion(item.optString("mode", "CLARIFY"), item.optString("text").take(180)))
            }
        }
        val usage = root.optJSONObject("usage")
        return Result(
            Suggestion(bestJson.optString("mode", "CLARIFY"), bestJson.optString("text").take(180)),
            alternatives,
            usage?.optInt("prompt_tokens", snapshot.estimatedTokens) ?: snapshot.estimatedTokens,
            usage?.optInt("completion_tokens", 0) ?: 0
        )
    }
}

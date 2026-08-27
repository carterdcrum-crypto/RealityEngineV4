package com.realityengine.v4

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Background, post-call memory extraction using the same provider selection the live coach already uses.
 * Only explicit, useful, non-sensitive caller facts are requested; YOU lines are context only.
 */
class CallerMemoryAiExtractor(context: Context) {
    data class Learned(
        val likes: List<String> = emptyList(),
        val dislikes: List<String> = emptyList(),
        val facts: List<String> = emptyList(),
        val topics: List<String> = emptyList(),
        val starters: List<String> = emptyList(),
        val unresolved: List<String> = emptyList(),
        val preferredStyle: String = "",
        val summary: String = "",
        val provider: String = "",
    )

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)
    private val performance = CoachProviderPerformanceStore(appContext)

    fun configured(): Boolean = settings.coachConfigured()

    fun extractAsync(phoneNumber: String, transcript: String, callback: (Learned?) -> Unit = {}) {
        if (phoneNumber.isBlank() || transcript.isBlank() || !configured()) {
            callback(null)
            return
        }
        EXECUTOR.execute {
            callback(runCatching { extract(transcript) }.getOrNull())
        }
    }

    internal fun extract(transcript: String): Learned {
        val prompt = transcriptPrompt(transcript)
        val providers = providerOrder()
        if (providers.isEmpty()) throw IllegalStateException("NO AI PROVIDER CONFIGURED FOR CALLER MEMORY")
        val failures = mutableListOf<String>()
        for (provider in providers) {
            try {
                val raw = when (provider) {
                    SettingsStore.COACH_PROVIDER_GROQ -> requestGroq(prompt)
                    SettingsStore.COACH_PROVIDER_GEMINI -> requestGemini(prompt)
                    SettingsStore.COACH_PROVIDER_CEREBRAS -> requestCompatible(OpenAiCompatibleCoachClient.cerebras(settings), prompt)
                    SettingsStore.COACH_PROVIDER_MISTRAL -> requestCompatible(OpenAiCompatibleCoachClient.mistral(settings), prompt)
                    SettingsStore.COACH_PROVIDER_OPENROUTER -> requestCompatible(OpenAiCompatibleCoachClient.openRouter(settings), prompt)
                    else -> continue
                }
                return parse(raw).copy(provider = provider)
            } catch (t: Throwable) {
                failures += "$provider: ${t.message.orEmpty().take(80)}"
            }
        }
        throw IllegalStateException("CALLER MEMORY EXTRACTION FAILED // ${failures.joinToString(" · ").take(220)}")
    }

    private fun providerOrder(): List<String> {
        val configured = SettingsStore.COACH_FALLBACK_ORDER.filter(settings::providerConfigured)
        if (settings.coachProvider != SettingsStore.COACH_PROVIDER_AUTO) {
            return configured.filter { it == settings.coachProvider }
        }
        return configured.sortedByDescending { CoachProviderPerformanceStore.score(performance.stats(it)) }
    }

    private fun requestGroq(prompt: String): String {
        val connection = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7_000
            connection.readTimeout = 14_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${settings.groqApiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().apply {
                put("model", settings.groqModel)
                put("temperature", 0.1)
                put("max_completion_tokens", 700)
                put("response_format", JSONObject().put("type", "json_object"))
                if (settings.groqModel.startsWith("openai/gpt-oss-")) put("reasoning_effort", "low")
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    put(JSONObject().put("role", "user").put("content", prompt))
                })
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw IllegalStateException("GROQ MEMORY HTTP ${connection.responseCode} // ${readError(connection)}")
            val root = JSONObject(BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() })
            return root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
        } finally {
            connection.disconnect()
        }
    }

    private fun requestGemini(prompt: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${settings.geminiModel}:generateContent"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7_000
            connection.readTimeout = 14_000
            connection.doOutput = true
            connection.setRequestProperty("x-goog-api-key", settings.geminiApiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
                put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 800)
                    put("responseMimeType", "application/json")
                    put("responseSchema", geminiSchema())
                })
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw IllegalStateException("GEMINI MEMORY HTTP ${connection.responseCode} // ${readError(connection)}")
            val root = JSONObject(BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() })
            val parts = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("GEMINI MEMORY RESPONSE EMPTY")
            return buildString {
                for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun requestCompatible(provider: OpenAiCompatibleCoachClient.Provider, prompt: String): String {
        return try {
            requestCompatibleOnce(provider, prompt, true)
        } catch (first: ProviderMemoryException) {
            if (first.code != 400) throw first
            requestCompatibleOnce(provider, prompt, false)
        }
    }

    private fun requestCompatibleOnce(provider: OpenAiCompatibleCoachClient.Provider, prompt: String, structured: Boolean): String {
        val connection = URL(provider.endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7_000
            connection.readTimeout = 14_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            provider.extraHeaders.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            val body = JSONObject().apply {
                put("model", provider.model)
                put("temperature", 0.1)
                put("max_tokens", 800)
                if (structured) put("response_format", JSONObject().put("type", "json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    put(JSONObject().put("role", "user").put("content", prompt))
                })
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) throw ProviderMemoryException(code, "${provider.label} MEMORY HTTP $code // ${readError(connection)}")
            val root = JSONObject(BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() })
            return root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
        } finally {
            connection.disconnect()
        }
    }

    private class ProviderMemoryException(val code: Int, message: String) : IllegalStateException(message)

    private fun transcriptPrompt(transcript: String): String {
        val normalized = transcript.trim()
        val clipped = if (normalized.length <= MAX_TRANSCRIPT_CHARS) normalized else {
            normalized.take(6_000) + "\n[...middle omitted for token budget...]\n" + normalized.takeLast(MAX_TRANSCRIPT_CHARS - 6_000)
        }
        return "Completed call transcript:\n\n$clipped"
    }

    companion object {
        private const val MAX_TRANSCRIPT_CHARS = 14_000
        private val EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "reality-caller-memory-ai").apply { isDaemon = true }
        }

        private val SYSTEM_PROMPT = """
You extract durable caller memory for a private phone-call assistant.
Analyze CALLER lines only. YOU lines are context and must never be attributed to the caller.
Save only explicit, useful facts that the caller actually said or clearly confirmed. Never guess.
Do not infer or store sensitive traits or secrets such as health/diagnoses, race/ethnicity, religion, political beliefs, sexual details, criminal history, passwords, account numbers, precise addresses, or financial identifiers.
Do not label deception, mental state, or personality. Keep wording neutral and compact.
Ordinary explicit relationship, work, school, hobby, preference, plan, and everyday-life facts are okay.
Conversation starters must be friendly, non-coercive follow-ups grounded in the transcript.
Unresolved items are topics the caller explicitly left open or planned to revisit.
Return JSON only with exactly these keys:
{"likes":[],"dislikes":[],"facts":[],"topics":[],"starters":[],"unresolved":[],"style":"","summary":""}
Use short strings. Maximum 6 items per list. style <= 80 characters. summary <= 360 characters and 1-2 neutral sentences.
""".trimIndent()

        internal fun parse(raw: String): Learned {
            val root = JSONObject(cleanJson(raw))
            return Learned(
                likes = strings(root.optJSONArray("likes"), 6),
                dislikes = strings(root.optJSONArray("dislikes"), 6),
                facts = strings(root.optJSONArray("facts"), 6),
                topics = strings(root.optJSONArray("topics"), 6),
                starters = strings(root.optJSONArray("starters"), 6),
                unresolved = strings(root.optJSONArray("unresolved"), 6),
                preferredStyle = clean(root.optString("style"), 80),
                summary = clean(root.optString("summary"), 360),
            )
        }

        private fun strings(array: JSONArray?, max: Int): List<String> = buildList {
            if (array == null) return@buildList
            for (i in 0 until array.length()) {
                val value = clean(array.optString(i), 160)
                if (value.isNotBlank() && none { it.equals(value, true) }) add(value)
                if (size >= max) break
            }
        }

        private fun clean(value: String, max: Int): String = value.trim().replace(Regex("\\s+"), " ").take(max)

        private fun cleanJson(raw: String): String {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("```")) return trimmed
            return trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }

        private fun geminiSchema(): JSONObject {
            fun stringArray() = JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING"))
            return JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("likes", stringArray())
                    put("dislikes", stringArray())
                    put("facts", stringArray())
                    put("topics", stringArray())
                    put("starters", stringArray())
                    put("unresolved", stringArray())
                    put("style", JSONObject().put("type", "STRING"))
                    put("summary", JSONObject().put("type", "STRING"))
                })
                put("required", JSONArray(listOf("likes", "dislikes", "facts", "topics", "starters", "unresolved", "style", "summary")))
            }
        }

        private fun readError(connection: HttpURLConnection): String = runCatching {
            val raw = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (raw.isBlank()) return@runCatching ""
            val root = JSONObject(raw)
            root.optJSONObject("error")?.optString("message").orEmpty().ifBlank { raw }
        }.getOrDefault("").replace(Regex("\\s+"), " ").take(180)
    }
}

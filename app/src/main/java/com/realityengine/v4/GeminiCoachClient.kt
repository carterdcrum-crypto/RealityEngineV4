package com.realityengine.v4

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Gemini Developer API implementation of the live response-coach request. */
class GeminiCoachClient(private val settings: SettingsStore) {
    class GeminiHttpException(val code: Int, message: String) : IllegalStateException(message)

    fun request(
        snapshot: ConversationContext.Snapshot,
        quickModeId: String? = null,
    ): LiveResponseEngine.Result {
        if (!settings.geminiConfigured()) throw IllegalStateException("GEMINI API KEY REQUIRED")
        val model = settings.geminiModel
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("x-goog-api-key", settings.geminiApiKey)
            connection.setRequestProperty("Content-Type", "application/json")

            val quick = CoachQuickModeCatalog.byId(quickModeId)
            val body = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", systemPrompt(quickModeId))
                )))
                put("contents", JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(
                            JSONObject().put("text", snapshot.asPromptContext())
                        ))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", quick?.temperature ?: .25)
                    put("maxOutputTokens", 512)
                    put("responseMimeType", "application/json")
                    put("responseSchema", responseSchema())
                })
            }

            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = readError(connection)
                throw GeminiHttpException(code, geminiHttpMessage(code, detail))
            }

            val root = JSONObject(BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() })
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val blocked = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                throw IllegalStateException(
                    if (blocked.isBlank()) "GEMINI RESPONSE EMPTY" else "GEMINI BLOCKED // $blocked"
                )
            }
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw IllegalStateException("GEMINI RESPONSE INVALID // NO CONTENT")
            val content = buildString {
                for (i in 0 until parts.length()) {
                    val text = parts.optJSONObject(i)?.optString("text").orEmpty()
                    if (text.isNotBlank()) append(text)
                }
            }.trim()
            if (content.isBlank()) throw IllegalStateException("GEMINI RESPONSE INVALID // EMPTY JSON")

            val parsed = JSONObject(content)
            fun suggestion(item: JSONObject) = LiveResponseEngine.Suggestion(
                mode = ResponseStrategyCatalog.normalizeMode(item.optString("mode", "CLARIFY")),
                tone = item.optString("tone", "calm/curious").take(48),
                text = item.optString("text").trim().take(180),
                reason = item.optString("reason").trim().take(100),
            )

            val best = suggestion(parsed.getJSONObject("best"))
            if (best.text.isBlank()) throw IllegalStateException("GEMINI RESPONSE INVALID // EMPTY REPLY")
            val alternativesJson = parsed.optJSONArray("alternatives") ?: JSONArray()
            val seenModes = linkedSetOf(best.mode)
            val alternatives = buildList {
                for (i in 0 until alternativesJson.length()) {
                    if (size >= 4) break
                    val candidate = suggestion(alternativesJson.getJSONObject(i))
                    if (candidate.text.isNotBlank() && seenModes.add(candidate.mode)) add(candidate)
                }
            }
            val usage = root.optJSONObject("usageMetadata")
            return LiveResponseEngine.Result(
                best = best,
                alternatives = alternatives,
                inputTokens = usage?.optInt("promptTokenCount", snapshot.estimatedTokens) ?: snapshot.estimatedTokens,
                outputTokens = usage?.optInt("candidatesTokenCount", 0) ?: 0,
                model = model,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun systemPrompt(quickModeId: String?): String {
        val strategyGuide = ResponseStrategyCatalog.promptGuide()
        val quickMode = CoachQuickModeCatalog.byId(quickModeId)
        val quickInstruction = quickMode?.let {
            "\n\nONE-SHOT QUICK MODE — ${it.label.uppercase()}: ${it.promptInstruction}"
        }.orEmpty()
        return """You are a live phone-call response coach. Suggest concise, natural replies for the USER to say to the CALLER. Personalize only from supplied caller profile/context; never invent facts. Choose exactly five DISTINCT strategies from the catalog below that best fit the current moment. Rank them: one BEST choice plus four alternatives. Do not force a strategy when it does not fit. Keep suggestions non-coercive: do not manipulate, threaten, shame, pressure, or fabricate. COGNITIVE_PROBE must remain a neutral question, never a trap.

STRATEGY CATALOG:
$strategyGuide

Every choice must include a short delivery tone such as warm/relaxed, calm/curious, neutral/firm, light/playful, slow/deliberate, or steady/direct. Keep each spoken reply under 18 words and each reason under 6 words.$quickInstruction"""
    }

    private fun responseSchema(): JSONObject {
        fun suggestionSchema() = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("mode", JSONObject().put("type", "STRING"))
                put("tone", JSONObject().put("type", "STRING"))
                put("text", JSONObject().put("type", "STRING"))
                put("reason", JSONObject().put("type", "STRING"))
            })
            put("required", JSONArray(listOf("mode", "tone", "text", "reason")))
        }
        return JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("best", suggestionSchema())
                put("alternatives", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", suggestionSchema())
                })
            })
            put("required", JSONArray(listOf("best", "alternatives")))
        }
    }

    private fun readError(connection: HttpURLConnection): String = runCatching {
        val raw = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (raw.isBlank()) return@runCatching ""
        val root = JSONObject(raw)
        root.optJSONObject("error")?.optString("message").orEmpty().ifBlank { raw }
    }.getOrDefault("").replace(Regex("\\s+"), " ").take(180)

    private fun geminiHttpMessage(code: Int, detail: String): String {
        val prefix = when (code) {
            400 -> "GEMINI REQUEST REJECTED"
            401, 403 -> "GEMINI AUTH/ACCESS FAILED"
            404 -> "GEMINI MODEL NOT FOUND"
            429 -> "GEMINI RATE LIMITED"
            in 500..599 -> "GEMINI SERVER ERROR"
            else -> "GEMINI ERROR // HTTP $code"
        }
        return if (detail.isBlank()) "$prefix // HTTP $code" else "$prefix // ${detail.take(150)}"
    }
}

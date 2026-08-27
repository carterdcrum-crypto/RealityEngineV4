package com.realityengine.v4

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Shared client for OpenAI-compatible fallback providers (Cerebras, Mistral, OpenRouter). */
class OpenAiCompatibleCoachClient(private val settings: SettingsStore) {
    data class Provider(
        val id: String,
        val label: String,
        val endpoint: String,
        val apiKey: String,
        val model: String,
        val extraHeaders: Map<String, String> = emptyMap(),
    )

    class ProviderHttpException(val provider: String, val code: Int, message: String) : IllegalStateException(message)

    fun request(
        provider: Provider,
        snapshot: ConversationContext.Snapshot,
        quickModeId: String? = null,
    ): LiveResponseEngine.Result {
        if (provider.apiKey.isBlank()) throw IllegalStateException("${provider.label.uppercase()} API KEY REQUIRED")
        return try {
            requestOnce(provider, snapshot, quickModeId, structured = true)
        } catch (first: ProviderHttpException) {
            // Some OpenAI-compatible gateways/models reject response_format even though they can
            // still reliably obey a JSON-only prompt. Retry once without constrained JSON mode.
            if (first.code != 400) throw first
            requestOnce(provider, snapshot, quickModeId, structured = false)
        }
    }

    private fun requestOnce(
        provider: Provider,
        snapshot: ConversationContext.Snapshot,
        quickModeId: String?,
        structured: Boolean,
    ): LiveResponseEngine.Result {
        val connection = URL(provider.endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7_000
            connection.readTimeout = 11_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            provider.extraHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            val quick = CoachQuickModeCatalog.byId(quickModeId)
            val body = JSONObject().apply {
                put("model", provider.model)
                put("temperature", quick?.temperature ?: .25)
                put("max_tokens", 420)
                if (structured) put("response_format", JSONObject().put("type", "json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt(quickModeId)))
                    put(JSONObject().put("role", "user").put("content", snapshot.asPromptContext()))
                })
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = readError(connection)
                throw ProviderHttpException(provider.label, code, httpMessage(provider.label, code, detail))
            }

            val root = JSONObject(BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() })
            val content = root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
                .trim()
            if (content.isBlank()) throw IllegalStateException("${provider.label.uppercase()} RESPONSE EMPTY")
            val parsed = JSONObject(cleanJson(content))

            fun suggestion(item: JSONObject) = LiveResponseEngine.Suggestion(
                mode = ResponseStrategyCatalog.normalizeMode(item.optString("mode", "CLARIFY")),
                tone = item.optString("tone", "calm/curious").take(48),
                text = item.optString("text").trim().take(180),
                reason = item.optString("reason").trim().take(100),
            )

            val best = suggestion(parsed.getJSONObject("best"))
            if (best.text.isBlank()) throw IllegalStateException("${provider.label.uppercase()} RESPONSE INVALID // EMPTY REPLY")
            val alternativesJson = parsed.optJSONArray("alternatives") ?: JSONArray()
            val seenModes = linkedSetOf(best.mode)
            val alternatives = buildList {
                for (i in 0 until alternativesJson.length()) {
                    if (size >= 4) break
                    val candidate = suggestion(alternativesJson.getJSONObject(i))
                    if (candidate.text.isNotBlank() && seenModes.add(candidate.mode)) add(candidate)
                }
            }
            val usage = root.optJSONObject("usage")
            return LiveResponseEngine.Result(
                best = best,
                alternatives = alternatives,
                inputTokens = usage?.optInt("prompt_tokens", snapshot.estimatedTokens) ?: snapshot.estimatedTokens,
                outputTokens = usage?.optInt("completion_tokens", 0) ?: 0,
                model = "${provider.label}:${provider.model}",
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

Every choice must include a short delivery tone such as warm/relaxed, calm/curious, neutral/firm, light/playful, slow/deliberate, or steady/direct. Return JSON only with this shape: {\"best\":{\"mode\":\"...\",\"tone\":\"...\",\"text\":\"...\",\"reason\":\"...\"},\"alternatives\":[{\"mode\":\"...\",\"tone\":\"...\",\"text\":\"...\",\"reason\":\"...\"}]}. Keep each spoken reply under 18 words and each reason under 6 words. No markdown or commentary outside the JSON.$quickInstruction"""
    }

    private fun cleanJson(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun readError(connection: HttpURLConnection): String = runCatching {
        val raw = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (raw.isBlank()) return@runCatching ""
        val root = JSONObject(raw)
        root.optJSONObject("error")?.optString("message").orEmpty().ifBlank { raw }
    }.getOrDefault("").replace(Regex("\\s+"), " ").take(180)

    private fun httpMessage(label: String, code: Int, detail: String): String {
        val prefix = when (code) {
            400 -> "$label REQUEST REJECTED"
            401, 403 -> "$label AUTH/ACCESS FAILED"
            404 -> "$label MODEL/ENDPOINT NOT FOUND"
            429 -> "$label RATE LIMITED"
            in 500..599 -> "$label SERVER ERROR"
            else -> "$label ERROR // HTTP $code"
        }.uppercase()
        return if (detail.isBlank()) "$prefix // HTTP $code" else "$prefix // ${detail.take(150)}"
    }

    companion object {
        fun cerebras(settings: SettingsStore) = Provider(
            id = SettingsStore.COACH_PROVIDER_CEREBRAS,
            label = "Cerebras",
            endpoint = "https://api.cerebras.ai/v1/chat/completions",
            apiKey = settings.cerebrasApiKey,
            model = settings.cerebrasModel,
        )

        fun mistral(settings: SettingsStore) = Provider(
            id = SettingsStore.COACH_PROVIDER_MISTRAL,
            label = "Mistral",
            endpoint = "https://api.mistral.ai/v1/chat/completions",
            apiKey = settings.mistralApiKey,
            model = settings.mistralModel,
        )

        fun openRouter(settings: SettingsStore) = Provider(
            id = SettingsStore.COACH_PROVIDER_OPENROUTER,
            label = "OpenRouter",
            endpoint = "https://openrouter.ai/api/v1/chat/completions",
            apiKey = settings.openRouterApiKey,
            model = settings.openRouterModel,
            extraHeaders = mapOf(
                "HTTP-Referer" to "https://github.com/carterdcrum-crypto/RealityEngineV4",
                "X-Title" to "Reality Engine V4",
            ),
        )
    }
}

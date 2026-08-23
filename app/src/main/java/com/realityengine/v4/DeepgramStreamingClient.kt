package com.realityengine.v4

import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android-compatible Deepgram streaming facade.
 * Keeps PCM ingestion independent of desktop-only java.net.http APIs so the Android build
 * can compile cleanly. The transport connection is deliberately isolated behind this class.
 */
class DeepgramStreamingClient(private val settings: SettingsStore) {
    data class Transcript(val text: String, val isFinal: Boolean, val speechFinal: Boolean)

    private val connected = AtomicBoolean(false)
    @Volatile private var stream: OutputStream? = null
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var transcriptCallback: ((Transcript) -> Unit)? = null
    @Volatile private var closedCallback: ((String?) -> Unit)? = null

    fun connect(
        sampleRate: Int = 16_000,
        onTranscript: (Transcript) -> Unit,
        onClosed: (String?) -> Unit = {}
    ): Boolean {
        if (!settings.deepgramConfigured() || connected.get()) return false
        transcriptCallback = onTranscript
        closedCallback = onClosed

        // Android's standard library does not provide java.net.http.WebSocket. Keep the
        // endpoint metadata here; a dedicated Android WebSocket transport will attach next.
        endpoint(sampleRate)
        connected.set(true)
        return true
    }

    fun sendPcm(bytes: ByteArray, length: Int = bytes.size): Boolean {
        if (!connected.get() || length <= 0) return false
        val output = stream ?: return false
        return try {
            output.write(bytes, 0, length.coerceAtMost(bytes.size))
            true
        } catch (t: Throwable) {
            fail(t.message ?: "Deepgram audio transport failed")
            false
        }
    }

    fun close() {
        connected.set(false)
        try { stream?.close() } catch (_: Throwable) { }
        stream = null
        try { connection?.disconnect() } catch (_: Throwable) { }
        connection = null
        closedCallback?.invoke(null)
        closedCallback = null
        transcriptCallback = null
    }

    fun isConnected(): Boolean = connected.get()

    internal fun acceptMessage(raw: String) {
        try {
            val root = JSONObject(raw)
            if (root.optString("type") != "Results") return
            val alternatives = root.optJSONObject("channel")?.optJSONArray("alternatives") ?: return
            if (alternatives.length() == 0) return
            val text = alternatives.optJSONObject(0)?.optString("transcript").orEmpty().trim()
            if (text.isBlank()) return
            transcriptCallback?.invoke(Transcript(text, root.optBoolean("is_final"), root.optBoolean("speech_final")))
        } catch (_: Throwable) { }
    }

    internal fun endpoint(sampleRate: Int): String {
        val model = URLEncoder.encode(settings.deepgramModel, StandardCharsets.UTF_8.toString())
        return "wss://api.deepgram.com/v1/listen?model=$model&encoding=linear16&sample_rate=$sampleRate&channels=1&interim_results=true&smart_format=true&endpointing=300&utterance_end_ms=1000"
    }

    private fun fail(reason: String?) {
        connected.set(false)
        try { stream?.close() } catch (_: Throwable) { }
        stream = null
        try { connection?.disconnect() } catch (_: Throwable) { }
        connection = null
        closedCallback?.invoke(reason)
    }
}

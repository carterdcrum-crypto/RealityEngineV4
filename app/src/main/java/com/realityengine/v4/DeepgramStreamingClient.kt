package com.realityengine.v4

import android.net.Uri
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean

/** Android-compatible Deepgram streaming facade. */
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

    internal fun endpoint(sampleRate: Int): String = Uri.Builder()
        .scheme("wss")
        .authority("api.deepgram.com")
        .appendPath("v1")
        .appendPath("listen")
        .appendQueryParameter("model", settings.deepgramModel)
        .appendQueryParameter("encoding", "linear16")
        .appendQueryParameter("sample_rate", sampleRate.toString())
        .appendQueryParameter("channels", "1")
        .appendQueryParameter("interim_results", "true")
        .appendQueryParameter("smart_format", "true")
        .appendQueryParameter("endpointing", "300")
        .appendQueryParameter("utterance_end_ms", "1000")
        .build()
        .toString()

    private fun fail(reason: String?) {
        connected.set(false)
        try { stream?.close() } catch (_: Throwable) { }
        stream = null
        try { connection?.disconnect() } catch (_: Throwable) { }
        connection = null
        closedCallback?.invoke(reason)
    }
}

package com.realityengine.v4

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Android WebSocket transport for Deepgram live transcription. */
class DeepgramStreamingClient(private val settings: SettingsStore) {
    data class Transcript(val text: String, val isFinal: Boolean, val speechFinal: Boolean)

    private val connected = AtomicBoolean(false)
    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var transcriptCallback: ((Transcript) -> Unit)? = null
    @Volatile private var closedCallback: ((String?) -> Unit)? = null

    fun connect(sampleRate: Int = 16_000, onTranscript: (Transcript) -> Unit, onClosed: (String?) -> Unit = {}): Boolean {
        if (!settings.deepgramConfigured() || connected.get()) return false
        transcriptCallback = onTranscript
        closedCallback = onClosed
        val request = Request.Builder().url(endpoint(sampleRate)).header("Authorization", "Token ${settings.deepgramApiKey}").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { connected.set(true) }
            override fun onMessage(webSocket: WebSocket, text: String) { acceptMessage(text) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { finish(reason.takeIf { it.isNotBlank() }) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { finish(t.message ?: "Deepgram WebSocket failed") }
        })
        return true
    }

    fun sendPcm(bytes: ByteArray, length: Int = bytes.size): Boolean {
        if (!connected.get() || length <= 0) return false
        val safeLength = length.coerceAtMost(bytes.size)
        return socket?.send(bytes.toByteString(0, safeLength)) == true
    }

    fun close() {
        if (connected.get()) socket?.send("{\"type\":\"CloseStream\"}")
        socket?.close(1000, "call ended")
        finish(null)
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
        .scheme("wss").authority("api.deepgram.com").appendPath("v1").appendPath("listen")
        .appendQueryParameter("model", settings.deepgramModel).appendQueryParameter("encoding", "linear16")
        .appendQueryParameter("sample_rate", sampleRate.toString()).appendQueryParameter("channels", "1")
        .appendQueryParameter("interim_results", "true").appendQueryParameter("smart_format", "true")
        .appendQueryParameter("endpointing", "300").appendQueryParameter("utterance_end_ms", "1000").build().toString()

    @Synchronized private fun finish(reason: String?) {
        val wasActive = connected.getAndSet(false)
        socket = null
        if (wasActive || reason != null) closedCallback?.invoke(reason)
        closedCallback = null
        transcriptCallback = null
    }
}

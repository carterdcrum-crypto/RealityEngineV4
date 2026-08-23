package com.realityengine.v4

import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

/** Minimal binary PCM -> Deepgram live transcription transport. */
class DeepgramStreamingClient(private val settings: SettingsStore) {
    data class Transcript(val text: String, val isFinal: Boolean, val speechFinal: Boolean)

    private val connected = AtomicBoolean(false)
    @Volatile private var socket: WebSocket? = null

    fun connect(
        sampleRate: Int = 16_000,
        onTranscript: (Transcript) -> Unit,
        onClosed: (String?) -> Unit = {}
    ): Boolean {
        if (!settings.deepgramConfigured() || connected.get()) return false
        val model = URLEncoder.encode(settings.deepgramModel, StandardCharsets.UTF_8.toString())
        val uri = URI.create(
            "wss://api.deepgram.com/v1/listen?model=$model&encoding=linear16&sample_rate=$sampleRate&channels=1&interim_results=true&smart_format=true&endpointing=300&utterance_end_ms=1000"
        )
        val client = HttpClient.newHttpClient()
        client.newWebSocketBuilder()
            .header("Authorization", "Token ${settings.deepgramApiKey}")
            .buildAsync(uri, object : WebSocket.Listener {
                private val textBuffer = StringBuilder()

                override fun onOpen(webSocket: WebSocket) {
                    socket = webSocket
                    connected.set(true)
                    webSocket.request(1)
                }

                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    textBuffer.append(data)
                    if (last) {
                        parse(textBuffer.toString(), onTranscript)
                        textBuffer.setLength(0)
                    }
                    webSocket.request(1)
                    return null
                }

                override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*>? {
                    webSocket.request(1)
                    return null
                }

                override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
                    connected.set(false); socket = null; onClosed(reason.takeIf { it.isNotBlank() }); return null
                }

                override fun onError(webSocket: WebSocket, error: Throwable) {
                    connected.set(false); socket = null; onClosed(error.message)
                }
            }).exceptionally { onClosed(it.message); null }
        return true
    }

    fun sendPcm(bytes: ByteArray, length: Int = bytes.size): Boolean {
        val ws = socket ?: return false
        if (!connected.get() || length <= 0) return false
        val safeLength = length.coerceAtMost(bytes.size)
        ws.sendBinary(ByteBuffer.wrap(bytes, 0, safeLength), true)
        return true
    }

    fun close() {
        val ws = socket
        connected.set(false); socket = null
        try { ws?.sendText("{\"type\":\"CloseStream\"}", true) } catch (_: Throwable) { }
    }

    fun isConnected(): Boolean = connected.get()

    private fun parse(raw: String, callback: (Transcript) -> Unit) {
        try {
            val root = JSONObject(raw)
            if (root.optString("type") != "Results") return
            val alternatives = root.optJSONObject("channel")?.optJSONArray("alternatives") ?: return
            if (alternatives.length() == 0) return
            val text = alternatives.optJSONObject(0)?.optString("transcript").orEmpty().trim()
            if (text.isBlank()) return
            callback(Transcript(text, root.optBoolean("is_final"), root.optBoolean("speech_final")))
        } catch (_: Throwable) { }
    }
}

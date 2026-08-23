package com.realityengine.v4

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/** Live transcription pipeline supporting native Shizuku PCM and Twilio media fallback PCM. */
class LiveTranscriptionPipeline(context: Context) {
    sealed class StartResult { data object Started : StartResult(); data class Unavailable(val reason: String) : StartResult() }

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)
    private val capture = VoiceCallPcmCapture(appContext)
    private val deepgram = DeepgramStreamingClient(settings)
    private val conversation = LiveConversationSession(appContext)
    private val running = AtomicBoolean(false)
    @Volatile private var interimCallback: ((String) -> Unit)? = null
    @Volatile private var stoppedCallback: ((String?) -> Unit)? = null

    fun start(onInterim: (String) -> Unit = {}, onStopped: (String?) -> Unit = {}): StartResult {
        if (!begin(VoiceCallPcmCapture.SAMPLE_RATE, onInterim, onStopped)) return StartResult.Unavailable("Deepgram stream could not start")
        return when (val result = capture.start(
            onPcm = { bytes, length -> if (running.get() && deepgram.isConnected()) deepgram.sendPcm(bytes, length) },
            onStopped = { reason -> if (running.getAndSet(false)) { deepgram.close(); stoppedCallback?.invoke(reason) } }
        )) {
            is VoiceCallPcmCapture.StartResult.Started -> StartResult.Started
            is VoiceCallPcmCapture.StartResult.Unavailable -> { running.set(false); deepgram.close(); StartResult.Unavailable(result.reason) }
        }
    }

    /** Starts the Deepgram side for a call whose audio arrives from Twilio Media Streams. */
    fun startTwilio(onInterim: (String) -> Unit = {}, onStopped: (String?) -> Unit = {}): StartResult =
        if (begin(8_000, onInterim, onStopped)) StartResult.Started else StartResult.Unavailable("Deepgram stream could not start")

    /** Feed one raw Twilio WebSocket JSON message. Non-media events are ignored. */
    fun acceptTwilioMessage(message: String): Boolean {
        if (!running.get()) return false
        val frame = TwilioMediaStreamDecoder.decode(message) ?: return false
        return deepgram.sendPcm(frame.pcm16)
    }

    private fun begin(sampleRate: Int, onInterim: (String) -> Unit, onStopped: (String?) -> Unit): Boolean {
        if (!settings.deepgramConfigured() || !running.compareAndSet(false, true)) return false
        interimCallback = onInterim; stoppedCallback = onStopped; conversation.bindActiveCaller()
        val connecting = deepgram.connect(sampleRate = sampleRate, onTranscript = { result ->
            interimCallback?.invoke(result.text)
            if (result.isFinal && result.text.isNotBlank()) conversation.onCallerTurn(result.text)
        }, onClosed = { reason -> if (running.getAndSet(false)) { capture.stop(); stoppedCallback?.invoke(reason) } })
        if (!connecting) running.set(false)
        return connecting
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        capture.stop(); deepgram.close(); conversation.clear(); interimCallback=null; stoppedCallback=null
    }

    fun isRunning(): Boolean = running.get()
}

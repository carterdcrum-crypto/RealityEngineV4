package com.realityengine.v4

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Connects the native Shizuku-gated VOICE_CALL PCM source to Deepgram.
 * Final caller utterances are forwarded into the live conversation/response-coach session.
 */
class LiveTranscriptionPipeline(context: Context) {
    sealed class StartResult {
        data object Started : StartResult()
        data class Unavailable(val reason: String) : StartResult()
    }

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)
    private val capture = VoiceCallPcmCapture(appContext)
    private val deepgram = DeepgramStreamingClient(settings)
    private val conversation = LiveConversationSession(appContext)
    private val running = AtomicBoolean(false)
    @Volatile private var interimCallback: ((String) -> Unit)? = null
    @Volatile private var stoppedCallback: ((String?) -> Unit)? = null

    fun start(onInterim: (String) -> Unit = {}, onStopped: (String?) -> Unit = {}): StartResult {
        if (!settings.deepgramConfigured()) return StartResult.Unavailable("Deepgram is not configured")
        if (!running.compareAndSet(false, true)) return StartResult.Started
        interimCallback = onInterim
        stoppedCallback = onStopped
        conversation.bindActiveCaller()

        val connecting = deepgram.connect(
            sampleRate = VoiceCallPcmCapture.SAMPLE_RATE,
            onTranscript = { result ->
                interimCallback?.invoke(result.text)
                if (result.isFinal && result.text.isNotBlank()) conversation.onCallerTurn(result.text)
            },
            onClosed = { reason ->
                if (running.getAndSet(false)) {
                    capture.stop()
                    stoppedCallback?.invoke(reason)
                }
            }
        )
        if (!connecting) {
            running.set(false)
            return StartResult.Unavailable("Deepgram stream could not start")
        }

        return when (val result = capture.start(
            onPcm = { bytes, length ->
                if (running.get() && deepgram.isConnected()) deepgram.sendPcm(bytes, length)
            },
            onStopped = { reason ->
                if (running.getAndSet(false)) {
                    deepgram.close()
                    stoppedCallback?.invoke(reason)
                }
            }
        )) {
            is VoiceCallPcmCapture.StartResult.Started -> StartResult.Started
            is VoiceCallPcmCapture.StartResult.Unavailable -> {
                running.set(false)
                deepgram.close()
                StartResult.Unavailable(result.reason)
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        capture.stop()
        deepgram.close()
        conversation.clear()
        interimCallback = null
        stoppedCallback = null
    }

    fun isRunning(): Boolean = running.get()
}

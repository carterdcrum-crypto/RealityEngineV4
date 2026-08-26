package com.realityengine.v4

import android.content.Context
import android.os.ParcelFileDescriptor
import java.util.concurrent.atomic.AtomicBoolean

/** 16 kHz mono PCM capture backed by scrcpy-server running in the Shizuku shell process. */
class VoiceCallPcmCapture(context: Context) {
    data class Format(val sampleRate: Int = SAMPLE_RATE, val channels: Int = 1, val bitsPerSample: Int = 16)
    enum class Direction { MIXED, DOWNLINK, UPLINK }
    enum class Mode { SINGLE, DUAL }
    sealed class StartResult {
        data class Started(val format: Format, val source: String, val mode: Mode) : StartResult()
        data class Unavailable(val reason: String) : StartResult()
    }

    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var reader: ScrcpyRawPcmReader? = null
    @Volatile var activeSource: String? = null
        private set

    fun start(onPcm: (ByteArray, Int) -> Unit, onStopped: (String?) -> Unit = {}): StartResult =
        start(onFrame = { bytes, length, _ -> onPcm(bytes, length) }, onStarted = {}, onStopped = onStopped)

    fun start(
        onFrame: (ByteArray, Int, Direction) -> Unit,
        onStarted: (Mode) -> Unit = {},
        onStopped: (String?) -> Unit = {}
    ): StartResult {
        if (!running.compareAndSet(false, true)) return StartResult.Unavailable("Capture already running")
        val shizukuState = ShizukuAudioStatus.state()
        if (shizukuState != ShizukuAudioStatus.State.READY) {
            running.set(false)
            return StartResult.Unavailable(ShizukuAudioStatus.diagnostic())
        }

        activeSource = SOURCE_STARTING
        worker = Thread({
            var failure: String? = null
            var pipe: ParcelFileDescriptor? = null
            try {
                val server = ScrcpyServerAsset.ensureExtracted(appContext)
                    ?: throw IllegalStateException("Verified scrcpy-server could not be extracted")
                if (!running.get()) return@Thread

                if (!ShizukuAudioClient.connect()) {
                    throw IllegalStateException("Privileged Shizuku audio service did not bind")
                }
                if (!running.get()) return@Thread

                pipe = ShizukuAudioClient.startScrcpy(server.absolutePath)
                    ?: throw IllegalStateException("scrcpy voice-call capture did not return an audio pipe")
                if (!running.get()) return@Thread

                activeSource = SOURCE_ACTIVE
                onStarted(Mode.SINGLE)

                val rawReader = ScrcpyRawPcmReader(ParcelFileDescriptor.AutoCloseInputStream(pipe))
                reader = rawReader
                pipe = null // ownership moved into AutoCloseInputStream/reader
                rawReader.run(
                    onPcm = { pcm, length ->
                        if (running.get() && length > 0) onFrame(pcm, length, Direction.MIXED)
                    },
                    onStopped = { reason -> if (reason != null) failure = reason }
                )
            } catch (t: Throwable) {
                if (running.get()) failure = t.message ?: "scrcpy call audio failed: ${t.javaClass.simpleName}"
            } finally {
                runCatching { pipe?.close() }
                reader = null
                activeSource = null
                running.set(false)
                ShizukuAudioClient.disconnect()
                worker = null
                onStopped(failure)
            }
        }, "reality-scrcpy-call-pcm").apply {
            isDaemon = true
            start()
        }

        return StartResult.Started(Format(), SOURCE_ACTIVE, Mode.SINGLE)
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        activeSource = null
        reader?.stop()
        worker?.interrupt()
    }

    fun isRunning(): Boolean = running.get()

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val SOURCE_STARTING = "SHIZUKU_SCRCPY_STARTING"
        private const val SOURCE_ACTIVE = "SHIZUKU_SCRCPY_VOICE_CALL_RAW"
    }
}

package com.realityengine.v4

import android.content.Context
import android.os.ParcelFileDescriptor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    @Volatile private var downlinkReader: ScrcpyRawPcmReader? = null
    @Volatile private var uplinkReader: ScrcpyRawPcmReader? = null
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
            var mixedPipe: ParcelFileDescriptor? = null
            try {
                val server = ScrcpyServerAsset.ensureExtracted(appContext)
                    ?: throw IllegalStateException("Verified scrcpy-server could not be extracted")
                if (!running.get()) return@Thread

                if (!ShizukuAudioClient.connect()) {
                    throw IllegalStateException("Privileged Shizuku audio service did not bind")
                }
                if (!running.get()) return@Thread

                // Prefer true directional capture: DOWNLINK = remote caller, UPLINK = this phone's mic.
                // This lets Deepgram multichannel label speakers deterministically instead of guessing
                // speaker identity from a mixed VOICE_CALL recording.
                val dualPipes = ShizukuAudioClient.startScrcpyDual(server.absolutePath)
                if (dualPipes != null && running.get()) {
                    activeSource = SOURCE_DUAL_ACTIVE
                    onStarted(Mode.DUAL)
                    runDualReaders(dualPipes, onFrame)?.let { failure = it }
                } else if (running.get()) {
                    // Device/OEM fallback: retain the already-proven mixed VOICE_CALL transcription path.
                    mixedPipe = ShizukuAudioClient.startScrcpy(server.absolutePath)
                        ?: throw IllegalStateException("scrcpy call capture did not return an audio pipe")
                    if (!running.get()) return@Thread

                    activeSource = SOURCE_MIXED_ACTIVE
                    onStarted(Mode.SINGLE)
                    val rawReader = ScrcpyRawPcmReader(ParcelFileDescriptor.AutoCloseInputStream(mixedPipe))
                    reader = rawReader
                    mixedPipe = null
                    rawReader.run(
                        onPcm = { pcm, length ->
                            if (running.get() && length > 0) onFrame(pcm, length, Direction.MIXED)
                        },
                        onStopped = { reason -> if (reason != null) failure = reason }
                    )
                }
            } catch (t: Throwable) {
                if (running.get()) failure = t.message ?: "scrcpy call audio failed: ${t.javaClass.simpleName}"
            } finally {
                runCatching { mixedPipe?.close() }
                reader = null
                downlinkReader = null
                uplinkReader = null
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

        return StartResult.Started(Format(), SOURCE_STARTING, Mode.SINGLE)
    }

    private fun runDualReaders(
        pipes: ShizukuAudioClient.DualScrcpyPipes,
        onFrame: (ByteArray, Int, Direction) -> Unit
    ): String? {
        val failure = AtomicReference<String?>(null)
        val finished = CountDownLatch(2)
        val shuttingDown = AtomicBoolean(false)

        val down = ScrcpyRawPcmReader(ParcelFileDescriptor.AutoCloseInputStream(pipes.downlink))
        val up = ScrcpyRawPcmReader(ParcelFileDescriptor.AutoCloseInputStream(pipes.uplink))
        downlinkReader = down
        uplinkReader = up

        fun streamEnded(direction: Direction, reason: String?) {
            if (running.get() && shuttingDown.compareAndSet(false, true)) {
                failure.compareAndSet(null, reason ?: "scrcpy ${direction.name.lowercase()} stream ended unexpectedly")
                running.set(false)
                if (direction != Direction.DOWNLINK) down.stop()
                if (direction != Direction.UPLINK) up.stop()
            }
            finished.countDown()
        }

        Thread({
            down.run(
                onPcm = { pcm, length -> if (running.get() && length > 0) onFrame(pcm, length, Direction.DOWNLINK) },
                onStopped = { reason -> streamEnded(Direction.DOWNLINK, reason) }
            )
        }, "reality-scrcpy-downlink-pcm").apply { isDaemon = true; start() }

        Thread({
            up.run(
                onPcm = { pcm, length -> if (running.get() && length > 0) onFrame(pcm, length, Direction.UPLINK) },
                onStopped = { reason -> streamEnded(Direction.UPLINK, reason) }
            )
        }, "reality-scrcpy-uplink-pcm").apply { isDaemon = true; start() }

        try {
            finished.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            if (running.get()) failure.compareAndSet(null, "Directional call audio reader interrupted")
        }
        return failure.get()
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        activeSource = null
        reader?.stop()
        downlinkReader?.stop()
        uplinkReader?.stop()
        worker?.interrupt()
    }

    fun isRunning(): Boolean = running.get()

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val SOURCE_STARTING = "SHIZUKU_SCRCPY_STARTING"
        private const val SOURCE_MIXED_ACTIVE = "SHIZUKU_SCRCPY_VOICE_CALL_RAW"
        private const val SOURCE_DUAL_ACTIVE = "SHIZUKU_SCRCPY_RX_TX_RAW"
    }
}

package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Guarded raw PCM capture source for devices where VOICE_CALL capture is available.
 * Shizuku remains the capability/permission gate; Samsung may still block the audio
 * source at runtime, in which case callers should select the configured fallback route.
 */
class VoiceCallPcmCapture(private val context: Context) {
    data class Format(val sampleRate: Int = 16_000, val channels: Int = 1, val bitsPerSample: Int = 16)

    sealed class StartResult {
        data class Started(val format: Format) : StartResult()
        data class Unavailable(val reason: String) : StartResult()
    }

    private val running = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null

    fun start(onPcm: (ByteArray, Int) -> Unit, onStopped: (String?) -> Unit = {}): StartResult {
        if (running.get()) return StartResult.Unavailable("Capture already running")
        if (!ShizukuAudioStatus.binderAvailable() || !ShizukuAudioStatus.permissionGranted()) {
            return StartResult.Unavailable("Shizuku authorization required")
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return StartResult.Unavailable("Microphone authorization required")
        }

        val format = Format()
        val minBuffer = AudioRecord.getMinBufferSize(format.sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return StartResult.Unavailable("Unsupported PCM format")

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_CALL,
                format.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, 8192)
            )
        } catch (_: SecurityException) {
            return StartResult.Unavailable("VOICE_CALL source blocked")
        } catch (_: Throwable) {
            return StartResult.Unavailable("VOICE_CALL source unavailable")
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return StartResult.Unavailable("VOICE_CALL source failed to initialize")
        }

        recorder = audioRecord
        running.set(true)
        worker = Thread({
            var failure: String? = null
            val buffer = ByteArray(maxOf(minBuffer, 4096))
            try {
                audioRecord.startRecording()
                if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    failure = "VOICE_CALL recording did not start"
                } else {
                    while (running.get()) {
                        val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                        when {
                            read > 0 -> onPcm(buffer.copyOf(read), read)
                            read == AudioRecord.ERROR_DEAD_OBJECT -> { failure = "Audio source disconnected"; break }
                            read < 0 -> { failure = "Audio read failed: $read"; break }
                        }
                    }
                }
            } catch (_: SecurityException) {
                failure = "VOICE_CALL capture blocked at runtime"
            } catch (_: Throwable) {
                failure = "VOICE_CALL capture stopped unexpectedly"
            } finally {
                running.set(false)
                try { audioRecord.stop() } catch (_: Throwable) { }
                try { audioRecord.release() } catch (_: Throwable) { }
                if (recorder === audioRecord) recorder = null
                onStopped(failure)
            }
        }, "reality-voice-call-pcm").apply { isDaemon = true; start() }

        return StartResult.Started(format)
    }

    fun stop() {
        running.set(false)
        try { recorder?.stop() } catch (_: Throwable) { }
        worker?.interrupt()
        worker = null
    }

    fun isRunning(): Boolean = running.get()
}

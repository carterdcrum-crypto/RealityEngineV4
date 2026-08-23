package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

/** Guarded 16 kHz mono PCM capture for the native call-audio path. */
class VoiceCallPcmCapture(context: Context) {
    data class Format(val sampleRate: Int = SAMPLE_RATE, val channels: Int = 1, val bitsPerSample: Int = 16)
    sealed class StartResult { data class Started(val format: Format) : StartResult(); data class Unavailable(val reason: String) : StartResult() }

    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null

    fun start(onPcm: (ByteArray, Int) -> Unit, onStopped: (String?) -> Unit = {}): StartResult {
        if (running.get()) return StartResult.Unavailable("Capture already running")
        if (!ShizukuAudioStatus.binderAvailable() || !ShizukuAudioStatus.permissionGranted()) return StartResult.Unavailable("Shizuku authorization required")
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return StartResult.Unavailable("Microphone authorization required")
        if (CallAudioBridge.state(appContext) != CallAudioBridge.State.VOICE_CALL_SOURCE_AVAILABLE) return StartResult.Unavailable("VOICE_CALL source unavailable")

        val format = Format()
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer <= 0) return StartResult.Unavailable("Unsupported PCM format")
        val bufferSize = maxOf(minBuffer * 2, 8192)
        val audioRecord = try { AudioRecord(MediaRecorder.AudioSource.VOICE_CALL, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize) }
        catch (_: SecurityException) { return StartResult.Unavailable("VOICE_CALL source blocked") }
        catch (_: Throwable) { return StartResult.Unavailable("VOICE_CALL source unavailable") }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) { audioRecord.release(); return StartResult.Unavailable("VOICE_CALL source failed to initialize") }

        recorder = audioRecord; running.set(true)
        worker = Thread({
            var failure: String? = null
            val buffer = ByteArray(bufferSize)
            try {
                audioRecord.startRecording()
                if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) failure = "VOICE_CALL recording did not start"
                else while (running.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    when { read > 0 -> onPcm(buffer.copyOf(read), read); read == AudioRecord.ERROR_DEAD_OBJECT -> { failure="Audio source disconnected"; break }; read < 0 -> { failure="Audio read failed: $read"; break } }
                }
            } catch (_: SecurityException) { failure="VOICE_CALL capture blocked at runtime" }
            catch (_: Throwable) { if (running.get()) failure="VOICE_CALL capture stopped unexpectedly" }
            finally {
                running.set(false)
                try { audioRecord.stop() } catch (_: Throwable) { }
                try { audioRecord.release() } catch (_: Throwable) { }
                if (recorder === audioRecord) recorder=null
                onStopped(failure)
            }
        }, "reality-voice-call-pcm").apply { isDaemon=true; start() }
        return StartResult.Started(format)
    }

    fun stop() { running.set(false); try { recorder?.stop() } catch (_: Throwable) { }; worker?.interrupt(); worker=null }
    fun isRunning(): Boolean = running.get()

    companion object { const val SAMPLE_RATE=16_000; const val CHANNEL_CONFIG=AudioFormat.CHANNEL_IN_MONO; const val AUDIO_FORMAT=AudioFormat.ENCODING_PCM_16BIT }
}

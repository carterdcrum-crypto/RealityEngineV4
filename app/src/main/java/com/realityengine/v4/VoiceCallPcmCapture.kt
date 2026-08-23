package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

/** Guarded 16 kHz mono PCM capture. Tries protected VOICE_CALL first, then the
 * device-supported VOICE_COMMUNICATION path discovered by active-call diagnostics. */
class VoiceCallPcmCapture(context: Context) {
    data class Format(val sampleRate: Int = SAMPLE_RATE, val channels: Int = 1, val bitsPerSample: Int = 16)
    sealed class StartResult { data class Started(val format: Format, val source: String) : StartResult(); data class Unavailable(val reason: String) : StartResult() }

    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    @Volatile var activeSource: String? = null
        private set

    fun start(onPcm: (ByteArray, Int) -> Unit, onStopped: (String?) -> Unit = {}): StartResult {
        if (running.get()) return StartResult.Unavailable("Capture already running")
        if (!ShizukuAudioStatus.binderAvailable() || !ShizukuAudioStatus.permissionGranted()) return StartResult.Unavailable("Shizuku authorization required")
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return StartResult.Unavailable("Microphone authorization required")

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer <= 0) return StartResult.Unavailable("Unsupported PCM format")
        val bufferSize = maxOf(minBuffer * 2, 8192)
        val candidates = arrayOf(
            MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION"
        )
        var audioRecord: AudioRecord? = null
        var sourceName: String? = null
        for ((source, name) in candidates) {
            val candidate = try { AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize) } catch (_: Throwable) { null }
            if (candidate?.state == AudioRecord.STATE_INITIALIZED) { audioRecord=candidate;sourceName=name;break }
            try { candidate?.release() } catch (_: Throwable) { }
        }
        val selected = audioRecord ?: return StartResult.Unavailable("VOICE_CALL and VOICE_COMMUNICATION unavailable")
        val selectedName = sourceName ?: "UNKNOWN"
        activeSource=selectedName;recorder=selected;running.set(true)
        worker = Thread({
            var failure: String? = null
            val buffer = ByteArray(bufferSize)
            try {
                selected.startRecording()
                if (selected.recordingState != AudioRecord.RECORDSTATE_RECORDING) failure = "$selectedName recording did not start"
                else while (running.get()) {
                    val read = selected.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    when { read > 0 -> onPcm(buffer.copyOf(read), read); read == AudioRecord.ERROR_DEAD_OBJECT -> { failure="Audio source disconnected"; break }; read < 0 -> { failure="Audio read failed: $read"; break } }
                }
            } catch (_: SecurityException) { failure="$selectedName capture blocked at runtime" }
            catch (_: Throwable) { if (running.get()) failure="$selectedName capture stopped unexpectedly" }
            finally {
                running.set(false);activeSource=null
                try { selected.stop() } catch (_: Throwable) { }
                try { selected.release() } catch (_: Throwable) { }
                if (recorder === selected) recorder=null
                onStopped(failure)
            }
        }, "reality-call-pcm").apply { isDaemon=true; start() }
        return StartResult.Started(Format(),selectedName)
    }

    fun stop() { running.set(false);activeSource=null;try { recorder?.stop() } catch (_: Throwable) { };worker?.interrupt();worker=null }
    fun isRunning(): Boolean = running.get()

    companion object { const val SAMPLE_RATE=16_000; const val CHANNEL_CONFIG=AudioFormat.CHANNEL_IN_MONO; const val AUDIO_FORMAT=AudioFormat.ENCODING_PCM_16BIT }
}

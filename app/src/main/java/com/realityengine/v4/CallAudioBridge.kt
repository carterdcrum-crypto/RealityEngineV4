package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

/** Capability boundary for the call-audio layer. No audio is recorded here. */
object CallAudioBridge {
    enum class State { UNAVAILABLE, SHIZUKU_READY, VOICE_CALL_SOURCE_AVAILABLE }

    fun state(context: Context? = null): State {
        if (!ShizukuAudioStatus.binderAvailable() || !ShizukuAudioStatus.permissionGranted()) {
            return State.UNAVAILABLE
        }
        if (context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return State.SHIZUKU_READY
        }
        return if (context != null && canInitializeVoiceCallRecorder()) State.VOICE_CALL_SOURCE_AVAILABLE else State.SHIZUKU_READY
    }

    fun isCallSourceSupported(audioManager: AudioManager): Boolean =
        audioManager.getParameters("input_source").contains("VOICE_CALL", ignoreCase = true)

    private fun canInitializeVoiceCallRecorder(): Boolean {
        return try {
            val minBuffer = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) return false
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_CALL,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
            val ready = recorder.state == AudioRecord.STATE_INITIALIZED
            recorder.release()
            ready
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }
}

package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.telecom.Call

/** Capability status for call audio. Hardware probing is deferred until an active call,
 * because some Android devices cannot initialize the call source while idle. */
object CallAudioBridge {
    enum class State {
        UNAVAILABLE,
        SHIZUKU_READY,
        MICROPHONE_PERMISSION_REQUIRED,
        VOICE_CALL_SOURCE_AVAILABLE,
        VOICE_CALL_SOURCE_BLOCKED
    }

    fun state(context: Context): State {
        if (!ShizukuAudioStatus.binderAvailable() || !ShizukuAudioStatus.permissionGranted()) {
            return State.UNAVAILABLE
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return State.MICROPHONE_PERMISSION_REQUIRED
        }
        val activeCall = CallSessionRegistry.primary()
        if (activeCall?.state != Call.STATE_ACTIVE) return State.SHIZUKU_READY
        return if (canInitializeVoiceCallSource()) State.VOICE_CALL_SOURCE_AVAILABLE
        else State.VOICE_CALL_SOURCE_BLOCKED
    }

    private fun canInitializeVoiceCallSource(): Boolean {
        var recorder: AudioRecord? = null
        return try {
            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) return false
            recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_CALL, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
            recorder.state == AudioRecord.STATE_INITIALIZED
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        } finally {
            try { recorder?.release() } catch (_: Throwable) { }
        }
    }
}

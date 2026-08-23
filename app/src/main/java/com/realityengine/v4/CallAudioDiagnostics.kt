package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.telecom.Call

/** Non-recording diagnostics for the native cellular audio path.
 * Probes initialization only; it does not retain or transmit audio. */
object CallAudioDiagnostics {
    data class Report(
        val activeCall: Boolean,
        val microphoneGranted: Boolean,
        val shizukuBinder: Boolean,
        val shizukuGranted: Boolean,
        val voiceCallInitialized: Boolean,
        val voiceCommunicationInitialized: Boolean,
        val microphoneInitialized: Boolean,
        val detail: String
    )

    fun inspect(context: Context): Report {
        val active = CallSessionRegistry.primary()?.state == Call.STATE_ACTIVE
        val micGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val binder = ShizukuAudioStatus.binderAvailable()
        val shizuku = ShizukuAudioStatus.permissionGranted()
        if (!active || !micGranted) return Report(active, micGranted, binder, shizuku, false, false, false,
            if (!active) "Waiting for an active call" else "Microphone authorization required")

        val voiceCall = probe(MediaRecorder.AudioSource.VOICE_CALL)
        val voiceComm = probe(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        val microphone = probe(MediaRecorder.AudioSource.MIC)
        val detail = when {
            voiceCall -> "VOICE_CALL initializes"
            voiceComm -> "VOICE_CALL blocked; VOICE_COMMUNICATION initializes"
            microphone -> "Protected call sources blocked; microphone initializes"
            else -> "No tested input source initializes during this call"
        }
        return Report(active, micGranted, binder, shizuku, voiceCall, voiceComm, microphone, detail)
    }

    private fun probe(source: Int): Boolean {
        var record: AudioRecord? = null
        return try {
            val rate = 16_000
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return false
            record = AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 8192))
            record.state == AudioRecord.STATE_INITIALIZED
        } catch (_: Throwable) {
            false
        } finally {
            try { record?.release() } catch (_: Throwable) { }
        }
    }
}

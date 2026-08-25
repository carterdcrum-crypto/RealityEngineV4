package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.Call

/** Capability status for call audio. Shizuku authorization means the privileged bridge
 * is ready to be attempted; the live capture pipeline owns starting/stopping VOICE_CALL.
 * This avoids a status probe briefly opening and closing the same protected source that
 * transcription is about to consume. */
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
        return if (activeCall?.state == Call.STATE_ACTIVE) State.VOICE_CALL_SOURCE_AVAILABLE
        else State.SHIZUKU_READY
    }
}

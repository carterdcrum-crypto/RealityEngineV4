package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.Call

/** Capability status for call audio. The privileged UserService owns the VOICE_CALL
 * AudioRecord probe so the app process never makes a misleading unprivileged probe. */
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
        if (!ShizukuAudioClient.connect()) return false
        val result = ShizukuAudioClient.start()
        if (result == PrivilegedAudioService.START_OK) {
            ShizukuAudioClient.stop()
            return true
        }
        return false
    }
}

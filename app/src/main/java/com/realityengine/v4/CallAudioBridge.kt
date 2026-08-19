package com.realityengine.v4

import android.media.AudioManager

/** Readiness boundary for the call-audio layer. Capture remains disabled until a
 * supported, user-authorized audio transport is available. */
object CallAudioBridge {
    enum class State { UNAVAILABLE, SHIZUKU_READY }

    fun state(): State =
        if (ShizukuAudioStatus.binderAvailable() && ShizukuAudioStatus.permissionGranted()) {
            State.SHIZUKU_READY
        } else {
            State.UNAVAILABLE
        }

    fun isCallSourceSupported(audioManager: AudioManager): Boolean =
        audioManager.getParameters("input_source")
            .contains("VOICE_CALL", ignoreCase = true)
}

package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager

/** Beginner-facing readiness checks used by onboarding without changing setup behavior. */
object WalkthroughSetupStatus {
    data class Status(val ready: Boolean, val message: String)

    fun forStep(activity: Activity, step: WalkthroughContent.Step, settings: SettingsStore): Status? =
        when (WalkthroughActionRouter.actionFor(step)) {
            WalkthroughActionRouter.Action.DEFAULT_PHONE -> {
                val telecom = activity.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val ready = telecom.defaultDialerPackage == activity.packageName
                Status(ready, if (ready) "Ready — Phone is your phone app" else "Needs setup — choose Phone as your phone app")
            }
            WalkthroughActionRouter.Action.PERMISSIONS -> {
                val mic = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                Status(mic, if (mic) "Ready — microphone access is allowed" else "Needs setup — microphone access is required")
            }
            WalkthroughActionRouter.Action.SHIZUKU -> {
                val ready = ShizukuAudioStatus.binderAvailable() && ShizukuAudioStatus.permissionGranted()
                Status(ready, if (ready) "Ready — Shizuku is connected" else "Needs setup — start and authorize Shizuku")
            }
            WalkthroughActionRouter.Action.TRANSCRIPTION_SETTINGS -> {
                val ready = settings.deepgramConfigured()
                Status(ready, if (ready) "Ready — transcription is configured" else "Needs setup — add your Deepgram key")
            }
            WalkthroughActionRouter.Action.COACH_SETTINGS -> {
                val ready = settings.groqConfigured() && settings.responseCoachEnabled
                Status(ready, if (ready) "Ready — response coach is configured" else "Needs setup — configure and enable the response coach")
            }
            WalkthroughActionRouter.Action.CALL_AUDIO -> when (CallAudioBridge.state(activity)) {
                CallAudioBridge.State.VOICE_CALL_SOURCE_AVAILABLE -> Status(true, "Ready — supported call audio is available")
                CallAudioBridge.State.MICROPHONE_PERMISSION_REQUIRED -> Status(false, "Needs setup — allow microphone access")
                CallAudioBridge.State.UNAVAILABLE -> Status(false, "Needs setup — connect Shizuku first")
                CallAudioBridge.State.SHIZUKU_READY -> Status(false, "Almost ready — run the call audio check")
                CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED -> Status(false, "Attention — this phone is blocking the call audio source")
            }
            WalkthroughActionRouter.Action.NONE -> null
        }
}

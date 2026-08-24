package com.realityengine.v4

import android.content.Context

/** Chooses the live transcription audio path. Hardware probes are collected here;
 * the actual decision table lives in AudioRoutePolicy so every route is unit-testable. */
class AudioCaptureRouter(context: Context) {
    enum class Route {
        SHIZUKU_VOICE_CALL,
        NATIVE_VOICE_COMMUNICATION,
        TWILIO_MEDIA_STREAM,
        MICROPHONE_PERMISSION_REQUIRED,
        SHIZUKU_PERMISSION_REQUIRED,
        TWILIO_CONFIGURATION_REQUIRED,
        UNAVAILABLE
    }

    data class Decision(val route: Route, val reason: String, val canTranscribe: Boolean)

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)

    fun decide(twilioCallActive: Boolean = false): Decision {
        val bridgeState = CallAudioBridge.state(appContext)
        val voiceComm = if (bridgeState == CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED)
            CallAudioDiagnostics.inspect(appContext).voiceCommunicationInitialized else false
        return AudioRoutePolicy.decide(
            AudioRoutePolicy.Inputs(
                bridgeState = bridgeState,
                shizukuAvailable = ShizukuAudioStatus.binderAvailable(),
                shizukuGranted = ShizukuAudioStatus.permissionGranted(),
                voiceCommunicationAvailable = voiceComm,
                twilioConfigured = settings.twilioMediaConfigured(),
                twilioCallActive = twilioCallActive
            )
        )
    }
}

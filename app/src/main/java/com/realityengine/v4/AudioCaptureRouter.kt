package com.realityengine.v4

import android.content.Context

/** Chooses the live transcription audio path. Protected cellular audio is owned solely
 * by the Shizuku bridge; the app process never probes VOICE_COMMUNICATION as a substitute. */
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

    fun decide(twilioCallActive: Boolean = false): Decision = AudioRoutePolicy.decide(
        AudioRoutePolicy.Inputs(
            bridgeState = CallAudioBridge.state(appContext),
            shizukuAvailable = ShizukuAudioStatus.binderAvailable(),
            shizukuGranted = ShizukuAudioStatus.permissionGranted(),
            voiceCommunicationAvailable = false,
            twilioConfigured = settings.twilioMediaConfigured(),
            twilioCallActive = twilioCallActive
        )
    )
}

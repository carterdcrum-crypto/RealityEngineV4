package com.realityengine.v4

import android.content.Context

/** Chooses the live transcription audio path. Native cellular capture is only
 * evaluated during an active call; an idle Shizuku-ready state is not a failure. */
class AudioCaptureRouter(context: Context) {
    enum class Route {
        SHIZUKU_VOICE_CALL,
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
        return when (CallAudioBridge.state(appContext)) {
            CallAudioBridge.State.VOICE_CALL_SOURCE_AVAILABLE -> Decision(
                Route.SHIZUKU_VOICE_CALL,
                "Call audio source available",
                true
            )
            CallAudioBridge.State.MICROPHONE_PERMISSION_REQUIRED -> Decision(
                Route.MICROPHONE_PERMISSION_REQUIRED,
                "Microphone authorization is required",
                false
            )
            CallAudioBridge.State.SHIZUKU_READY -> Decision(
                Route.UNAVAILABLE,
                "Shizuku ready; call audio will be checked when a call is active",
                false
            )
            CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED -> twilioFallback(twilioCallActive)
            CallAudioBridge.State.UNAVAILABLE -> {
                if (!ShizukuAudioStatus.binderAvailable() || !ShizukuAudioStatus.permissionGranted()) {
                    if (twilioCallActive && settings.twilioMediaConfigured()) twilioFallback(true)
                    else Decision(Route.SHIZUKU_PERMISSION_REQUIRED, "Shizuku is unavailable or not authorized", false)
                } else twilioFallback(twilioCallActive)
            }
        }
    }

    private fun twilioFallback(twilioCallActive: Boolean): Decision {
        if (!settings.twilioMediaConfigured()) return Decision(
            Route.TWILIO_CONFIGURATION_REQUIRED,
            "Native call audio is unavailable and Twilio fallback is not configured",
            false
        )
        if (!twilioCallActive) return Decision(
            Route.UNAVAILABLE,
            "Twilio fallback requires a call routed through Twilio",
            false
        )
        return Decision(Route.TWILIO_MEDIA_STREAM, "Using Twilio media stream", true)
    }
}

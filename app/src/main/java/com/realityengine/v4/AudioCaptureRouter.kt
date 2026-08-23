package com.realityengine.v4

import android.content.Context

/**
 * Chooses the live transcription audio path.
 * Native cellular calls prefer the Shizuku/VOICE_CALL path. If Android blocks
 * that source, a configured Twilio media path can be selected instead.
 *
 * Twilio fallback represents calls routed through Twilio Programmable Voice;
 * it cannot capture an arbitrary carrier call that never traverses Twilio.
 */
class AudioCaptureRouter(context: Context) {
    enum class Route {
        SHIZUKU_VOICE_CALL,
        TWILIO_MEDIA_STREAM,
        MICROPHONE_PERMISSION_REQUIRED,
        SHIZUKU_PERMISSION_REQUIRED,
        TWILIO_CONFIGURATION_REQUIRED,
        UNAVAILABLE
    }

    data class Decision(
        val route: Route,
        val reason: String,
        val canTranscribe: Boolean
    )

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)

    fun decide(twilioCallActive: Boolean = false): Decision {
        return when (CallAudioBridge.state(appContext)) {
            CallAudioBridge.State.VOICE_CALL_SOURCE_AVAILABLE -> Decision(
                Route.SHIZUKU_VOICE_CALL,
                "Shizuku authorized and Android VOICE_CALL source available",
                true
            )
            CallAudioBridge.State.MICROPHONE_PERMISSION_REQUIRED -> Decision(
                Route.MICROPHONE_PERMISSION_REQUIRED,
                "Microphone permission is required before call audio can be captured",
                false
            )
            CallAudioBridge.State.SHIZUKU_READY,
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
            "Native call audio is blocked and Twilio fallback is not configured",
            false
        )
        if (!twilioCallActive) return Decision(
            Route.UNAVAILABLE,
            "Native call audio is blocked; Twilio fallback requires the call to be routed through Twilio",
            false
        )
        return Decision(
            Route.TWILIO_MEDIA_STREAM,
            "Using Twilio media stream because native call audio is unavailable",
            true
        )
    }
}

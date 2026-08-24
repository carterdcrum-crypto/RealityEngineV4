package com.realityengine.v4

/** Pure routing policy for live transcription. Hardware/API probes stay outside this object. */
object AudioRoutePolicy {
    data class Inputs(
        val bridgeState: CallAudioBridge.State,
        val shizukuAvailable: Boolean,
        val shizukuGranted: Boolean,
        val voiceCommunicationAvailable: Boolean,
        val twilioConfigured: Boolean,
        val twilioCallActive: Boolean
    )

    fun decide(i: Inputs): AudioCaptureRouter.Decision = when (i.bridgeState) {
        CallAudioBridge.State.VOICE_CALL_SOURCE_AVAILABLE ->
            AudioCaptureRouter.Decision(AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL, "VOICE_CALL source available", true)
        CallAudioBridge.State.MICROPHONE_PERMISSION_REQUIRED ->
            AudioCaptureRouter.Decision(AudioCaptureRouter.Route.MICROPHONE_PERMISSION_REQUIRED, "Microphone authorization is required", false)
        CallAudioBridge.State.SHIZUKU_READY ->
            AudioCaptureRouter.Decision(AudioCaptureRouter.Route.UNAVAILABLE, "Shizuku ready; call audio will be checked when a call is active", false)
        CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED -> {
            if (i.voiceCommunicationAvailable)
                AudioCaptureRouter.Decision(AudioCaptureRouter.Route.NATIVE_VOICE_COMMUNICATION, "VOICE_CALL blocked; using VOICE_COMMUNICATION", true)
            else fallback(i)
        }
        CallAudioBridge.State.UNAVAILABLE -> {
            if (!i.shizukuAvailable || !i.shizukuGranted) {
                if (i.twilioCallActive && i.twilioConfigured) fallback(i)
                else AudioCaptureRouter.Decision(AudioCaptureRouter.Route.SHIZUKU_PERMISSION_REQUIRED, "Shizuku is unavailable or not authorized", false)
            } else fallback(i)
        }
    }

    private fun fallback(i: Inputs): AudioCaptureRouter.Decision {
        if (!i.twilioConfigured) return AudioCaptureRouter.Decision(AudioCaptureRouter.Route.TWILIO_CONFIGURATION_REQUIRED, "Native call audio is unavailable and Twilio fallback is not configured", false)
        if (!i.twilioCallActive) return AudioCaptureRouter.Decision(AudioCaptureRouter.Route.UNAVAILABLE, "Twilio fallback requires a call routed through Twilio", false)
        return AudioCaptureRouter.Decision(AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM, "Using Twilio media stream", true)
    }
}

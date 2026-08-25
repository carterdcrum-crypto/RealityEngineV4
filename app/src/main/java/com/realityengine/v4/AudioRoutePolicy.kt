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
            AudioCaptureRouter.Decision(AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL, "Two-sided call audio source available", true)
        CallAudioBridge.State.MICROPHONE_PERMISSION_REQUIRED ->
            AudioCaptureRouter.Decision(AudioCaptureRouter.Route.MICROPHONE_PERMISSION_REQUIRED, "Microphone authorization is required", false)
        CallAudioBridge.State.SHIZUKU_READY ->
            AudioCaptureRouter.Decision(AudioCaptureRouter.Route.UNAVAILABLE, "Call audio is being checked", false)
        CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED -> fallback(i)
        CallAudioBridge.State.UNAVAILABLE -> {
            if (!i.shizukuAvailable || !i.shizukuGranted) {
                if (i.twilioCallActive && i.twilioConfigured) fallback(i)
                else AudioCaptureRouter.Decision(AudioCaptureRouter.Route.SHIZUKU_PERMISSION_REQUIRED, "Call audio access is not available", false)
            } else fallback(i)
        }
    }

    private fun fallback(i: Inputs): AudioCaptureRouter.Decision {
        if (i.twilioConfigured && i.twilioCallActive)
            return AudioCaptureRouter.Decision(AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM, "Using supported two-sided media stream", true)
        if (i.voiceCommunicationAvailable)
            return AudioCaptureRouter.Decision(AudioCaptureRouter.Route.UNAVAILABLE, "This phone exposes microphone audio, but Android blocks two-sided cellular call capture", false)
        if (!i.twilioConfigured)
            return AudioCaptureRouter.Decision(AudioCaptureRouter.Route.TWILIO_CONFIGURATION_REQUIRED, "Two-sided cellular call audio is blocked on this phone; supported media-stream fallback is not configured", false)
        return AudioCaptureRouter.Decision(AudioCaptureRouter.Route.UNAVAILABLE, "Two-sided cellular call audio is blocked; media-stream fallback requires a routed call", false)
    }
}

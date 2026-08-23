package com.realityengine.v4

/** Presentation helper for live call audio/transcription routing. */
object AudioRouteLabel {
    fun current(): String {
        val state = AudioRouteState.snapshot()
        return when (state.route) {
            AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL -> "SHIZUKU // LIVE"
            AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM -> "TWILIO // FALLBACK"
            AudioCaptureRouter.Route.MICROPHONE_PERMISSION_REQUIRED -> "MIC // PERMISSION REQUIRED"
            AudioCaptureRouter.Route.SHIZUKU_PERMISSION_REQUIRED -> "SHIZUKU // PERMISSION REQUIRED"
            AudioCaptureRouter.Route.TWILIO_CONFIGURATION_REQUIRED -> "TWILIO // SETUP REQUIRED"
            AudioCaptureRouter.Route.UNAVAILABLE -> "AUDIO // UNAVAILABLE"
        }
    }

    fun detail(): String {
        val state = AudioRouteState.snapshot()
        return if (state.canTranscribe) current() else "${current()} · ${state.reason.take(80)}"
    }
}

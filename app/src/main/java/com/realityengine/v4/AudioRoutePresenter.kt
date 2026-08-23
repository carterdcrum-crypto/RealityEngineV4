package com.realityengine.v4

/** Converts audio routing telemetry into compact active-call UI text. */
object AudioRoutePresenter {
    fun label(snapshot: AudioRouteState.Snapshot = AudioRouteState.snapshot()): String = when (snapshot.route) {
        AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL -> "AUDIO // VOICE_CALL · LIVE"
        AudioCaptureRouter.Route.NATIVE_VOICE_COMMUNICATION -> "AUDIO // VOICE_COMM · LIVE"
        AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM -> "AUDIO // TWILIO · FALLBACK"
        AudioCaptureRouter.Route.MICROPHONE_PERMISSION_REQUIRED -> "AUDIO // MIC PERMISSION REQUIRED"
        AudioCaptureRouter.Route.SHIZUKU_PERMISSION_REQUIRED -> "AUDIO // SHIZUKU PERMISSION REQUIRED"
        AudioCaptureRouter.Route.TWILIO_CONFIGURATION_REQUIRED -> "AUDIO // TWILIO SETUP REQUIRED"
        AudioCaptureRouter.Route.UNAVAILABLE -> "AUDIO // UNAVAILABLE"
    }

    fun detail(snapshot: AudioRouteState.Snapshot = AudioRouteState.snapshot()): String {
        val status = if (snapshot.canTranscribe) "TRANSCRIPTION READY" else "TRANSCRIPTION BLOCKED"
        return "$status · ${snapshot.reason.take(96)}"
    }
}

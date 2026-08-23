package com.realityengine.v4

/** Converts audio routing telemetry into compact active-call UI text. */
object AudioRoutePresenter {
    fun label(snapshot: AudioRouteState.Snapshot = AudioRouteState.snapshot()): String = when (snapshot.route) {
        AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL -> "AUDIO // SHIZUKU · LIVE"
        AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM -> "AUDIO // TWILIO · FALLBACK"
        AudioCaptureRouter.Route.MICROPHONE_LOCAL_ONLY -> "AUDIO // MIC · LOCAL ONLY"
        AudioCaptureRouter.Route.UNAVAILABLE -> "AUDIO // UNAVAILABLE"
    }

    fun detail(snapshot: AudioRouteState.Snapshot = AudioRouteState.snapshot()): String {
        val status = if (snapshot.canTranscribe) "TRANSCRIPTION READY" else "TRANSCRIPTION BLOCKED"
        return "$status · ${snapshot.reason.take(96)}"
    }
}

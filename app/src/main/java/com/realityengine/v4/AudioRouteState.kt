package com.realityengine.v4

/** Process-local telemetry for the currently selected live transcription route. */
object AudioRouteState {
    data class Snapshot(
        val route: AudioCaptureRouter.Route = AudioCaptureRouter.Route.UNAVAILABLE,
        val reason: String = "Audio route not evaluated",
        val canTranscribe: Boolean = false,
        val updatedAtMs: Long = 0L
    ) {
        val label: String
            get() = when (route) {
                AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL -> "SHIZUKU // LIVE"
                AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM -> "TWILIO // FALLBACK"
                AudioCaptureRouter.Route.UNAVAILABLE -> "AUDIO // BLOCKED"
            }

        val detail: String
            get() = if (canTranscribe) "$label · TRANSCRIPTION READY" else "$label · $reason"
    }

    @Volatile private var current = Snapshot()

    fun publish(decision: AudioCaptureRouter.Decision) {
        current = Snapshot(
            route = decision.route,
            reason = decision.reason,
            canTranscribe = decision.canTranscribe,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    fun snapshot(): Snapshot = current

    fun clear() {
        current = Snapshot()
    }
}

package com.realityengine.v4

/** Process-local telemetry for the currently selected live transcription route. */
object AudioRouteState {
    data class Snapshot(
        val route: AudioCaptureRouter.Route = AudioCaptureRouter.Route.UNAVAILABLE,
        val reason: String = "Audio route not evaluated",
        val canTranscribe: Boolean = false,
        val updatedAtMs: Long = 0L
    )

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

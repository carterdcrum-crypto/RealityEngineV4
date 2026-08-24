package com.realityengine.v4

import android.content.Context

/** Process-local telemetry for the currently selected live transcription route. */
object AudioRouteState {
    data class Snapshot(
        val route: AudioCaptureRouter.Route = AudioCaptureRouter.Route.UNAVAILABLE,
        val reason: String = "Audio route not evaluated",
        val canTranscribe: Boolean = false,
        val diagnostic: String = "",
        val updatedAtMs: Long = 0L
    ) {
        val label: String
            get() = AudioRoutePresenter.label(this)

        val detail: String
            get() {
                val base = AudioRoutePresenter.detail(this)
                return if (diagnostic.isBlank()) base else "$base · ${diagnostic.take(160)}"
            }
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

    fun diagnose(context: Context) {
        val report = CallAudioDiagnostics.inspect(context.applicationContext)
        val previous = current
        current = previous.copy(
            diagnostic = buildString {
                append(report.detail)
                append(" · SHIZUKU ")
                append(if (report.shizukuBinder && report.shizukuGranted) "OK" else "NO")
                append(" · VC=").append(if (report.voiceCallInitialized) "Y" else "N")
                append(" COMM=").append(if (report.voiceCommunicationInitialized) "Y" else "N")
                append(" MIC=").append(if (report.microphoneInitialized) "Y" else "N")
            },
            updatedAtMs = System.currentTimeMillis()
        )
    }

    fun snapshot(): Snapshot = current
    fun clear() { current = Snapshot() }
}

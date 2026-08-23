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
            get() = when (route) {
                AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL -> "SHIZUKU // LIVE"
                AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM -> "TWILIO // FALLBACK"
                AudioCaptureRouter.Route.MICROPHONE_PERMISSION_REQUIRED -> "MIC // PERMISSION REQUIRED"
                AudioCaptureRouter.Route.SHIZUKU_PERMISSION_REQUIRED -> "SHIZUKU // PERMISSION REQUIRED"
                AudioCaptureRouter.Route.TWILIO_CONFIGURATION_REQUIRED -> "TWILIO // SETUP REQUIRED"
                AudioCaptureRouter.Route.UNAVAILABLE -> "AUDIO // BLOCKED"
            }

        val detail: String
            get() {
                val base = if (canTranscribe) "$label · TRANSCRIPTION READY" else "$label · $reason"
                return if (diagnostic.isBlank()) base else "$base · $diagnostic"
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

    /** Adds device-specific source diagnostics during an active call without retaining audio. */
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

    fun clear() {
        current = Snapshot()
    }
}

package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRoutePresenterTest {
    private fun snapshot(
        route: AudioCaptureRouter.Route,
        canTranscribe: Boolean,
        reason: String = "ok"
    ) = AudioRouteState.Snapshot(route, canTranscribe, reason)

    @Test fun `live Shizuku route clearly reports transcription ready`() {
        val s = snapshot(AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL, true, "call audio available")
        assertEquals("AUDIO // VOICE_CALL · LIVE", AudioRoutePresenter.label(s))
        assertTrue(AudioRoutePresenter.detail(s).startsWith("TRANSCRIPTION READY"))
    }

    @Test fun `native communication fallback remains visibly live`() {
        val s = snapshot(AudioCaptureRouter.Route.NATIVE_VOICE_COMMUNICATION, true)
        assertEquals("AUDIO // VOICE_COMM · LIVE", AudioRoutePresenter.label(s))
        assertTrue(AudioRoutePresenter.detail(s).startsWith("TRANSCRIPTION READY"))
    }

    @Test fun `blocked routes never claim transcription ready`() {
        val blocked = listOf(
            AudioCaptureRouter.Route.MICROPHONE_PERMISSION_REQUIRED,
            AudioCaptureRouter.Route.SHIZUKU_PERMISSION_REQUIRED,
            AudioCaptureRouter.Route.TWILIO_CONFIGURATION_REQUIRED,
            AudioCaptureRouter.Route.UNAVAILABLE
        )
        blocked.forEach { route ->
            val s = snapshot(route, false, "waiting")
            assertTrue(AudioRoutePresenter.detail(s).startsWith("TRANSCRIPTION BLOCKED"))
        }
    }

    @Test fun `diagnostic reason is bounded for active call UI`() {
        val longReason = "x".repeat(300)
        val detail = AudioRoutePresenter.detail(snapshot(AudioCaptureRouter.Route.UNAVAILABLE, false, longReason))
        assertEquals("TRANSCRIPTION BLOCKED · ".length + 96, detail.length)
    }
}

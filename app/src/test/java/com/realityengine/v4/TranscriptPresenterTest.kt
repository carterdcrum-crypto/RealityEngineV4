package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptPresenterTest {
    private val route = AudioRouteState.Snapshot()

    @Test fun `empty transcript waits for audio`() {
        assertEquals("AWAITING AUDIO STREAM…", TranscriptPresenter.render(LiveTranscriptState.State(), route))
    }

    @Test fun `route diagnostics appear before speech arrives`() {
        val liveRoute = AudioRouteState.Snapshot(
            route = AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL,
            reason = "ready",
            canTranscribe = true,
            updatedAtMs = 1L
        )
        val rendered = TranscriptPresenter.render(LiveTranscriptState.State(), liveRoute)
        assertTrue(rendered.startsWith("○ CALL AUDIO //"))
        assertTrue(rendered.contains("TRANSCRIPTION READY"))
    }

    @Test fun `final history is preserved while interim speech is appended`() {
        val entries = listOf(
            LiveTranscriptState.Entry("first", true, 1L),
            LiveTranscriptState.Entry("second", true, 2L)
        )
        val state = LiveTranscriptState.State("working", false, 3L, entries)
        val rendered = TranscriptPresenter.render(state, route)
        assertEquals("● first\n● second\n○ working", rendered)
    }

    @Test fun `only eight latest finalized lines are shown`() {
        val entries = (1..10).map { LiveTranscriptState.Entry("line-$it", true, it.toLong()) }
        val rendered = TranscriptPresenter.render(LiveTranscriptState.State("line-10", true, 10L, entries), route)
        assertFalse(rendered.contains("line-1\n"))
        assertFalse(rendered.contains("line-2\n"))
        assertTrue(rendered.startsWith("● line-3"))
        assertTrue(rendered.endsWith("● line-10"))
    }
}

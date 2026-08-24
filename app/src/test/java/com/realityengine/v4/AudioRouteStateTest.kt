package com.realityengine.v4

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRouteStateTest {
    @Test fun `unavailable route is clearly blocked and not transcription ready`() {
        val s = AudioRouteState.Snapshot(
            route = AudioCaptureRouter.Route.UNAVAILABLE,
            reason = "No supported call-audio source",
            canTranscribe = false
        )
        assertTrue(s.label.contains("UNAVAILABLE"))
        assertTrue(s.detail.startsWith("TRANSCRIPTION BLOCKED"))
        assertTrue(s.detail.contains("No supported call-audio source"))
        assertFalse(s.detail.contains("TRANSCRIPTION READY"))
    }

    @Test fun `shizuku voice call route reports live transcription readiness`() {
        val s = AudioRouteState.Snapshot(
            route = AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL,
            reason = "VOICE_CALL initialized",
            canTranscribe = true
        )
        assertTrue(s.label.contains("VOICE_CALL"))
        assertTrue(s.detail.contains("TRANSCRIPTION READY"))
        assertFalse(s.detail.contains("TRANSCRIPTION BLOCKED"))
    }

    @Test fun `native voice communication route is represented independently`() {
        val s = AudioRouteState.Snapshot(
            route = AudioCaptureRouter.Route.NATIVE_VOICE_COMMUNICATION,
            canTranscribe = true
        )
        assertTrue(s.label.contains("VOICE_COMM"))
        assertTrue(s.detail.contains("TRANSCRIPTION READY"))
    }

    @Test fun `permission states never claim transcription readiness`() {
        val mic = AudioRouteState.Snapshot(
            route = AudioCaptureRouter.Route.MICROPHONE_PERMISSION_REQUIRED,
            reason = "Grant microphone permission"
        )
        val shizuku = AudioRouteState.Snapshot(
            route = AudioCaptureRouter.Route.SHIZUKU_PERMISSION_REQUIRED,
            reason = "Grant Shizuku permission"
        )
        assertTrue(mic.label.contains("PERMISSION REQUIRED"))
        assertTrue(shizuku.label.contains("PERMISSION REQUIRED"))
        assertFalse(mic.detail.contains("TRANSCRIPTION READY"))
        assertFalse(shizuku.detail.contains("TRANSCRIPTION READY"))
    }

    @Test fun `diagnostic suffix cannot overwrite the route truth`() {
        val s = AudioRouteState.Snapshot(
            route = AudioCaptureRouter.Route.UNAVAILABLE,
            reason = "Blocked",
            canTranscribe = false,
            diagnostic = "SHIZUKU OK · VC=N COMM=N MIC=Y"
        )
        assertTrue(s.label.contains("UNAVAILABLE"))
        assertTrue(s.detail.startsWith("TRANSCRIPTION BLOCKED"))
        assertTrue(s.detail.contains("SHIZUKU OK"))
        assertFalse(s.detail.contains("TRANSCRIPTION READY"))
    }
}

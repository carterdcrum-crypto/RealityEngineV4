package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRoutePolicyTest {
    private fun decide(
        state: CallAudioBridge.State,
        shizuku: Boolean = true,
        granted: Boolean = true,
        voiceComm: Boolean = false,
        twilio: Boolean = false,
        active: Boolean = false
    ) = AudioRoutePolicy.decide(AudioRoutePolicy.Inputs(state, shizuku, granted, voiceComm, twilio, active))

    @Test fun `voice call source is preferred and transcription ready`() {
        val d = decide(CallAudioBridge.State.VOICE_CALL_SOURCE_AVAILABLE)
        assertEquals(AudioCaptureRouter.Route.SHIZUKU_VOICE_CALL, d.route)
        assertTrue(d.canTranscribe)
    }

    @Test fun `shizuku ready before active call is waiting not blocked permission`() {
        val d = decide(CallAudioBridge.State.SHIZUKU_READY)
        assertEquals(AudioCaptureRouter.Route.UNAVAILABLE, d.route)
        assertFalse(d.canTranscribe)
        assertTrue(d.reason.contains("call audio will be checked"))
    }

    @Test fun `blocked voice call uses voice communication only when probe succeeds`() {
        val fallback = decide(CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED, voiceComm = true)
        assertEquals(AudioCaptureRouter.Route.NATIVE_VOICE_COMMUNICATION, fallback.route)
        assertTrue(fallback.canTranscribe)
        val blocked = decide(CallAudioBridge.State.VOICE_CALL_SOURCE_BLOCKED)
        assertEquals(AudioCaptureRouter.Route.TWILIO_CONFIGURATION_REQUIRED, blocked.route)
        assertFalse(blocked.canTranscribe)
    }

    @Test fun `missing shizuku authorization is explicit`() {
        val d = decide(CallAudioBridge.State.UNAVAILABLE, shizuku = false, granted = false)
        assertEquals(AudioCaptureRouter.Route.SHIZUKU_PERMISSION_REQUIRED, d.route)
        assertFalse(d.canTranscribe)
    }

    @Test fun `twilio is ready only when configured and routed call is active`() {
        val waiting = decide(CallAudioBridge.State.UNAVAILABLE, twilio = true, active = false)
        assertEquals(AudioCaptureRouter.Route.UNAVAILABLE, waiting.route)
        assertFalse(waiting.canTranscribe)
        val live = decide(CallAudioBridge.State.UNAVAILABLE, shizuku = false, granted = false, twilio = true, active = true)
        assertEquals(AudioCaptureRouter.Route.TWILIO_MEDIA_STREAM, live.route)
        assertTrue(live.canTranscribe)
    }

    @Test fun `microphone permission requirement never claims transcription ready`() {
        val d = decide(CallAudioBridge.State.MICROPHONE_PERMISSION_REQUIRED)
        assertEquals(AudioCaptureRouter.Route.MICROPHONE_PERMISSION_REQUIRED, d.route)
        assertFalse(d.canTranscribe)
    }
}

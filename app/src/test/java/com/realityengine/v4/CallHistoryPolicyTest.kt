package com.realityengine.v4

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallHistoryPolicyTest {
    @Test fun `null limit means complete available history`() {
        assertNull(CallHistoryPolicy.normalizeLimit(null))
        assertTrue(CallHistoryPolicy.shouldContinue(0, null))
        assertTrue(CallHistoryPolicy.shouldContinue(10_000, null))
    }

    @Test fun `explicit limit is enforced and negative limit is safe`() {
        assertEquals(25, CallHistoryPolicy.normalizeLimit(25))
        assertTrue(CallHistoryPolicy.shouldContinue(24, 25))
        assertFalse(CallHistoryPolicy.shouldContinue(25, 25))
        assertEquals(0, CallHistoryPolicy.normalizeLimit(-5))
        assertFalse(CallHistoryPolicy.shouldContinue(0, -5))
    }

    @Test fun `all Android call directions have stable labels`() {
        assertEquals("IN", CallHistoryPolicy.direction(CallLog.Calls.INCOMING_TYPE))
        assertEquals("OUT", CallHistoryPolicy.direction(CallLog.Calls.OUTGOING_TYPE))
        assertEquals("MISSED", CallHistoryPolicy.direction(CallLog.Calls.MISSED_TYPE))
        assertEquals("REJECTED", CallHistoryPolicy.direction(CallLog.Calls.REJECTED_TYPE))
        assertEquals("BLOCKED", CallHistoryPolicy.direction(CallLog.Calls.BLOCKED_TYPE))
        assertEquals("VOICEMAIL", CallHistoryPolicy.direction(CallLog.Calls.VOICEMAIL_TYPE))
        assertEquals("CALL", CallHistoryPolicy.direction(Int.MAX_VALUE))
    }
}

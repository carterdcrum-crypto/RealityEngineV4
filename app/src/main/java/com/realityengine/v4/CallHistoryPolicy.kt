package com.realityengine.v4

import android.provider.CallLog

/** Pure call-history rules kept outside Android cursor code so they are deterministic and testable. */
object CallHistoryPolicy {
    fun normalizeLimit(limit: Int?): Int? = limit?.coerceAtLeast(0)

    fun shouldContinue(currentSize: Int, limit: Int?): Boolean {
        val safe = normalizeLimit(limit)
        return safe == null || currentSize < safe
    }

    fun direction(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "IN"
        CallLog.Calls.OUTGOING_TYPE -> "OUT"
        CallLog.Calls.MISSED_TYPE -> "MISSED"
        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
        CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
        CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
        else -> "CALL"
    }
}

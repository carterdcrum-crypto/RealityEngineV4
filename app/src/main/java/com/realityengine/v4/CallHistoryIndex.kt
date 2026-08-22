package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog

data class CallHistoryEntry(
    val number: String,
    val displayName: String,
    val direction: String,
    val durationSeconds: Long
)

/** Keeps call-log querying and contact-name resolution out of MainActivity. */
class CallHistoryIndex(
    private val context: Context,
    private val contacts: ContactIndex
) {
    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun recent(limit: Int = 30): List<CallHistoryEntry> {
        if (!hasPermission()) return emptyList()
        val result = ArrayList<CallHistoryEntry>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION
        )
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                while (cursor.moveToNext() && result.size < limit) {
                    val number = cursor.getString(numberIndex) ?: "Unknown"
                    val cached = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                    val display = cached ?: contacts.resolveName(number) ?: number
                    val direction = when (cursor.getInt(typeIndex)) {
                        CallLog.Calls.INCOMING_TYPE -> "IN"
                        CallLog.Calls.OUTGOING_TYPE -> "OUT"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        else -> "CALL"
                    }
                    result += CallHistoryEntry(
                        number = number,
                        displayName = display,
                        direction = direction,
                        durationSeconds = cursor.getLong(durationIndex)
                    )
                }
            }
        } catch (_: Throwable) {
            return emptyList()
        }
        return result
    }
}

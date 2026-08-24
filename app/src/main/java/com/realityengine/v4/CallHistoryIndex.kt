package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog

data class CallHistoryEntry(
    val number: String,
    val displayName: String,
    val direction: String,
    val durationSeconds: Long,
    val timestampMs: Long,
    val realitySummary: String = ""
)

/** Reads the complete call log exposed by Android and enriches it with Reality Engine profile data. */
class CallHistoryIndex(
    private val context: Context,
    private val contacts: ContactIndex
) {
    private val profiles = CallerProfileStore(context.applicationContext)

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun recent(limit: Int? = null): List<CallHistoryEntry> {
        if (!hasPermission()) return emptyList()
        val safeLimit = CallHistoryPolicy.normalizeLimit(limit)
        if (safeLimit == 0) return emptyList()
        val result = ArrayList<CallHistoryEntry>()
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DURATION, CallLog.Calls.DATE)
        try {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                while (CallHistoryPolicy.shouldContinue(result.size, safeLimit) && cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex)?.takeIf { it.isNotBlank() } ?: "Unknown"
                    val cached = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                    val display = cached ?: contacts.resolveName(number) ?: number
                    val summary = if (number == "Unknown") "" else profiles.load(number).lastCallSummary
                    result += CallHistoryEntry(number, display, CallHistoryPolicy.direction(cursor.getInt(typeIndex)), cursor.getLong(durationIndex), cursor.getLong(dateIndex), summary)
                }
            }
        } catch (_: SecurityException) { return emptyList() }
        catch (_: Throwable) { return emptyList() }
        return result
    }
}

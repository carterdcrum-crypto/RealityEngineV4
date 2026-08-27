package com.realityengine.v4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog

data class CallHistoryEntry(
    val id: Long,
    val number: String,
    val displayName: String,
    val direction: String,
    val durationSeconds: Long,
    val timestampMs: Long,
    val realitySummary: String = ""
)

/** Reads and, when authorized, removes Android call-log rows. */
class CallHistoryIndex(
    private val context: Context,
    private val contacts: ContactIndex
) {
    private val profiles = CallerProfileStore(context.applicationContext)

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun recent(limit: Int? = null): List<CallHistoryEntry> {
        if (!hasPermission()) return emptyList()
        val safeLimit = CallHistoryPolicy.normalizeLimit(limit)
        if (safeLimit == 0) return emptyList()
        val result = ArrayList<CallHistoryEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
        )
        try {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                while (CallHistoryPolicy.shouldContinue(result.size, safeLimit) && cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex)?.takeIf { it.isNotBlank() } ?: "Unknown"
                    val cached = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                    val display = cached ?: contacts.resolveName(number) ?: number
                    val summary = profiles.load(number).lastCallSummary
                    result += CallHistoryEntry(
                        id = cursor.getLong(idIndex),
                        number = number,
                        displayName = display,
                        direction = CallHistoryPolicy.direction(cursor.getInt(typeIndex)),
                        durationSeconds = cursor.getLong(durationIndex),
                        timestampMs = cursor.getLong(dateIndex),
                        realitySummary = summary,
                    )
                }
            }
        } catch (_: SecurityException) { return emptyList() }
        catch (_: Throwable) { return emptyList() }
        return result
    }

    fun delete(entry: CallHistoryEntry): Boolean {
        if (!hasWritePermission() || entry.id < 0L) return false
        return try {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID}=?",
                arrayOf(entry.id.toString()),
            ) > 0
        } catch (_: Throwable) {
            false
        }
    }

    fun clearAll(): Int {
        if (!hasWritePermission()) return 0
        return try { context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null) }
        catch (_: Throwable) { 0 }
    }
}

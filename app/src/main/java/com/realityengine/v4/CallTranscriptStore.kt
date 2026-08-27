package com.realityengine.v4

import android.content.Context
import java.io.File

/** Private on-device storage for completed call transcripts. */
object CallTranscriptStore {
    data class SavedTranscript(
        val file: File,
        val phoneNumber: String,
        val timestampMs: Long,
        val turnCount: Int,
        val text: String,
    )

    fun save(context: Context, phoneNumber: String, entries: List<LiveTranscriptState.Entry>): SavedTranscript? {
        val finalized = entries.filter { it.isFinal && it.text.isNotBlank() }
        if (finalized.isEmpty()) return null
        val phone = phoneNumber.ifBlank { "Unknown" }
        val rendered = render(finalized)
        if (rendered.isBlank()) return null
        val timestamp = finalized.firstOrNull()?.updatedAtMs?.takeIf { it > 0L } ?: System.currentTimeMillis()
        val dir = File(context.filesDir, "call_transcripts/${storageKey(phone)}").apply { mkdirs() }
        val file = File(dir, "transcript_${timestamp}.txt")
        file.writeText(rendered, Charsets.UTF_8)
        return SavedTranscript(file, phone, timestamp, finalized.size, rendered)
    }

    fun savedFor(context: Context, phoneNumber: String): List<SavedTranscript> {
        val phone = phoneNumber.ifBlank { "Unknown" }
        val dir = File(context.filesDir, "call_transcripts/${storageKey(phone)}")
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("transcript_") && it.name.endsWith(".txt") }
            ?.mapNotNull { file ->
                runCatching {
                    val ts = file.name.removePrefix("transcript_").removeSuffix(".txt").toLongOrNull()
                        ?: file.lastModified()
                    val text = file.readText(Charsets.UTF_8)
                    SavedTranscript(file, phone, ts, text.lineSequence().count { it.isNotBlank() }, text)
                }.getOrNull()
            }
            ?.sortedByDescending { it.timestampMs }
            .orEmpty()
    }

    fun savedAll(context: Context): List<SavedTranscript> {
        val root = File(context.filesDir, "call_transcripts")
        return root.listFiles()?.filter { it.isDirectory }?.flatMap { dir ->
            dir.listFiles()?.filter { it.isFile && it.name.startsWith("transcript_") && it.name.endsWith(".txt") }?.mapNotNull { file ->
                runCatching {
                    val text = file.readText(Charsets.UTF_8)
                    val ts = file.name.removePrefix("transcript_").removeSuffix(".txt").toLongOrNull() ?: file.lastModified()
                    SavedTranscript(file, dir.name, ts, text.lineSequence().count { it.isNotBlank() }, text)
                }.getOrNull()
            }.orEmpty()
        }?.sortedByDescending { it.timestampMs }.orEmpty()
    }

    fun delete(saved: SavedTranscript): Boolean = saved.file.delete()

    internal fun render(entries: List<LiveTranscriptState.Entry>): String = entries.joinToString("\n") { entry ->
        val speaker = when (entry.isCaller) {
            true -> "CALLER"
            false -> "YOU"
            null -> "VOICE"
        }
        "$speaker: ${entry.text.trim()}"
    }

    internal fun storageKey(phoneNumber: String): String = PhoneNumberKey.normalize(phoneNumber)?.ifBlank { "unknown" } ?: "unknown"
}

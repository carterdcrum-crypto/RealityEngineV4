package com.realityengine.v4

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Private on-device storage for user-approved call recordings. */
object CallRecordingStore {
    data class PendingRecording(
        val phoneNumber: String,
        val displayName: String,
        val file: File,
        val sampleRate: Int,
        val channels: Int,
        val startedAtMs: Long,
        val durationSeconds: Long,
    )

    data class SavedRecording(
        val file: File,
        val timestampMs: Long,
        val durationSeconds: Long,
        val channels: Int,
    )

    class Writer internal constructor(
        private val phoneNumber: String,
        private val displayName: String,
        private val file: File,
        private val sampleRate: Int,
        private val channels: Int,
        private val startedAtMs: Long,
    ) {
        private val raf = RandomAccessFile(file, "rw")
        private var dataBytes = 0L
        private var closed = false

        init {
            raf.setLength(0L)
            raf.write(ByteArray(WAV_HEADER_BYTES))
        }

        @Synchronized
        fun write(bytes: ByteArray, length: Int) {
            if (closed) return
            val count = length.coerceIn(0, bytes.size)
            if (count <= 0) return
            raf.write(bytes, 0, count)
            dataBytes += count
        }

        @Synchronized
        fun finish(): PendingRecording? {
            if (closed) return null
            closed = true
            return try {
                raf.seek(0L)
                raf.write(wavHeader(sampleRate, channels, dataBytes))
                raf.fd.sync()
                raf.close()
                if (dataBytes <= 0L) {
                    file.delete()
                    null
                } else {
                    val bytesPerSecond = sampleRate.toLong() * channels * 2L
                    PendingRecording(
                        phoneNumber = phoneNumber,
                        displayName = displayName,
                        file = file,
                        sampleRate = sampleRate,
                        channels = channels,
                        startedAtMs = startedAtMs,
                        durationSeconds = if (bytesPerSecond > 0) dataBytes / bytesPerSecond else 0L,
                    )
                }
            } catch (_: Throwable) {
                runCatching { raf.close() }
                file.delete()
                null
            }
        }

        @Synchronized
        fun discard() {
            if (!closed) runCatching { raf.close() }
            closed = true
            file.delete()
        }
    }

    fun begin(
        context: Context,
        phoneNumber: String,
        displayName: String,
        sampleRate: Int,
        channels: Int,
    ): Writer? {
        if (sampleRate <= 0 || channels !in 1..2) return null
        return try {
            val dir = File(context.cacheDir, "call_recordings_pending").apply { mkdirs() }
            val started = System.currentTimeMillis()
            val file = File(dir, "pending_${started}_${System.nanoTime()}.wav")
            Writer(phoneNumber, displayName, file, sampleRate, channels, started)
        } catch (_: Throwable) {
            null
        }
    }

    fun savePending(context: Context, pending: PendingRecording): SavedRecording? {
        if (!pending.file.exists()) return null
        return try {
            val dir = recordingDir(context, pending.phoneNumber).apply { mkdirs() }
            val destination = File(dir, "call_${pending.startedAtMs}.wav")
            if (!pending.file.renameTo(destination)) {
                pending.file.copyTo(destination, overwrite = true)
                pending.file.delete()
            }
            SavedRecording(destination, pending.startedAtMs, pending.durationSeconds, pending.channels)
        } catch (_: Throwable) {
            null
        }
    }

    fun deletePending(pending: PendingRecording): Boolean =
        !pending.file.exists() || pending.file.delete()

    fun savedFor(context: Context, phoneNumber: String): List<SavedRecording> {
        val dir = recordingDir(context, phoneNumber)
        return dir.listFiles { file -> file.isFile && file.extension.equals("wav", true) }
            ?.mapNotNull(::readSaved)
            ?.sortedByDescending { it.timestampMs }
            .orEmpty()
    }

    fun deleteSaved(context: Context, phoneNumber: String, fileName: String): Boolean {
        val safeName = File(fileName).name
        val file = File(recordingDir(context, phoneNumber), safeName)
        return file.exists() && file.delete()
    }

    private fun readSaved(file: File): SavedRecording? = try {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < WAV_HEADER_BYTES) return null
            val header = ByteArray(WAV_HEADER_BYTES)
            raf.readFully(header)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val channels = buffer.getShort(22).toInt().coerceAtLeast(1)
            val sampleRate = buffer.getInt(24).coerceAtLeast(1)
            val dataBytes = buffer.getInt(40).toLong().coerceAtLeast(0L)
            val bytesPerSecond = sampleRate.toLong() * channels * 2L
            val timestamp = file.nameWithoutExtension.removePrefix("call_").toLongOrNull()
                ?: file.lastModified()
            SavedRecording(
                file = file,
                timestampMs = timestamp,
                durationSeconds = if (bytesPerSecond > 0) dataBytes / bytesPerSecond else 0L,
                channels = channels,
            )
        }
    } catch (_: Throwable) {
        null
    }

    private fun recordingDir(context: Context, phoneNumber: String): File =
        File(File(context.filesDir, "call_recordings"), storageKey(phoneNumber))

    internal fun storageKey(phoneNumber: String): String {
        val normalized = PhoneNumberKey.normalize(phoneNumber).orEmpty()
        val safe = normalized.replace(Regex("[^+0-9A-Za-z_-]"), "_").take(64)
        return safe.ifBlank { "unknown" }
    }

    internal fun wavHeader(sampleRate: Int, channels: Int, dataBytes: Long): ByteArray {
        val dataSize = dataBytes.coerceIn(0L, 0xffffffffL).toInt()
        val byteRate = sampleRate * channels * 2
        val blockAlign = (channels * 2).toShort()
        return ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()
    }

    private const val WAV_HEADER_BYTES = 44
}

object CallRecordingState {
    @Volatile private var pending: CallRecordingStore.PendingRecording? = null

    @Synchronized
    fun publish(recording: CallRecordingStore.PendingRecording) {
        pending?.let(CallRecordingStore::deletePending)
        pending = recording
    }

    fun peek(): CallRecordingStore.PendingRecording? = pending

    @Synchronized
    fun clear(expected: CallRecordingStore.PendingRecording? = null) {
        if (expected == null || pending === expected) pending = null
    }
}

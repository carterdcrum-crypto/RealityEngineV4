package com.realityengine.v4

import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads scrcpy 4.0 audio configured with raw_stream=true and audio_codec=raw.
 * With raw_stream enabled, scrcpy suppresses device/stream/frame metadata, so
 * every byte received here belongs to signed PCM16-LE audio.
 */
class ScrcpyRawPcmReader(
    private val input: InputStream,
    private val chunkBytes: Int = DEFAULT_CHUNK_BYTES
) {
    private val running = AtomicBoolean(false)

    fun run(onPcm: (ByteArray, Int) -> Unit, onStopped: (String?) -> Unit = {}) {
        if (!running.compareAndSet(false, true)) {
            onStopped("scrcpy PCM reader already running")
            return
        }

        var failure: String? = null
        val buffer = ByteArray(chunkBytes.coerceAtLeast(PCM_FRAME_BYTES))
        var carry: Byte? = null

        try {
            while (running.get()) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue

                var offset = 0
                if (carry != null) {
                    val merged = byteArrayOf(carry!!, buffer[0])
                    onPcm(merged, merged.size)
                    carry = null
                    offset = 1
                }

                val remaining = read - offset
                val aligned = remaining - (remaining % PCM_FRAME_BYTES)
                if (aligned > 0) {
                    val pcm = buffer.copyOfRange(offset, offset + aligned)
                    onPcm(pcm, pcm.size)
                }
                if (remaining > aligned) carry = buffer[offset + aligned]
            }
        } catch (t: Throwable) {
            if (running.get()) failure = "scrcpy PCM read failed: ${t.javaClass.simpleName}"
        } finally {
            running.set(false)
            runCatching { input.close() }
            onStopped(failure)
        }
    }

    fun stop() {
        running.set(false)
        runCatching { input.close() }
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val BITS_PER_SAMPLE = 16
        const val PCM_FRAME_BYTES = CHANNELS * (BITS_PER_SAMPLE / 8)
        private const val DEFAULT_CHUNK_BYTES = 9_600 // ~50 ms stereo PCM16 @ 48 kHz
    }
}

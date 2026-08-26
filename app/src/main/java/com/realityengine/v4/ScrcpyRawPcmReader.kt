package com.realityengine.v4

import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads scrcpy 4.0 `audio_codec=raw` + `raw_stream=true` audio.
 * scrcpy emits PCM16-LE, 48 kHz, stereo. This reader keeps stereo-frame alignment,
 * downmixes L/R, and performs an exact 3:1 box-filter downsample to 16 kHz mono.
 */
class ScrcpyRawPcmReader(
    private val input: InputStream,
    private val chunkBytes: Int = DEFAULT_CHUNK_BYTES
) {
    private val running = AtomicBoolean(false)
    private var carry = ByteArray(0)
    private var downsampleCount = 0
    private var downsampleSum = 0L

    fun run(onPcm: (ByteArray, Int) -> Unit, onStopped: (String?) -> Unit = {}) {
        if (!running.compareAndSet(false, true)) {
            onStopped("scrcpy PCM reader already running")
            return
        }

        var failure: String? = null
        val readBuffer = ByteArray(chunkBytes.coerceAtLeast(PCM_FRAME_BYTES))
        try {
            while (running.get()) {
                val read = input.read(readBuffer)
                if (read < 0) break
                if (read == 0) continue

                val merged = ByteArray(carry.size + read)
                carry.copyInto(merged)
                readBuffer.copyInto(merged, carry.size, 0, read)
                val alignedBytes = merged.size - (merged.size % PCM_FRAME_BYTES)
                if (alignedBytes > 0) {
                    val converted = convertFrames(merged, alignedBytes)
                    if (converted.isNotEmpty()) onPcm(converted, converted.size)
                }
                carry = if (alignedBytes < merged.size) merged.copyOfRange(alignedBytes, merged.size) else ByteArray(0)
            }
        } catch (t: Throwable) {
            if (running.get()) failure = "scrcpy PCM read failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            running.set(false)
            runCatching { input.close() }
            carry = ByteArray(0)
            onStopped(failure)
        }
    }

    private fun convertFrames(bytes: ByteArray, length: Int): ByteArray {
        val frameCount = length / PCM_FRAME_BYTES
        val maxOutputSamples = (frameCount + downsampleCount) / DOWNSAMPLE_RATIO
        if (maxOutputSamples <= 0) {
            accumulateFrames(bytes, length, null)
            return ByteArray(0)
        }
        val output = ByteArray(maxOutputSamples * 2)
        var outOffset = 0
        accumulateFrames(bytes, length) { sample ->
            if (outOffset + 1 < output.size) {
                output[outOffset++] = (sample and 0xff).toByte()
                output[outOffset++] = ((sample ushr 8) and 0xff).toByte()
            }
        }
        return if (outOffset == output.size) output else output.copyOf(outOffset)
    }

    private inline fun accumulateFrames(bytes: ByteArray, length: Int, emit: ((Int) -> Unit)?) {
        var offset = 0
        while (offset + 3 < length) {
            val left = littleEndianShort(bytes[offset], bytes[offset + 1])
            val right = littleEndianShort(bytes[offset + 2], bytes[offset + 3])
            val mono = (left + right) / 2
            downsampleSum += mono.toLong()
            downsampleCount++
            if (downsampleCount == DOWNSAMPLE_RATIO) {
                val sample = (downsampleSum / DOWNSAMPLE_RATIO).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                emit?.invoke(sample)
                downsampleCount = 0
                downsampleSum = 0L
            }
            offset += PCM_FRAME_BYTES
        }
    }

    private fun littleEndianShort(lo: Byte, hi: Byte): Int =
        (((hi.toInt() and 0xff) shl 8) or (lo.toInt() and 0xff)).toShort().toInt()

    fun stop() {
        running.set(false)
        runCatching { input.close() }
    }

    companion object {
        const val INPUT_SAMPLE_RATE = 48_000
        const val INPUT_CHANNELS = 2
        const val OUTPUT_SAMPLE_RATE = 16_000
        const val OUTPUT_CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val PCM_FRAME_BYTES = INPUT_CHANNELS * (BITS_PER_SAMPLE / 8)
        private const val DOWNSAMPLE_RATIO = INPUT_SAMPLE_RATE / OUTPUT_SAMPLE_RATE
        private const val DEFAULT_CHUNK_BYTES = 9_600
    }
}

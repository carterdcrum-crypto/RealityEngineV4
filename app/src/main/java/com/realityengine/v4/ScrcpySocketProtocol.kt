package com.realityengine.v4

/**
 * scrcpy 4.0 audio-only socket protocol constants.
 *
 * With video=false/control=false the audio socket is the first and only media
 * socket. Reality Engine requests raw_stream=true, so codec/device/frame
 * metadata are disabled and bytes arriving on the socket are PCM16LE directly.
 */
object ScrcpySocketProtocol {
    fun socketName(scid: Int): String = "scrcpy_${scid.toUInt().toString(16)}"

    const val PCM_BITS_PER_SAMPLE = 16
    const val PCM_CHANNELS = 2
    const val PCM_SAMPLE_RATE = 48_000
    const val PCM_BYTES_PER_SAMPLE = PCM_BITS_PER_SAMPLE / 8
    const val PCM_FRAME_BYTES = PCM_CHANNELS * PCM_BYTES_PER_SAMPLE

    fun alignedByteCount(byteCount: Int): Int =
        byteCount.coerceAtLeast(0) - (byteCount.coerceAtLeast(0) % PCM_FRAME_BYTES)
}

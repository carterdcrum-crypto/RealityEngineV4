package com.realityengine.v4

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Token-free acoustic change detector over PCM16 audio.
 * Measures deviations in observable signal properties; it is not a lie detector.
 */
class AcousticSignalAnalyzer {
    data class Result(val score: Int, val level: Float, val zeroCrossingRate: Float)

    private var baselineLevel = 0f
    private var baselineZcr = 0f
    private var samplesSeen = 0

    @Synchronized
    fun analyze(pcm16: ByteArray, length: Int = pcm16.size): Result {
        val usable = length.coerceAtMost(pcm16.size) and -2
        if (usable < 4) return Result(0, 0f, 0f)
        var sumSquares = 0.0
        var crossings = 0
        var count = 0
        var previous = 0
        var i = 0
        while (i < usable) {
            val sample = ((pcm16[i].toInt() and 0xff) or (pcm16[i + 1].toInt() shl 8)).toShort().toInt()
            val normalized = sample / 32768f
            sumSquares += normalized * normalized
            if (count > 0 && (sample >= 0) != (previous >= 0)) crossings++
            previous = sample; count++; i += 2
        }
        val level = sqrt(sumSquares / count).toFloat()
        val zcr = crossings.toFloat() / count.coerceAtLeast(1)
        if (samplesSeen < 24) {
            val n = samplesSeen.toFloat()
            baselineLevel = (baselineLevel * n + level) / (n + 1f)
            baselineZcr = (baselineZcr * n + zcr) / (n + 1f)
            samplesSeen++
            return Result(0, level, zcr)
        }
        val levelDelta = abs(level - baselineLevel) / baselineLevel.coerceAtLeast(.01f)
        val zcrDelta = abs(zcr - baselineZcr) / baselineZcr.coerceAtLeast(.01f)
        val score = ((levelDelta * 45f) + (zcrDelta * 35f)).toInt().coerceIn(0, 100)
        baselineLevel = baselineLevel * .985f + level * .015f
        baselineZcr = baselineZcr * .985f + zcr * .015f
        return Result(score, level, zcr)
    }

    @Synchronized fun reset() { baselineLevel = 0f; baselineZcr = 0f; samplesSeen = 0 }
}

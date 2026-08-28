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
    private var baselineFlux = 0f
    private var baselineCrest = 1f
    private var voicedFramesSeen = 0
    private var previousLevel = 0f
    private var smoothedScore = 0f
    private var lastPublishAtMs = 0L

    @Synchronized
    fun analyze(pcm16: ByteArray, length: Int = pcm16.size): Result {
        val usable = length.coerceAtMost(pcm16.size) and -2
        if (usable < 4) return Result(0, 0f, 0f)

        var sumSquares = 0.0
        var sumDelta = 0.0
        var peak = 0f
        var crossings = 0
        var count = 0
        var previous = 0
        var i = 0
        while (i < usable) {
            val sample = ((pcm16[i].toInt() and 0xff) or (pcm16[i + 1].toInt() shl 8)).toShort().toInt()
            val normalized = sample / 32768f
            sumSquares += normalized * normalized
            peak = maxOf(peak, abs(normalized))
            if (count > 0) {
                if ((sample >= 0) != (previous >= 0)) crossings++
                sumDelta += abs(sample - previous) / 65536.0
            }
            previous = sample
            count++
            i += 2
        }

        val level = sqrt(sumSquares / count).toFloat()
        val zcr = crossings.toFloat() / count.coerceAtLeast(1)
        val flux = (sumDelta / (count - 1).coerceAtLeast(1)).toFloat()
        val crest = (peak / level.coerceAtLeast(.001f)).coerceIn(1f, 8f)

        // Do not teach the baseline with silence. Decay toward zero instead of producing a spike
        // every time somebody stops talking.
        if (level < SILENCE_FLOOR) {
            smoothedScore *= .72f
            previousLevel = level
            publishRealtime(smoothedScore.toInt())
            return Result(smoothedScore.toInt().coerceIn(0, 100), level, zcr)
        }

        if (voicedFramesSeen < BASELINE_FRAMES) {
            val n = voicedFramesSeen.toFloat()
            baselineLevel = (baselineLevel * n + level) / (n + 1f)
            baselineZcr = (baselineZcr * n + zcr) / (n + 1f)
            baselineFlux = (baselineFlux * n + flux) / (n + 1f)
            baselineCrest = (baselineCrest * n + crest) / (n + 1f)
            previousLevel = level
            voicedFramesSeen++
            publishRealtime(0)
            return Result(0, level, zcr)
        }

        val levelDelta = abs(level - baselineLevel) / baselineLevel.coerceAtLeast(.008f)
        val zcrDelta = abs(zcr - baselineZcr) / baselineZcr.coerceAtLeast(.008f)
        val fluxDelta = abs(flux - baselineFlux) / baselineFlux.coerceAtLeast(.004f)
        val crestDelta = abs(crest - baselineCrest) / baselineCrest.coerceAtLeast(1f)
        val frameJump = abs(level - previousLevel) / previousLevel.coerceAtLeast(.008f)

        val rawScore = (
            minOf(levelDelta, 1.7f) * 28f +
                minOf(zcrDelta, 1.5f) * 17f +
                minOf(fluxDelta, 1.6f) * 19f +
                minOf(crestDelta, 1.2f) * 12f +
                minOf(frameJump, 1.7f) * 24f
            ).coerceIn(0f, 100f)

        // Fast attack shows real voice changes; slower release keeps the bar readable instead of
        // jittering on every 20 ms PCM frame.
        val smoothing = if (rawScore >= smoothedScore) ATTACK else RELEASE
        smoothedScore += (rawScore - smoothedScore) * smoothing

        // Follow the speaker slowly enough to keep sudden changes visible, but fast enough to
        // adapt when their normal speaking level shifts during a long call.
        baselineLevel = baselineLevel * .97f + level * .03f
        baselineZcr = baselineZcr * .97f + zcr * .03f
        baselineFlux = baselineFlux * .97f + flux * .03f
        baselineCrest = baselineCrest * .97f + crest * .03f
        previousLevel = level

        val score = smoothedScore.toInt().coerceIn(0, 100)
        publishRealtime(score)
        return Result(score, level, zcr)
    }

    private fun publishRealtime(score: Int) {
        val now = System.currentTimeMillis()
        if (now - lastPublishAtMs < PUBLISH_INTERVAL_MS) return
        lastPublishAtMs = now
        LiveSignalState.publishRealtime(acoustic = score)
    }

    @Synchronized
    fun reset() {
        baselineLevel = 0f
        baselineZcr = 0f
        baselineFlux = 0f
        baselineCrest = 1f
        voicedFramesSeen = 0
        previousLevel = 0f
        smoothedScore = 0f
        lastPublishAtMs = 0L
    }

    companion object {
        private const val BASELINE_FRAMES = 8
        private const val SILENCE_FLOOR = .0025f
        private const val ATTACK = .58f
        private const val RELEASE = .24f
        private const val PUBLISH_INTERVAL_MS = 90L
    }
}

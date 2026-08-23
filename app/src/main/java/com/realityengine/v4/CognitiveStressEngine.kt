package com.realityengine.v4

import kotlin.math.abs

/** Behavioral load estimator. This is not a lie detector or truthfulness determination. */
class CognitiveStressEngine {
    data class Metrics(
        val responseLatencyMs: Long,
        val fillerCount: Int,
        val fillerDensity: Float,
        val wordsPerMinute: Float,
        val speechRateDeviation: Float,
        val cognitiveStressScore: Float
    )

    private val fillers = setOf("um", "uh", "like", "honestly", "basically")
    private var promptEndedAtMs = 0L
    private var speechStartedAtMs = 0L

    fun markPromptEnd(timestampMs: Long = System.currentTimeMillis()) {
        promptEndedAtMs = timestampMs
        speechStartedAtMs = 0L
    }

    fun markSpeechStart(timestampMs: Long = System.currentTimeMillis()) {
        if (speechStartedAtMs == 0L) speechStartedAtMs = timestampMs
    }

    fun analyze(transcript: String, speechDurationMs: Long): Metrics {
        val words = transcript.lowercase().split(Regex("[^a-z']+")).filter { it.isNotBlank() }
        val phraseFillers = Regex("\\byou\\s+know\\b").findAll(transcript.lowercase()).count()
        val fillerCount = words.count { it in fillers } + phraseFillers
        val density = if (words.isEmpty()) 0f else fillerCount.toFloat() / words.size
        val minutes = (speechDurationMs.coerceAtLeast(1L) / 60_000f)
        val wpm = if (words.isEmpty()) 0f else words.size / minutes
        val latency = if (promptEndedAtMs > 0L && speechStartedAtMs >= promptEndedAtMs) speechStartedAtMs - promptEndedAtMs else 0L

        val latencyStress = ((latency - 350L).coerceAtLeast(0L) / 2_000f).coerceIn(0f, 1f)
        val fillerStress = (density / 0.12f).coerceIn(0f, 1f)
        val rateDeviation = if (wpm <= 0f) 0f else (abs(wpm - BASELINE_WPM) / 100f).coerceIn(0f, 1f)
        val score = (latencyStress * 0.40f + fillerStress * 0.35f + rateDeviation * 0.25f).coerceIn(0f, 1f)

        return Metrics(latency, fillerCount, density, wpm, rateDeviation, score)
    }

    /** Modulates E1/E2 anomaly evidence without changing persisted schemas. */
    fun applyToEvidence(acoustic: Float, linguistic: Float, score: Float): Pair<Float, Float> {
        val s = score.coerceIn(0f, 1f)
        val multiplier = 1f + (s * 0.25f)
        return (acoustic * multiplier).coerceIn(0f, 1f) to (linguistic * multiplier).coerceIn(0f, 1f)
    }

    companion object { private const val BASELINE_WPM = 145f }
}

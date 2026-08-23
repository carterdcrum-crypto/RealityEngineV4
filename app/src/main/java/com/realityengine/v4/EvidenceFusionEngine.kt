package com.realityengine.v4

import kotlin.math.exp
import kotlin.math.ln

/**
 * Fuses acoustic, linguistic, and factual evidence in log-odds space.
 * Output is an evidence/anomaly score under this model, not a determination of truthfulness.
 */
class EvidenceFusionEngine(
    private val prior: Float = 0.20f,
    private val maxStreamLogLikelihood: Double = 2.2
) {
    data class Streams(
        val acoustic: Float,
        val linguistic: Float,
        val factual: Float
    )

    data class Result(
        val acoustic: Float,
        val linguistic: Float,
        val factual: Float,
        val combined: Float,
        val logOdds: Double,
        val elevatedStreams: Int
    )

    fun fuse(streams: Streams): Result {
        val a = streams.acoustic.coerceIn(0.001f, 0.999f)
        val l = streams.linguistic.coerceIn(0.001f, 0.999f)
        val f = streams.factual.coerceIn(0.001f, 0.999f)

        val safePrior = prior.coerceIn(0.01f, 0.99f).toDouble()
        var logOdds = ln(safePrior / (1.0 - safePrior))
        logOdds += streamEvidence(a)
        logOdds += streamEvidence(l)
        logOdds += streamEvidence(f)

        val combined = sigmoid(logOdds).toFloat().coerceIn(0f, 1f)
        val elevated = listOf(a, l, f).count { it >= 0.65f }
        return Result(a, l, f, combined, logOdds, elevated)
    }

    fun toProfileEvent(result: Result, context: String = "") = CallerProfileStore.EvidenceEvent(
        acoustic = result.acoustic,
        linguistic = result.linguistic,
        factual = result.factual,
        combined = result.combined,
        context = context
    )

    private fun streamEvidence(probability: Float): Double {
        val p = probability.toDouble().coerceIn(0.001, 0.999)
        return ln(p / (1.0 - p)).coerceIn(-maxStreamLogLikelihood, maxStreamLogLikelihood)
    }

    private fun sigmoid(value: Double): Double = when {
        value >= 0.0 -> 1.0 / (1.0 + exp(-value))
        else -> { val e = exp(value); e / (1.0 + e) }
    }
}

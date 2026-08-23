package com.realityengine.v4

import java.util.Locale

/**
 * Lightweight, token-free linguistic signal producer.
 * Scores observable language features only; the score is not a lie detector or diagnosis.
 */
object LinguisticSignalAnalyzer {
    data class Result(val score: Int, val markers: List<String>)

    private val distancing = setOf("that person", "that woman", "that man", "those people")
    private val uncertainty = setOf("maybe", "probably", "possibly", "i think", "i guess", "not sure", "i don't remember")
    private val qualifiers = setOf("honestly", "basically", "actually", "literally", "technically", "to be honest")

    fun analyze(text: String): Result {
        val normalized = text.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
        if (normalized.length < 4) return Result(0, emptyList())
        val markers = mutableListOf<String>()
        var score = 0

        val uncertaintyHits = uncertainty.count { normalized.contains(it) }
        if (uncertaintyHits > 0) { score += minOf(24, uncertaintyHits * 8); markers += "uncertainty language" }

        val qualifierHits = qualifiers.count { normalized.contains(it) }
        if (qualifierHits > 0) { score += minOf(18, qualifierHits * 6); markers += "qualifier density" }

        if (distancing.any { normalized.contains(it) }) { score += 14; markers += "distancing language" }

        val words = normalized.split(' ').filter { it.isNotBlank() }
        if (words.size >= 28) { score += 8; markers += "high response length" }
        if (normalized.count { it == ',' } >= 4) { score += 6; markers += "clause density" }
        if (normalized.contains("i mean") && normalized.contains("but")) { score += 6; markers += "self-correction" }

        return Result(score.coerceIn(0, 100), markers.distinct())
    }
}

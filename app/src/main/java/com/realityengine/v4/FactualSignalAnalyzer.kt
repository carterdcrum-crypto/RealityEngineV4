package com.realityengine.v4

import android.content.Context
import java.util.Locale

/**
 * Token-free consistency signal against facts previously saved for this caller.
 * Reports possible textual conflicts for review; it does not determine truth or deception.
 */
class FactualSignalAnalyzer(context: Context) {
    data class Result(val score: Int, val matchedFact: String = "", val reason: String = "")
    private val profiles = CallerProfileStore(context.applicationContext)

    fun analyze(phoneNumber: String, transcript: String): Result {
        if (phoneNumber.isBlank() || transcript.isBlank()) return Result(0)
        val current = normalize(transcript)
        val facts = profiles.load(phoneNumber).importantFacts
        var best = Result(0)
        for (fact in facts.takeLast(20)) {
            val saved = normalize(fact)
            val overlap = keywordOverlap(saved, current)
            if (overlap < 2) continue
            val savedNeg = hasNegation(saved)
            val currentNeg = hasNegation(current)
            val numberConflict = numericConflict(saved, current)
            val score = when {
                numberConflict -> 78
                savedNeg != currentNeg -> 68
                else -> 0
            }
            if (score > best.score) best = Result(score, fact.take(160), if (numberConflict) "number mismatch" else "possible polarity mismatch")
        }
        return best
    }

    private fun normalize(value: String) = value.lowercase(Locale.US).replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
    private fun hasNegation(value: String) = Regex("\\b(no|not|never|don't|didn't|isn't|wasn't|can't|cannot|won't)\\b").containsMatchIn(value)
    private fun keywords(value: String) = value.split(' ').filter { it.length >= 4 && it !in STOP }.toSet()
    private fun keywordOverlap(a: String, b: String) = keywords(a).intersect(keywords(b)).size
    private fun numericConflict(a: String, b: String): Boolean {
        val an = Regex("\\b\\d+(?:\\.\\d+)?\\b").findAll(a).map { it.value }.toSet()
        val bn = Regex("\\b\\d+(?:\\.\\d+)?\\b").findAll(b).map { it.value }.toSet()
        return an.isNotEmpty() && bn.isNotEmpty() && an.intersect(bn).isEmpty()
    }

    companion object {
        private val STOP = setOf("that", "this", "with", "from", "have", "been", "were", "they", "them", "their", "there", "what", "when", "where", "would", "could", "should", "about")
    }
}

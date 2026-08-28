package com.realityengine.v4

import java.util.Locale

/**
 * Lightweight, token-free linguistic signal producer.
 * Scores observable language-load features only; the score is not a lie detector or diagnosis.
 */
object LinguisticSignalAnalyzer {
    data class Result(val score: Int, val markers: List<String>)

    private val distancing = setOf("that person", "that woman", "that man", "those people")
    private val uncertainty = setOf(
        "maybe", "probably", "possibly", "i think", "i guess", "not sure", "i'm not sure",
        "i don't remember", "i can't remember", "i suppose", "perhaps",
    )
    private val qualifiers = setOf("honestly", "basically", "actually", "literally", "technically", "to be honest", "truthfully")
    private val fillers = setOf("um", "uh", "erm", "you know", "kind of", "sort of")
    private val corrections = setOf("i mean", "actually", "rather", "let me rephrase", "what i meant", "no, i")
    private val absolutes = setOf("always", "never", "definitely", "absolutely", "everyone", "nobody", "completely", "exactly")
    private val vagueTime = setOf("sometime", "some time", "a while", "later on", "around then", "at some point")

    fun analyze(text: String): Result {
        val normalized = text.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
        if (normalized.length < 4) return Result(0, emptyList())
        val words = normalized.split(' ').filter { it.isNotBlank() }
        val markers = mutableListOf<String>()
        var score = 0

        val uncertaintyHits = uncertainty.count { containsPhrase(normalized, it) }
        if (uncertaintyHits > 0) {
            score += minOf(27, uncertaintyHits * 9)
            markers += "uncertainty / hedge language"
        }

        val qualifierHits = qualifiers.count { containsPhrase(normalized, it) }
        if (qualifierHits > 0) {
            score += minOf(18, qualifierHits * 6)
            markers += "qualifier density"
        }

        val fillerHits = fillers.sumOf { phrase -> phraseCount(normalized, phrase) }
        if (fillerHits > 0) {
            score += minOf(24, fillerHits * 5)
            markers += "disfluency density"
        }

        val correctionHits = corrections.count { containsPhrase(normalized, it) }
        if (correctionHits > 0) {
            score += minOf(22, correctionHits * 8)
            markers += "self-correction / restart"
        }

        if (distancing.any { containsPhrase(normalized, it) }) {
            score += 12
            markers += "distancing language"
        }

        val negations = Regex("\\b(no|not|never|don't|didn't|doesn't|isn't|wasn't|weren't|can't|cannot|won't|wouldn't|couldn't)\\b")
            .findAll(normalized).count()
        if (negations >= 2) {
            score += minOf(15, negations * 3)
            markers += "negation density"
        }

        val absoluteHits = absolutes.count { containsPhrase(normalized, it) }
        if (absoluteHits > 0) {
            score += minOf(15, absoluteHits * 5)
            markers += "absolute wording"
        }

        val vagueHits = vagueTime.count { containsPhrase(normalized, it) }
        if (vagueHits > 0) {
            score += minOf(18, vagueHits * 6)
            markers += "temporal vagueness"
        }

        val clauseConnectors = Regex("\\b(but|although|however|because|except|unless|though|yet)\\b").findAll(normalized).count()
        if (clauseConnectors >= 2) {
            score += minOf(10, clauseConnectors * 2)
            markers += "clause density"
        }

        if (words.size >= 18) {
            score += minOf(10, 4 + (words.size - 18) / 5)
            markers += "high response length"
        }

        if (hasRepeatedPhrase(words)) {
            score += 8
            markers += "phrase repetition"
        }

        return Result(score.coerceIn(0, 100), markers.distinct())
    }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        Regex("(?<![a-z0-9'])${Regex.escape(phrase)}(?![a-z0-9'])").containsMatchIn(text)

    private fun phraseCount(text: String, phrase: String): Int =
        Regex("(?<![a-z0-9'])${Regex.escape(phrase)}(?![a-z0-9'])").findAll(text).count()

    private fun hasRepeatedPhrase(words: List<String>): Boolean {
        if (words.size < 8) return false
        val pairs = words.windowed(2).map { it.joinToString(" ") }
        return pairs.groupingBy { it }.eachCount().any { (pair, count) ->
            count >= 2 && pair.split(' ').none { it in REPEAT_STOP }
        }
    }

    private val REPEAT_STOP = setOf("the", "and", "that", "this", "with", "you", "your", "for", "was", "are", "but", "not")
}

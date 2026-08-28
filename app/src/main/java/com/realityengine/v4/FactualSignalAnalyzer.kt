package com.realityengine.v4

import android.content.Context
import java.util.Locale

/**
 * Token-free consistency signal against saved caller memory and earlier claims in the same call.
 * Reports possible textual conflicts for review; it does not determine truth or deception.
 */
class FactualSignalAnalyzer(context: Context) {
    data class Result(val score: Int, val matchedFact: String = "", val reason: String = "")

    private val profiles = CallerProfileStore(context.applicationContext)
    private val recentClaims = ArrayDeque<String>()
    private var sessionPhoneKey = ""
    private var lastClaimAtMs = 0L

    @Synchronized
    fun analyze(phoneNumber: String, transcript: String): Result {
        val cleanText = transcript.trim()
        if (cleanText.isBlank()) return Result(0)
        val phoneKey = PhoneNumberKey.normalize(phoneNumber).orEmpty()
        rotateSessionIfNeeded(phoneKey)

        val current = normalize(cleanText)
        if (current.length < 6) return Result(0)

        var best = Result(0)
        if (phoneKey.isNotBlank() && !phoneKey.equals("UNKNOWN CALLER", ignoreCase = true)) {
            val profile = profiles.load(phoneNumber)
            val savedReferences = buildList {
                addAll(profile.importantFacts.takeLast(20))
                addAll(profile.unresolvedTopics.takeLast(6))
                if (profile.lastCallSummary.isNotBlank()) add(profile.lastCallSummary)
            }
            for (reference in savedReferences) {
                val candidate = compare(reference, current, source = "saved memory")
                if (candidate.score > best.score) best = candidate
            }
        }

        // A caller can contradict something they said minutes earlier even when the profile has no
        // saved memory yet. Compare finalized caller turns inside this session before storing this one.
        for (claim in recentClaims.takeLast(18)) {
            val candidate = compare(claim, current, source = "earlier call claim")
            if (candidate.score > best.score) best = candidate
        }

        rememberClaim(cleanText)
        return best
    }

    private fun rotateSessionIfNeeded(phoneKey: String) {
        val now = System.currentTimeMillis()
        val changedCaller = phoneKey != sessionPhoneKey
        val staleSession = lastClaimAtMs > 0L && now - lastClaimAtMs > SESSION_GAP_RESET_MS
        if (changedCaller || staleSession) {
            recentClaims.clear()
            sessionPhoneKey = phoneKey
        }
        lastClaimAtMs = now
    }

    private fun rememberClaim(text: String) {
        val normalized = normalize(text)
        if (keywords(normalized).size < 2 && numbers(normalized).isEmpty()) return
        if (recentClaims.lastOrNull()?.let { normalize(it) == normalized } == true) return
        recentClaims.addLast(text.take(220))
        while (recentClaims.size > MAX_RECENT_CLAIMS) recentClaims.removeFirst()
    }

    private fun compare(reference: String, current: String, source: String): Result {
        val saved = normalize(reference)
        if (saved.isBlank()) return Result(0)
        val overlap = keywordOverlap(saved, current)
        if (overlap < 1) return Result(0)

        val numberConflict = numericConflict(saved, current)
        val polarityConflict = overlap >= 2 && hasNegation(saved) != hasNegation(current)
        val explicitRevision = overlap >= 2 && REVISION.containsMatchIn(current)
        val score = when {
            numberConflict && overlap >= 2 -> if (source == "saved memory") 82 else 76
            numberConflict -> if (source == "saved memory") 72 else 66
            polarityConflict -> if (source == "saved memory") 70 else 64
            explicitRevision -> if (source == "saved memory") 62 else 58
            else -> 0
        }
        if (score == 0) return Result(0)

        val reason = when {
            numberConflict -> "$source · number/date mismatch"
            polarityConflict -> "$source · possible polarity reversal"
            else -> "$source · explicit revision"
        }
        return Result(score, reference.take(160), reason)
    }

    private fun normalize(value: String) = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9' ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun hasNegation(value: String) =
        Regex("\\b(no|not|never|don't|didn't|doesn't|isn't|wasn't|weren't|can't|cannot|won't|wouldn't|couldn't)\\b")
            .containsMatchIn(value)

    private fun keywords(value: String) = value.split(' ')
        .filter { it.length >= 4 && it !in STOP }
        .toSet()

    private fun keywordOverlap(a: String, b: String) = keywords(a).intersect(keywords(b)).size

    private fun numbers(value: String): Set<String> =
        Regex("\\b\\d+(?:[.:/]\\d+)*\\b").findAll(value).map { it.value }.toSet()

    private fun numericConflict(a: String, b: String): Boolean {
        val an = numbers(a)
        val bn = numbers(b)
        return an.isNotEmpty() && bn.isNotEmpty() && an.intersect(bn).isEmpty()
    }

    companion object {
        private const val MAX_RECENT_CLAIMS = 20
        private const val SESSION_GAP_RESET_MS = 45 * 60 * 1000L
        private val REVISION = Regex("\\b(actually|instead|no longer|not anymore|changed|cancelled|canceled|correction|rather)\\b", RegexOption.IGNORE_CASE)
        private val STOP = setOf(
            "that", "this", "with", "from", "have", "been", "were", "they", "them", "their", "there",
            "what", "when", "where", "would", "could", "should", "about", "just", "really", "then", "than",
        )
    }
}

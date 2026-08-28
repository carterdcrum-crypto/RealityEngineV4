package com.realityengine.v4

import android.content.Context
import java.util.Locale

/** Local-only search across saved transcripts, caller memory, and bookmarks. */
class ConversationSearchIndex(context: Context) {
    data class Result(
        val phoneNumber: String,
        val displayName: String,
        val source: String,
        val timestampMs: Long,
        val snippet: String,
        val score: Int,
    )

    private val appContext = context.applicationContext
    private val profiles = CallerProfileStore(appContext)
    private val bookmarks = CallBookmarkStore(appContext)

    fun search(query: String, limit: Int = 30): List<Result> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()
        val results = mutableListOf<Result>()

        CallTranscriptStore.savedAll(appContext).forEach { saved ->
            val match = bestSnippet(saved.text, terms) ?: return@forEach
            results += Result(
                phoneNumber = saved.phoneNumber,
                displayName = displayName(saved.phoneNumber),
                source = "TRANSCRIPT",
                timestampMs = saved.timestampMs,
                snippet = match.first,
                score = match.second + 2,
            )
        }

        profiles.allProfiles().forEach { profile ->
            val chunks = buildList {
                profile.likes.forEach { add("Like: $it") }
                profile.dislikes.forEach { add("Dislike: $it") }
                profile.importantFacts.forEach { add("Fact: $it") }
                profile.topics.forEach { add("Topic: $it") }
                profile.unresolvedTopics.forEach { add("Follow-up: $it") }
                profile.conversationStarters.forEach { add("Starter: $it") }
                if (profile.preferredConversationStyle.isNotBlank()) add("Style: ${profile.preferredConversationStyle}")
                if (profile.lastCallSummary.isNotBlank()) add("Last call: ${profile.lastCallSummary}")
            }
            chunks.forEach { chunk ->
                val score = score(chunk, terms)
                if (score > 0) results += Result(
                    phoneNumber = profile.phoneNumber,
                    displayName = profile.displayName.ifBlank { displayName(profile.phoneNumber) },
                    source = "MEMORY",
                    timestampMs = profile.updatedAtMs,
                    snippet = chunk.take(260),
                    score = score + 3,
                )
            }

            bookmarks.list(profile.phoneNumber).forEach { bookmark ->
                val score = score(bookmark.text, terms)
                if (score > 0) results += Result(
                    phoneNumber = profile.phoneNumber,
                    displayName = profile.displayName.ifBlank { displayName(profile.phoneNumber) },
                    source = "BOOKMARK",
                    timestampMs = bookmark.timestampMs,
                    snippet = bookmark.text.take(260),
                    score = score + 5,
                )
            }
        }

        return results
            .sortedWith(compareByDescending<Result> { it.score }.thenByDescending { it.timestampMs })
            .distinctBy { "${it.phoneNumber}|${it.source}|${it.snippet.lowercase(Locale.US)}" }
            .take(limit.coerceIn(1, 100))
    }

    private fun bestSnippet(text: String, terms: List<String>): Pair<String, Int>? {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val best = lines.map { it to score(it, terms) }.maxByOrNull { it.second } ?: return null
        if (best.second <= 0) return null
        return best.first.take(280) to best.second
    }

    private fun score(text: String, terms: List<String>): Int {
        val lower = text.lowercase(Locale.US)
        var score = 0
        terms.forEach { term ->
            if (term in lower) {
                score += 3
                if (Regex("\\b${Regex.escape(term)}\\b").containsMatchIn(lower)) score += 2
            }
        }
        if (terms.all { it in lower }) score += 5
        return score
    }

    private fun terms(query: String): List<String> = query
        .lowercase(Locale.US)
        .split(Regex("[^a-z0-9']+"))
        .map(String::trim)
        .filter { it.length >= 2 }
        .distinct()
        .take(8)

    private fun displayName(phone: String): String =
        ContactMediaStore.findByNumber(appContext, phone)?.name?.takeIf { it.isNotBlank() } ?: phone
}

package com.realityengine.v4

import android.content.Context

/** Read-only presentation model for saved per-number Reality Engine memory. */
class CallerProfileView(context: Context) {
    data class Snapshot(
        val phoneNumber: String,
        val name: String,
        val preferredStyle: String,
        val coachPersonaId: String,
        val likes: List<String>,
        val dislikes: List<String>,
        val recentTopics: List<String>,
        val starters: List<String>,
        val importantFacts: List<String>,
        val lastCallSummary: String,
        val peakCombined: Int,
        val peakContext: String
    )

    private val profiles = CallerProfileStore(context.applicationContext)

    fun load(phoneNumber: String, fallbackName: String = ""): Snapshot {
        val p = profiles.load(phoneNumber)
        val peak = p.evidenceEvents.maxByOrNull { it.combined }
        return Snapshot(
            phoneNumber = p.phoneNumber,
            name = p.displayName.ifBlank { fallbackName },
            preferredStyle = p.preferredConversationStyle,
            coachPersonaId = p.coachPersonaId,
            likes = p.likes.takeLast(8),
            dislikes = p.dislikes.takeLast(8),
            recentTopics = p.topics.takeLast(8),
            starters = p.conversationStarters.takeLast(5),
            importantFacts = p.importantFacts.takeLast(10),
            lastCallSummary = p.lastCallSummary,
            peakCombined = ((peak?.combined ?: 0f).coerceIn(0f, 1f) * 100).toInt(),
            peakContext = peak?.context.orEmpty().take(180)
        )
    }
}

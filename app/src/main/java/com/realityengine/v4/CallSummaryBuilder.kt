package com.realityengine.v4

import android.content.Context

/** Builds a compact, token-free end-of-call summary from persistent caller memory and evidence. */
class CallSummaryBuilder(context: Context) {
    private val profiles = CallerProfileStore(context.applicationContext)

    fun finalize(phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        val profile = profiles.load(phoneNumber)
        val recentEvents = profile.evidenceEvents.takeLast(12)
        val peak = recentEvents.maxByOrNull { it.combined }
        val summary = buildList {
            profile.topics.lastOrNull()?.let { add("Latest topic: ${it.take(120)}") }
            if (profile.preferredConversationStyle.isNotBlank()) add("Preferred style: ${profile.preferredConversationStyle.take(100)}")
            peak?.takeIf { it.combined >= .55f }?.let {
                add("Highest signal: ${(it.combined * 100).toInt()}% combined (${(it.acoustic * 100).toInt()}% acoustic, ${(it.linguistic * 100).toInt()}% linguistic, ${(it.factual * 100).toInt()}% factual)${if (it.context.isBlank()) "" else " near: ${it.context.take(110)}"}")
            }
            profile.conversationStarters.lastOrNull()?.let { add("Useful next opener: ${it.take(130)}") }
        }.joinToString(" • ").take(700)
        if (summary.isNotBlank()) profiles.update(phoneNumber) { it.lastCallSummary = summary }
        return summary
    }
}

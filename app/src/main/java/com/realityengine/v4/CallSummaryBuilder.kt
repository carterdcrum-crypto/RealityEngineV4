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
        val firstTs = recentEvents.firstOrNull()?.timestampMs
        val timeline = if (firstTs == null) emptyList() else recentEvents
            .filter { it.combined >= .55f }
            .sortedByDescending { it.combined }
            .take(3)
            .sortedBy { it.timestampMs }
            .map {
                val elapsed = ((it.timestampMs - firstTs).coerceAtLeast(0L) / 1000L)
                val mm = elapsed / 60
                val ss = elapsed % 60
                "@%d:%02d %d%% [A%d L%d F%d]%s".format(
                    mm, ss, (it.combined * 100).toInt(), (it.acoustic * 100).toInt(),
                    (it.linguistic * 100).toInt(), (it.factual * 100).toInt(),
                    if (it.context.isBlank()) "" else " ${it.context.take(80)}"
                )
            }
        val summary = buildList {
            profile.topics.lastOrNull()?.let { add("Latest topic: ${it.take(120)}") }
            if (profile.preferredConversationStyle.isNotBlank()) add("Preferred style: ${profile.preferredConversationStyle.take(100)}")
            peak?.takeIf { it.combined >= .55f }?.let {
                add("Highest signal: ${(it.combined * 100).toInt()}% combined (${(it.acoustic * 100).toInt()}% acoustic, ${(it.linguistic * 100).toInt()}% linguistic, ${(it.factual * 100).toInt()}% factual)${if (it.context.isBlank()) "" else " near: ${it.context.take(110)}"}")
            }
            if (timeline.isNotEmpty()) add("Timeline: ${timeline.joinToString(" | ")}")
            profile.conversationStarters.lastOrNull()?.let { add("Useful next opener: ${it.take(130)}") }
        }.joinToString(" • ").take(1000)
        if (summary.isNotBlank()) profiles.update(phoneNumber) { it.lastCallSummary = summary }
        return summary
    }
}

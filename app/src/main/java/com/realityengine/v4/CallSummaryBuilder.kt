package com.realityengine.v4

import android.content.Context
import kotlin.math.roundToInt

/** Builds a compact, token-free end-of-call summary from persistent caller memory and evidence. */
class CallSummaryBuilder(context: Context) {
    private val profiles = CallerProfileStore(context.applicationContext)

    fun finalize(phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        val profile = profiles.load(phoneNumber)
        val summary = buildSummary(profile)
        if (summary.isNotBlank()) profiles.update(phoneNumber) { it.lastCallSummary = summary }
        return summary
    }

    companion object {
        private fun pct(v: Float) = (v.coerceIn(0f, 1f) * 100f).roundToInt()

        internal fun buildSummary(profile: CallerProfileStore.CallerProfile): String {
            val recentEvents = profile.evidenceEvents.takeLast(12)
            val peak = recentEvents.maxByOrNull { it.combined }
            val firstTs = recentEvents.minOfOrNull { it.timestampMs }
            val timeline = if (firstTs == null) emptyList() else recentEvents
                .filter { it.combined >= .55f }
                .sortedWith(compareByDescending<CallerProfileStore.EvidenceEvent> { it.combined }.thenByDescending { it.timestampMs })
                .take(3)
                .sortedBy { it.timestampMs }
                .map {
                    val elapsed = ((it.timestampMs - firstTs).coerceAtLeast(0L) / 1000L)
                    val mm = elapsed / 60
                    val ss = elapsed % 60
                    "@%d:%02d %d%% [A%d L%d F%d]%s".format(
                        mm, ss, pct(it.combined), pct(it.acoustic), pct(it.linguistic), pct(it.factual),
                        if (it.context.isBlank()) "" else " ${it.context.take(80)}"
                    )
                }
            return buildList {
                profile.topics.lastOrNull()?.let { add("Latest topic: ${it.take(120)}") }
                if (profile.preferredConversationStyle.isNotBlank()) add("Preferred style: ${profile.preferredConversationStyle.take(100)}")
                peak?.takeIf { it.combined >= .55f }?.let {
                    add("Highest signal: ${pct(it.combined)}% combined (${pct(it.acoustic)}% acoustic, ${pct(it.linguistic)}% linguistic, ${pct(it.factual)}% factual)${if (it.context.isBlank()) "" else " near: ${it.context.take(110)}"}")
                }
                if (timeline.isNotEmpty()) add("Timeline: ${timeline.joinToString(" | ")}")
                profile.conversationStarters.lastOrNull()?.let { add("Useful next opener: ${it.take(130)}") }
            }.joinToString(" • ").take(1000)
        }
    }
}

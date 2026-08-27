package com.realityengine.v4

import java.util.Locale

/**
 * Shared strategy vocabulary for the live response coach.
 *
 * The model chooses the five strategies that best fit the current caller turn
 * instead of forcing the same five modes into every situation.
 */
object ResponseStrategyCatalog {
    data class Strategy(
        val id: String,
        val label: String,
        val guidance: String,
    )

    val all = listOf(
        Strategy("BONDING", "Bonding", "Build rapport, warmth, or shared understanding."),
        Strategy("CLARIFY", "Clarify", "Ask for the specific detail needed to understand the caller."),
        Strategy("MIRROR", "Mirror", "Reflect the caller's meaning or wording so they feel accurately heard."),
        Strategy("PIVOT", "Pivot", "Move the conversation toward a more useful topic or direction."),
        Strategy("COGNITIVE_PROBE", "Cognitive Probe", "Ask one neutral detail question that explores reasoning or consistency without trying to trap the caller."),
        Strategy("VALIDATE", "Validate", "Acknowledge the caller's feeling, concern, or perspective without pretending an unverified claim is true."),
        Strategy("REFRAME", "Reframe", "Offer a constructive alternative way to interpret the situation."),
        Strategy("DEESCALATE", "De-escalate", "Lower tension with calmer language, slower pacing, or reduced emotional intensity."),
        Strategy("BOUNDARY", "Boundary", "Set a clear, respectful limit or condition when the conversation needs one."),
        Strategy("DIRECT", "Direct", "Give a concise answer, request, or position without unnecessary padding."),
        Strategy("SUMMARIZE", "Summarize", "Restate the key points briefly to confirm shared understanding."),
        Strategy("NEXT_STEP", "Next Step", "Move the conversation toward a concrete action, decision, or follow-up."),
    )

    val ids: Set<String> = all.mapTo(linkedSetOf()) { it.id }

    fun normalizeMode(raw: String): String {
        val normalized = raw
            .trim()
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
        return normalized.takeIf { it in ids } ?: "CLARIFY"
    }

    fun promptGuide(): String = all.joinToString("\n") { strategy ->
        "${strategy.id}: ${strategy.guidance}"
    }
}

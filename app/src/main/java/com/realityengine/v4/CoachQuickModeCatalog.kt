package com.realityengine.v4

/** One-shot delivery modes triggered from the active-call screen. */
object CoachQuickModeCatalog {
    data class Mode(
        val id: String,
        val label: String,
        val promptInstruction: String,
        val temperature: Double,
    )

    const val UNHINGED = "UNHINGED"
    const val FLIRT = "FLIRT"

    val all: List<Mode> = listOf(
        Mode(
            id = UNHINGED,
            label = "Unhinged",
            promptInstruction = "Go maximum chaos-comedy: absurd, unpredictable, meme-brained, shamelessly dramatic, weirdly specific, deadpan, surreal, and laugh-out-loud surprising. Take creative swings and make the response feel genuinely unhinged rather than merely quirky. You may roast the situation, exaggerate harmless details, use ridiculous metaphors, or suddenly pivot into theatrical nonsense when it fits. Keep the target of the joke away from protected traits or vulnerabilities, and do not threaten, humiliate, encourage dangerous or illegal behavior, or turn a joke into real-world harm. The reply still has to make sense as something the user could actually say in this conversation.",
            temperature = 0.92,
        ),
        Mode(
            id = FLIRT,
            label = "Flirt",
            promptInstruction = "Make the replies boldly romantic, teasing, charming, affectionate, confident, and chemistry-forward. Use playful compliments, coy banter, butterflies/date-night energy, affectionate callbacks, and light romantic tension when the conversation supports it. Keep it nonsexual, pressure-free, respectful, and never possessive or coercive. Make it feel clearly flirty rather than merely friendly.",
            temperature = 0.62,
        ),
    )

    fun normalize(raw: String?): String? {
        val clean = raw.orEmpty().trim().uppercase()
        return all.firstOrNull { it.id == clean }?.id
    }

    fun byId(raw: String?): Mode? = normalize(raw)?.let { id -> all.firstOrNull { it.id == id } }
}

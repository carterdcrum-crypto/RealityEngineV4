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
            promptInstruction = "Make the replies absurd, surprising, fast, and genuinely funny. Keep the humor non-cruel, non-threatening, non-dangerous, and never suggest illegal or reckless behavior. The joke should still fit the actual conversation.",
            temperature = 0.70,
        ),
        Mode(
            id = FLIRT,
            label = "Flirt",
            promptInstruction = "Make the replies lightly playful, charming, and complimentary while staying nonsexual, respectful, pressure-free, and appropriate for a casual conversation. Never make explicit, suggestive, possessive, or coercive remarks.",
            temperature = 0.45,
        ),
    )

    fun normalize(raw: String?): String? {
        val clean = raw.orEmpty().trim().uppercase()
        return all.firstOrNull { it.id == clean }?.id
    }

    fun byId(raw: String?): Mode? = normalize(raw)?.let { id -> all.firstOrNull { it.id == id } }
}

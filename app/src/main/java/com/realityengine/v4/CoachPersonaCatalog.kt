package com.realityengine.v4

/** Delivery personas shape wording and tone without changing the underlying response strategy. */
object CoachPersonaCatalog {
    data class Persona(
        val id: String,
        val label: String,
        val description: String,
        val promptInstruction: String,
    )

    val all = listOf(
        Persona(
            "ADAPTIVE",
            "Adaptive",
            "Use caller memory and the current moment to choose the most natural delivery.",
            "Adapt delivery to the supplied caller profile and current conversation. Prefer the caller's learned communication style when available, without inventing preferences.",
        ),
        Persona(
            "WARM",
            "Warm",
            "Friendly, supportive, relationship-focused.",
            "Use warm, human, supportive wording. Prioritize rapport and ease while staying concise and truthful.",
        ),
        Persona(
            "DIRECT",
            "Direct",
            "Short, clear, minimal fluff.",
            "Use crisp, straightforward wording with minimal padding. Be respectful, not harsh.",
        ),
        Persona(
            "ANALYTICAL",
            "Analytical",
            "Curious, precise, clarification-heavy.",
            "Use precise, thoughtful wording. Favor useful distinctions and neutral clarification when it helps.",
        ),
        Persona(
            "CALM",
            "Calm",
            "Steady, low-intensity, good during tension.",
            "Use steady, low-intensity language and measured pacing. Avoid escalating tension.",
        ),
        Persona(
            "PLAYFUL",
            "Playful",
            "Casual, light, humorous when appropriate.",
            "Use relaxed, lightly playful wording when the moment supports it. Never joke about serious distress or force humor.",
        ),
        Persona(
            "ASSERTIVE",
            "Assertive",
            "Clear positions and respectful boundaries.",
            "Use confident, concise language with clear positions and respectful boundaries. Do not pressure or dominate the caller.",
        ),
    )

    val ids: Set<String> = all.map { it.id }.toSet()

    fun normalize(raw: String?, fallback: String = "ADAPTIVE"): String {
        val normalized = raw.orEmpty().trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')
        return normalized.takeIf { it in ids } ?: fallback.takeIf { it in ids } ?: "ADAPTIVE"
    }

    fun byId(id: String?): Persona = all.first { it.id == normalize(id) }

    /** AUTO means the contact inherits the global setting. */
    fun resolve(globalId: String?, contactOverride: String?, learnedStyle: String?): Persona {
        val override = contactOverride.orEmpty().trim().uppercase()
        val base = if (override.isNotBlank() && override != "AUTO") normalize(override) else normalize(globalId)
        if (base != "ADAPTIVE") return byId(base)

        val style = learnedStyle.orEmpty().lowercase()
        val inferred = when {
            "direct" in style || "concise" in style -> "DIRECT"
            "detailed" in style || "explanatory" in style || "analytical" in style -> "ANALYTICAL"
            "humor" in style || "casual" in style || "playful" in style -> "PLAYFUL"
            "calm" in style || "matter-of-fact" in style || "steady" in style -> "CALM"
            else -> "ADAPTIVE"
        }
        return byId(inferred)
    }

    fun contactChoices(): List<Pair<String, String>> =
        listOf("AUTO" to "Auto · use global/adaptive") + all.filterNot { it.id == "ADAPTIVE" }.map { it.id to it.label }
}

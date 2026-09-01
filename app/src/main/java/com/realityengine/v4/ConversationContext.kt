package com.realityengine.v4

/**
 * Compact two-sided context for live response coaching.
 * Keeps the raw turn stream local while producing a bounded prompt snapshot.
 */
class ConversationContext(
    private val maxRecentTurns: Int = 6,
    private val targetInputTokens: Int = 650
) {
    enum class Speaker { USER, CALLER }

    data class Turn(
        val speaker: Speaker,
        val text: String,
        val timestampMs: Long = System.currentTimeMillis()
    )

    data class Snapshot(
        val summary: String,
        val recentTurns: List<Turn>,
        val facts: List<String>,
        val unresolved: List<String>,
        val estimatedTokens: Int,
        val coachDirective: String = ""
    ) {
        fun asPromptContext(): String = buildString {
            val latestCallerTurn = recentTurns.lastOrNull { it.speaker == Speaker.CALLER }
            append("RESPONSE TARGET: Respond primarily to the CALLER'S LATEST WORDS, intent, and context. ")
            append("USER speech is prior-response context only; never treat it as the caller's request.\n")
            if (latestCallerTurn != null) {
                append("CALLER LATEST: ").append(latestCallerTurn.text).append('\n')
            }
            if (coachDirective.isNotBlank()) append("COACH DELIVERY: ").append(coachDirective).append('\n')
            if (summary.isNotBlank()) append("STATE: ").append(summary).append('\n')
            if (facts.isNotEmpty()) append("FACTS: ").append(facts.joinToString(" | ")).append('\n')
            if (unresolved.isNotEmpty()) append("OPEN: ").append(unresolved.joinToString(" | ")).append('\n')
            append("PRIOR CONVERSATION CONTEXT:\n")
            recentTurns.forEach {
                append(if (it.speaker == Speaker.CALLER) "CALLER: " else "USER: ")
                append(it.text).append('\n')
            }
        }.trim()
    }

    private val turns = ArrayDeque<Turn>()
    private val facts = LinkedHashSet<String>()
    private val unresolved = LinkedHashSet<String>()
    private var runningSummary = ""
    private var coachDirective = ""

    @Synchronized
    fun addTurn(speaker: Speaker, text: String) {
        val clean = text.trim().replace(Regex("\\s+"), " ").take(360)
        if (clean.isBlank()) return
        turns.addLast(Turn(speaker, clean))
        compactIfNeeded()
    }

    @Synchronized
    fun rememberFact(value: String) {
        val clean = value.trim().replace(Regex("\\s+"), " ")
        if (clean.isNotBlank()) {
            facts.add(clean.take(150))
            while (facts.size > 6) facts.remove(facts.first())
        }
    }

    @Synchronized
    fun setCoachDirective(value: String) {
        coachDirective = value.trim().replace(Regex("\\s+"), " ").take(360)
    }

    @Synchronized
    fun markUnresolved(value: String) {
        val clean = value.trim().replace(Regex("\\s+"), " ")
        if (clean.isNotBlank()) {
            unresolved.add(clean.take(140))
            while (unresolved.size > 3) unresolved.remove(unresolved.first())
        }
    }

    @Synchronized
    fun resolve(value: String) {
        unresolved.remove(value)
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val selected = turns.toList().takeLast(maxRecentTurns).toMutableList()
        var snapshot = buildSnapshot(selected)
        while (snapshot.estimatedTokens > targetInputTokens && selected.size > 2) {
            selected.removeAt(0)
            snapshot = buildSnapshot(selected)
        }
        if (snapshot.estimatedTokens > targetInputTokens) {
            val leanFacts = snapshot.facts.takeLast(3)
            val leanOpen = snapshot.unresolved.takeLast(2)
            snapshot = buildSnapshot(selected, leanFacts, leanOpen, compactText(runningSummary, 360))
        }
        return snapshot
    }

    @Synchronized
    fun clear() {
        turns.clear(); facts.clear(); unresolved.clear(); runningSummary = ""; coachDirective = ""
    }

    private fun compactIfNeeded() {
        while (turns.size > maxRecentTurns * 2) {
            val old = turns.removeFirst()
            val fragment = (if (old.speaker == Speaker.CALLER) "C:" else "U:") + old.text
            runningSummary = compactText(listOf(runningSummary, fragment).filter { it.isNotBlank() }.joinToString(" "), 520)
        }
    }

    private fun buildSnapshot(
        selected: List<Turn>,
        factList: List<String> = facts.toList(),
        openList: List<String> = unresolved.toList(),
        summary: String = compactText(runningSummary, 520)
    ): Snapshot {
        val chars = summary.length + coachDirective.length + selected.sumOf { it.text.length + 10 } + factList.sumOf { it.length } + openList.sumOf { it.length }
        return Snapshot(summary, selected, factList, openList, estimateTokens(chars), coachDirective)
    }

    // Conservative local approximation; actual API usage is learned from provider usage metadata.
    private fun estimateTokens(chars: Int): Int = (chars / 3.5).toInt().coerceAtLeast(1)

    private fun compactText(value: String, maxChars: Int): String =
        if (value.length <= maxChars) value else "…" + value.takeLast(maxChars - 1)
}

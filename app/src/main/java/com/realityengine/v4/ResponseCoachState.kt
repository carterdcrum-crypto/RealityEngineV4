package com.realityengine.v4

/**
 * Observable bridge between transcription/AI engines and the active-call UI.
 * State is explicitly reset between calls so suggestions from one caller can
 * never bleed into the next call screen.
 */
object ResponseCoachState {
    data class Snapshot(
        val best: LiveResponseEngine.Suggestion? = null,
        val alternatives: List<LiveResponseEngine.Suggestion> = emptyList(),
        val chosen: LiveResponseEngine.ChosenResponse? = null,
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        val updatedAt: Long = 0L
    )

    private val listeners = LinkedHashSet<(Snapshot) -> Unit>()
    @Volatile private var snapshot = Snapshot()

    @Synchronized fun publish(result: LiveResponseEngine.Result) {
        snapshot = snapshot.copy(
            best = result.best,
            alternatives = result.alternatives,
            inputTokens = result.inputTokens,
            outputTokens = result.outputTokens,
            updatedAt = System.currentTimeMillis()
        )
        notifyListeners()
    }

    @Synchronized fun publishChosen(chosen: LiveResponseEngine.ChosenResponse) {
        snapshot = snapshot.copy(chosen = chosen, updatedAt = System.currentTimeMillis())
        notifyListeners()
    }

    @Synchronized fun clearSuggestions() {
        snapshot = snapshot.copy(best = null, alternatives = emptyList())
        notifyListeners()
    }

    @Synchronized fun clearCall() {
        snapshot = Snapshot()
        notifyListeners()
    }

    fun current(): Snapshot = snapshot

    @Synchronized fun addListener(listener: (Snapshot) -> Unit) {
        listeners += listener
        listener(snapshot)
    }

    @Synchronized fun removeListener(listener: (Snapshot) -> Unit) {
        listeners -= listener
    }

    @Synchronized internal fun resetForTest() {
        snapshot = Snapshot()
        listeners.clear()
    }

    private fun notifyListeners() {
        val current = snapshot
        listeners.toList().forEach { it(current) }
    }
}

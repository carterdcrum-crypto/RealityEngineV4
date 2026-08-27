package com.realityengine.v4

/** Thread-safe bridge from streaming transcription to the active-call UI. */
object LiveTranscriptState {
    data class Entry(
        val text: String,
        val isFinal: Boolean,
        val updatedAtMs: Long,
        val isCaller: Boolean? = null
    )

    data class State(
        val text: String = "",
        val isFinal: Boolean = false,
        val updatedAtMs: Long = 0L,
        val entries: List<Entry> = emptyList(),
        val isCaller: Boolean? = null
    )

    // Keep enough finalized turns for long calls so the end-of-call transcript can be saved intact.
    private const val MAX_FINAL_ENTRIES = 500
    @Volatile private var current = State()
    private val listeners = LinkedHashSet<(State) -> Unit>()

    fun snapshot(): State = current

    @Synchronized
    fun addListener(listener: (State) -> Unit) {
        listeners += listener
        listener(current)
    }

    @Synchronized
    fun removeListener(listener: (State) -> Unit) { listeners -= listener }

    fun publish(text: String, isFinal: Boolean, isCaller: Boolean? = null) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val now = System.currentTimeMillis()
        val previous = current
        val history = if (isFinal) {
            val last = previous.entries.lastOrNull()
            if (last?.text == clean && last.isCaller == isCaller) previous.entries
            else (previous.entries + Entry(clean, true, now, isCaller)).takeLast(MAX_FINAL_ENTRIES)
        } else previous.entries
        val next = State(clean, isFinal, now, history, isCaller)
        current = next
        val copy = synchronized(this) { listeners.toList() }
        copy.forEach { it(next) }
    }

    /** Finalized speech accumulated during the current call, oldest to newest. */
    fun transcript(): List<Entry> = current.entries

    fun clear() {
        current = State()
        val copy = synchronized(this) { listeners.toList() }
        copy.forEach { it(current) }
    }
}

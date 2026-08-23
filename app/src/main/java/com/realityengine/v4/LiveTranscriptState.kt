package com.realityengine.v4

/** Thread-safe bridge from streaming transcription to the active-call UI. */
object LiveTranscriptState {
    data class State(
        val text: String = "",
        val isFinal: Boolean = false,
        val updatedAtMs: Long = 0L
    )

    @Volatile private var current = State()
    private val listeners = LinkedHashSet<(State) -> Unit>()

    fun snapshot(): State = current

    @Synchronized
    fun addListener(listener: (State) -> Unit) {
        listeners += listener
        listener(current)
    }

    @Synchronized
    fun removeListener(listener: (State) -> Unit) {
        listeners -= listener
    }

    fun publish(text: String, isFinal: Boolean) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val next = State(clean, isFinal, System.currentTimeMillis())
        current = next
        val copy = synchronized(this) { listeners.toList() }
        copy.forEach { it(next) }
    }

    fun clear() {
        current = State()
        val copy = synchronized(this) { listeners.toList() }
        copy.forEach { it(current) }
    }
}

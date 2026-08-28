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
    private const val INTERIM_RECONCILE_WINDOW_MS = 8_000L
    private const val TRAILING_RESCUE_WINDOW_MS = 12_000L

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

        // Streaming STT can sometimes emit a richer interim hypothesis and then a shorter final
        // segment that drops an edge word. Reconcile only the immediately preceding interim from
        // the same speaker, keeping the final wording as the authority for the overlapping core.
        val resolved = if (
            isFinal &&
            !previous.isFinal &&
            previous.text.isNotBlank() &&
            previous.isCaller == isCaller &&
            now - previous.updatedAtMs <= INTERIM_RECONCILE_WINDOW_MS
        ) {
            reconcileUtterance(previous.text, clean)
        } else {
            clean
        }

        val history = if (isFinal) {
            val last = previous.entries.lastOrNull()
            if (last?.text.equals(resolved, ignoreCase = true) && last?.isCaller == isCaller) {
                previous.entries
            } else {
                (previous.entries + Entry(resolved, true, now, isCaller)).takeLast(MAX_FINAL_ENTRIES)
            }
        } else {
            previous.entries
        }

        val next = State(resolved, isFinal, now, history, isCaller)
        current = next
        val copy = synchronized(this) { listeners.toList() }
        copy.forEach { it(next) }
    }

    /**
     * Best complete transcript snapshot for persistence at call end.
     *
     * Finalized turns are authoritative, but if the call disconnects while the STT service still
     * has a meaningful interim tail, rescue that tail instead of silently dropping the caller's
     * last words.
     */
    fun transcript(): List<Entry> {
        val snapshot = current
        val history = snapshot.entries
        val trailing = snapshot.text.trim()
        if (snapshot.isFinal || trailing.isBlank()) return history

        val last = history.lastOrNull()
        if (last != null && last.isCaller == snapshot.isCaller) {
            if (last.text.equals(trailing, ignoreCase = true) || last.text.contains(trailing, ignoreCase = true)) {
                return history
            }
            if (snapshot.updatedAtMs - last.updatedAtMs <= TRAILING_RESCUE_WINDOW_MS) {
                val merged = reconcileUtterance(last.text, trailing)
                if (!merged.equals(last.text, ignoreCase = true) && !merged.equals(trailing, ignoreCase = true)) {
                    return (history.dropLast(1) + last.copy(text = merged, updatedAtMs = snapshot.updatedAtMs))
                        .takeLast(MAX_FINAL_ENTRIES)
                }
            }
        }

        return (history + Entry(trailing, true, snapshot.updatedAtMs, snapshot.isCaller))
            .takeLast(MAX_FINAL_ENTRIES)
    }

    fun clear() {
        current = State()
        val copy = synchronized(this) { listeners.toList() }
        copy.forEach { it(current) }
    }

    /**
     * Preserve missing leading/trailing words when interim/final hypotheses overlap.
     * Falls back to the final hypothesis when there is no trustworthy overlap.
     */
    internal fun reconcileUtterance(interimText: String, finalText: String): String {
        val interim = interimText.trim()
        val final = finalText.trim()
        if (interim.isBlank()) return final
        if (final.isBlank()) return interim
        if (interim.equals(final, ignoreCase = true)) return final
        if (interim.contains(final, ignoreCase = true)) return interim
        if (final.contains(interim, ignoreCase = true)) return final

        val interimWords = interim.split(Regex("\\s+")).filter(String::isNotBlank)
        val finalWords = final.split(Regex("\\s+")).filter(String::isNotBlank)
        if (interimWords.isEmpty() || finalWords.isEmpty()) return final

        fun comparable(word: String): String = word.lowercase().trim { !it.isLetterOrDigit() }
        fun same(a: String, b: String): Boolean = comparable(a) == comparable(b) && comparable(a).isNotBlank()

        val maxOverlap = minOf(interimWords.size, finalWords.size)
        for (count in maxOverlap downTo 2) {
            var matches = true
            for (i in 0 until count) {
                if (!same(interimWords[interimWords.size - count + i], finalWords[i])) {
                    matches = false
                    break
                }
            }
            if (matches) return (interimWords + finalWords.drop(count)).joinToString(" ")
        }

        for (count in maxOverlap downTo 2) {
            var matches = true
            for (i in 0 until count) {
                if (!same(finalWords[finalWords.size - count + i], interimWords[i])) {
                    matches = false
                    break
                }
            }
            if (matches) return (finalWords + interimWords.drop(count)).joinToString(" ")
        }

        return final
    }
}

package com.realityengine.v4

/** Thread-safe bridge from the evidence engine to the active-call UI. */
object LiveSignalState {
    data class State(
        val acoustic: Int = 0,
        val linguistic: Int = 0,
        val factual: Int = 0,
        val combined: Int = 0,
        val elevatedStreams: Int = 0,
        val updatedAtMs: Long = 0L
    )

    @Volatile
    private var current = State()

    fun publish(snapshot: LiveEvidenceEngine.Snapshot) {
        current = State(
            acoustic = snapshot.acoustic.coerceIn(0, 100),
            linguistic = snapshot.linguistic.coerceIn(0, 100),
            factual = snapshot.factual.coerceIn(0, 100),
            combined = snapshot.combined.coerceIn(0, 100),
            elevatedStreams = snapshot.elevatedStreams.coerceIn(0, 3),
            updatedAtMs = snapshot.timestampMs
        )
    }

    fun snapshot(): State = current

    fun clear() {
        current = State()
    }
}

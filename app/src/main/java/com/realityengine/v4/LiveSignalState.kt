package com.realityengine.v4

import android.content.Context

/** Thread-safe bridge from the evidence engine to the active-call UI. */
object LiveSignalState {
    data class State(
        val acoustic: Int = 0,
        val linguistic: Int = 0,
        val factual: Int = 0,
        val combined: Int = 0,
        val elevatedStreams: Int = 0,
        val updatedAtMs: Long = 0L,
        val context: String = "",
        val cognitiveStress: Int = 0,
    )

    @Volatile private var current = State()
    @Volatile private var haptics: SignalHaptics? = null

    fun initialize(context: Context) {
        if (haptics == null) synchronized(this) {
            if (haptics == null) haptics = SignalHaptics(context.applicationContext)
        }
    }

    fun publish(snapshot: LiveEvidenceEngine.Snapshot) {
        val state = State(
            acoustic = snapshot.acoustic.coerceIn(0, 100),
            linguistic = snapshot.linguistic.coerceIn(0, 100),
            factual = snapshot.factual.coerceIn(0, 100),
            combined = snapshot.combined.coerceIn(0, 100),
            elevatedStreams = snapshot.elevatedStreams.coerceIn(0, 3),
            updatedAtMs = snapshot.timestampMs,
            context = snapshot.context,
            cognitiveStress = snapshot.cognitiveStress,
        )
        current = state
        haptics?.update(state.acoustic, state.linguistic, state.factual)
    }

    fun snapshot(): State = current

    fun clear() {
        current = State()
        haptics?.update(0, 0, 0)
    }
}

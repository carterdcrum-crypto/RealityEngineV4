package com.realityengine.v4

import android.content.Context

/** Thread-safe bridge from evidence producers to the active-call UI. */
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

    private val fusion = EvidenceFusionEngine()
    @Volatile private var current = State()
    @Volatile private var haptics: SignalHaptics? = null
    private var lastRealtimeHapticAtMs = 0L

    fun initialize(context: Context) {
        if (haptics == null) synchronized(this) {
            if (haptics == null) haptics = SignalHaptics(context.applicationContext)
        }
    }

    @Synchronized
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
        lastRealtimeHapticAtMs = state.updatedAtMs
    }

    /**
     * Fast presentation path for evidence that changes between completed transcript turns.
     * This does not persist profile evidence; LiveEvidenceEngine remains the authoritative
     * final-turn/persistence path.
     */
    @Synchronized
    fun publishRealtime(
        acoustic: Int? = null,
        linguistic: Int? = null,
        factual: Int? = null,
        context: String? = null,
    ) {
        val previous = current
        val a = acoustic?.coerceIn(0, 100) ?: previous.acoustic
        val l = linguistic?.coerceIn(0, 100) ?: previous.linguistic
        val f = factual?.coerceIn(0, 100) ?: previous.factual
        val fused = fusion.fuse(
            EvidenceFusionEngine.Streams(
                acoustic = a / 100f,
                linguistic = l / 100f,
                factual = f / 100f,
            )
        )
        val now = System.currentTimeMillis()
        val cleanContext = context
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.take(220)
            ?.takeIf { it.isNotBlank() }
        val state = previous.copy(
            acoustic = a,
            linguistic = l,
            factual = f,
            combined = (fused.combined * 100f).toInt().coerceIn(0, 100),
            elevatedStreams = fused.elevatedStreams.coerceIn(0, 3),
            updatedAtMs = now,
            context = cleanContext ?: previous.context,
        )
        current = state
        if (now - lastRealtimeHapticAtMs >= REALTIME_HAPTIC_INTERVAL_MS) {
            lastRealtimeHapticAtMs = now
            haptics?.update(state.acoustic, state.linguistic, state.factual)
        }
    }

    fun snapshot(): State = current

    @Synchronized
    fun clear() {
        current = State()
        lastRealtimeHapticAtMs = 0L
        haptics?.update(0, 0, 0)
    }

    private const val REALTIME_HAPTIC_INTERVAL_MS = 250L
}

package com.realityengine.v4

/** Pure formatter for the active-call transcript panel. */
object TranscriptPresenter {
    fun render(
        snapshot: LiveTranscriptState.State,
        route: AudioRouteState.Snapshot
    ): String {
        val lines = snapshot.entries.takeLast(8).map { "● ${it.text}" }.toMutableList()
        if (!snapshot.isFinal && snapshot.text.isNotBlank()) lines += "○ ${snapshot.text}"

        if (lines.isNotEmpty()) return lines.joinToString("\n")
        return if (route.updatedAtMs > 0L) {
            "○ CALL AUDIO // ${route.detail}"
        } else {
            "AWAITING AUDIO STREAM…"
        }
    }
}

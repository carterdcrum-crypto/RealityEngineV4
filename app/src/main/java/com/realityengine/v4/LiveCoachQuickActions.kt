package com.realityengine.v4

/** Lightweight bridge from CallActivity to the currently active conversation session. */
object LiveCoachQuickActions {
    @Volatile private var handler: ((String) -> Boolean)? = null

    fun attach(block: (String) -> Boolean) {
        handler = block
    }

    fun request(modeId: String): Boolean = handler?.invoke(modeId) == true
}

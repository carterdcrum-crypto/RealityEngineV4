package com.realityengine.v4

import android.telecom.Call
import java.util.concurrent.CopyOnWriteArrayList

object CallSessionRegistry {
    private val calls = CopyOnWriteArrayList<Call>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun add(call: Call) {
        if (!calls.contains(call)) calls.add(call)
        notifyChanged()
    }

    fun remove(call: Call) {
        calls.remove(call)
        notifyChanged()
    }

    fun all(): List<Call> = calls.toList()

    fun primary(): Call? = calls.firstOrNull { it.state != Call.STATE_DISCONNECTED } ?: calls.firstOrNull()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }
}

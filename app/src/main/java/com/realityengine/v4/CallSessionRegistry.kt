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

    fun removeIfDisconnected(call: Call) {
        if (call.state == Call.STATE_DISCONNECTED) calls.remove(call)
        notifyChanged()
    }

    fun all(): List<Call> = calls.toList()

    fun primary(): Call? {
        val live = calls.filter { it.state != Call.STATE_DISCONNECTED }
        return live.firstOrNull { it.state == Call.STATE_RINGING }
            ?: live.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: live.firstOrNull { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            ?: live.firstOrNull { it.state == Call.STATE_HOLDING }
            ?: live.firstOrNull()
    }

    /** Stable number key for a specific call, including a call that is being removed. */
    fun numberFor(call: Call): String? = PhoneNumberKey.normalize(
        call.details?.handle?.schemeSpecificPart
    )

    /** Stable number key shared by caller profiles, response coaching, and evidence history. */
    fun primaryNumber(): String? = primary()?.let(::numberFor)

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    private fun notifyChanged() { listeners.forEach { it.invoke() } }
}

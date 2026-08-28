package com.realityengine.v4

import android.telecom.Call
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object CallSessionRegistry {
    private val calls = CopyOnWriteArrayList<Call>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val knownNumbers = ConcurrentHashMap<Call, String>()

    fun add(call: Call) {
        rememberNumber(call)
        if (!calls.contains(call)) calls.add(call)
        notifyChanged()
    }

    /**
     * Telecom can expose an incoming handle after onCallAdded. Keep the first usable number
     * attached to the Call object so caller memory/transcripts still have a stable key later.
     */
    fun refreshDetails(call: Call) {
        val before = knownNumbers[call]
        rememberNumber(call)
        if (knownNumbers[call] != before) notifyChanged()
    }

    fun remove(call: Call) {
        calls.remove(call)
        knownNumbers.remove(call)
        notifyChanged()
    }

    fun removeIfDisconnected(call: Call) {
        if (call.state == Call.STATE_DISCONNECTED) calls.remove(call)
        // Do not clear knownNumbers here. onCallRemoved still needs the stable number for
        // transcript, recording and memory finalization.
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
    fun numberFor(call: Call): String? {
        rememberNumber(call)
        return knownNumbers[call]
    }

    /** Stable number key shared by caller profiles, response coaching, and evidence history. */
    fun primaryNumber(): String? = primary()?.let(::numberFor)

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    private fun rememberNumber(call: Call) {
        val current = PhoneNumberKey.normalize(call.details?.handle?.schemeSpecificPart) ?: return
        if (current.isNotBlank()) knownNumbers[call] = current
    }

    private fun notifyChanged() { listeners.forEach { it.invoke() } }
}

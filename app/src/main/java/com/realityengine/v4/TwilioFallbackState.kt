package com.realityengine.v4

/**
 * Process-local state for calls intentionally routed through Twilio.
 * Carrier calls remain false; only the Twilio calling path should mark a session active.
 */
object TwilioFallbackState {
    @Volatile private var active = false
    @Volatile private var remoteNumber = ""

    fun begin(number: String) {
        remoteNumber = number.trim()
        active = true
    }

    fun end() {
        active = false
        remoteNumber = ""
    }

    fun isActive(): Boolean = active
    fun number(): String = remoteNumber
}

package com.realityengine.v4

import android.content.Context

/** Loads compact persistent caller memory for live AI coaching. */
class CallerProfileSession(context: Context) {
    private val profiles = CallerProfileStore(context.applicationContext)
    private var activeNumber = ""

    @Synchronized
    fun bind(phoneNumber: String, conversation: ConversationContext): CallerProfileStore.CallerProfile? {
        val clean = phoneNumber.trim()
        if (clean.isBlank() || clean == "UNKNOWN CALLER") return null
        if (clean == activeNumber) return profiles.load(clean)
        activeNumber = clean
        profiles.injectInto(conversation, clean)
        return profiles.load(clean)
    }

    /** Re-inject the latest saved profile before an AI analysis turn. */
    @Synchronized
    fun refresh(phoneNumber: String, conversation: ConversationContext): CallerProfileStore.CallerProfile? {
        val clean = phoneNumber.trim()
        if (clean.isBlank() || clean == "UNKNOWN CALLER") return null
        activeNumber = clean
        profiles.injectInto(conversation, clean)
        return profiles.load(clean)
    }

    @Synchronized fun clear() { activeNumber = "" }
}

package com.realityengine.v4

import android.content.Context

/** Loads compact persistent caller memory and resolved coach delivery for live AI coaching. */
class CallerProfileSession(context: Context) {
    private val appContext = context.applicationContext
    private val profiles = CallerProfileStore(appContext)
    private val settings = SettingsStore(appContext)
    private var activeNumber = ""

    @Synchronized
    fun bind(phoneNumber: String, conversation: ConversationContext): CallerProfileStore.CallerProfile? {
        val clean = phoneNumber.trim()
        if (clean.isBlank() || clean == "UNKNOWN CALLER") {
            applyPersona(null, conversation)
            return null
        }
        if (clean == activeNumber) return profiles.load(clean).also { applyPersona(it, conversation) }
        activeNumber = clean
        profiles.injectInto(conversation, clean)
        return profiles.load(clean).also { applyPersona(it, conversation) }
    }

    /** Re-inject the latest saved profile before an AI analysis turn. */
    @Synchronized
    fun refresh(phoneNumber: String, conversation: ConversationContext): CallerProfileStore.CallerProfile? {
        val clean = phoneNumber.trim()
        if (clean.isBlank() || clean == "UNKNOWN CALLER") {
            applyPersona(null, conversation)
            return null
        }
        activeNumber = clean
        profiles.injectInto(conversation, clean)
        return profiles.load(clean).also { applyPersona(it, conversation) }
    }

    private fun applyPersona(profile: CallerProfileStore.CallerProfile?, conversation: ConversationContext) {
        val persona = CoachPersonaCatalog.resolve(
            globalId = settings.coachPersonaId,
            contactOverride = profile?.coachPersonaId,
            learnedStyle = profile?.preferredConversationStyle,
        )
        conversation.setCoachDirective("${persona.label}: ${persona.promptInstruction}")
    }

    @Synchronized fun clear() { activeNumber = "" }
}

package com.realityengine.v4

import android.content.Context

/** Persists response strategies that appeared to keep a caller engaged for future personalization. */
class ConversationOutcomeLearner(context: Context) {
    private val profiles = CallerProfileStore(context.applicationContext)

    fun recordFollowUp(phoneNumber: String, chosen: LiveResponseEngine.ChosenResponse?, callerReply: String) {
        val suggestion = chosen?.suggestion ?: return
        if (phoneNumber.isBlank() || callerReply.trim().length < 4) return
        if (chosen.confidence < .42f) return
        profiles.update(phoneNumber) { profile ->
            profile.preferredConversationStyle = "${suggestion.mode}: ${suggestion.tone}".take(160)
            val starter = "${suggestion.mode} / ${suggestion.tone}: ${suggestion.text}".take(180)
            if (profile.conversationStarters.none { it.equals(starter, true) }) profile.conversationStarters.add(starter)
        }
    }
}

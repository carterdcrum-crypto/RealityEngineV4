package com.realityengine.v4

import android.content.Context

/**
 * Owns one call's compact conversation context and response coach.
 * Keeps the active phone number bound to persistent caller memory while exposing
 * simple caller/user turn entry points for the transcription layer.
 */
class LiveConversationSession(context: Context) {
    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)
    private val conversation = ConversationContext()
    private val responseEngine = LiveResponseEngine(settings, conversation, appContext)
    private var activeNumber = ""

    @Synchronized
    fun bindActiveCaller(): String? {
        val number = CallSessionRegistry.primaryNumber() ?: return null
        if (number != activeNumber) {
            activeNumber = number
            responseEngine.bindCaller(number)
        }
        return number
    }

    fun onCallerTurn(text: String, callback: (LiveResponseEngine.Result?) -> Unit = {}) {
        bindActiveCaller()
        responseEngine.onCallerTurn(text, callback)
    }

    fun onUserTurn(text: String): LiveResponseEngine.ChosenResponse {
        bindActiveCaller()
        return responseEngine.onUserTurn(text)
    }

    @Synchronized
    fun clear() {
        activeNumber = ""
        responseEngine.clearCaller()
        conversation.clear()
        ResponseCoachState.clearSuggestions()
    }

    fun snapshot(): ConversationContext.Snapshot = conversation.snapshot()
}

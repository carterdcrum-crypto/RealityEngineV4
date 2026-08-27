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
    private val outcomeLearner = ConversationOutcomeLearner(appContext)
    private var activeNumber = ""
    private var pendingChosenResponse: LiveResponseEngine.ChosenResponse? = null

    init {
        LiveCoachQuickActions.attach { modeId -> requestQuickMode(modeId) }
    }

    @Synchronized
    fun bindActiveCaller(): String? {
        val number = CallSessionRegistry.primaryNumber() ?: return null
        if (number != activeNumber) {
            activeNumber = number
            pendingChosenResponse = null
            ResponseCoachState.clearCall()
            responseEngine.bindCaller(number)
        }
        return number
    }

    fun onCallerTurn(text: String, callback: (LiveResponseEngine.Result?) -> Unit = {}) {
        bindActiveCaller()
        val chosen = pendingChosenResponse
        if (chosen != null && activeNumber.isNotBlank()) {
            outcomeLearner.recordFollowUp(activeNumber, chosen, text)
            pendingChosenResponse = null
        }
        responseEngine.onCallerTurn(text, callback)
    }

    fun onUserTurn(text: String): LiveResponseEngine.ChosenResponse {
        bindActiveCaller()
        return responseEngine.onUserTurn(text).also { pendingChosenResponse = it }
    }

    fun requestQuickMode(modeId: String): Boolean {
        if (bindActiveCaller().isNullOrBlank()) return false
        if (CoachQuickModeCatalog.byId(modeId) == null) return false
        responseEngine.requestQuickMode(modeId)
        return true
    }

    @Synchronized
    fun clear() {
        activeNumber = ""
        pendingChosenResponse = null
        responseEngine.clearCaller()
        conversation.clear()
        ResponseCoachState.clearCall()
    }

    fun snapshot(): ConversationContext.Snapshot = conversation.snapshot()
}

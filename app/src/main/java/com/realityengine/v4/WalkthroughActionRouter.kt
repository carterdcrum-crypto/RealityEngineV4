package com.realityengine.v4

/** Maps beginner walkthrough buttons to stable setup actions without tying copy to UI logic. */
object WalkthroughActionRouter {
    enum class Action {
        DEFAULT_PHONE,
        PERMISSIONS,
        SHIZUKU,
        TRANSCRIPTION_SETTINGS,
        COACH_SETTINGS,
        CALL_AUDIO,
        NONE
    }

    fun actionFor(step: WalkthroughContent.Step): Action = when (step.actionLabel) {
        "Choose default phone app" -> Action.DEFAULT_PHONE
        "Review permissions" -> Action.PERMISSIONS
        "Check Shizuku" -> Action.SHIZUKU
        "Open transcription settings" -> Action.TRANSCRIPTION_SETTINGS
        "Open coach settings" -> Action.COACH_SETTINGS
        "Check call audio" -> Action.CALL_AUDIO
        else -> Action.NONE
    }
}

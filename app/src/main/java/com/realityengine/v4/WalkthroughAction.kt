package com.realityengine.v4

/** Stable actions behind the beginner walkthrough buttons. */
enum class WalkthroughAction {
    DEFAULT_PHONE,
    PERMISSIONS,
    SHIZUKU,
    TRANSCRIPTION_SETTINGS,
    COACH_SETTINGS,
    CALL_AUDIO,
    NONE
}

object WalkthroughActionResolver {
    fun resolve(step: WalkthroughContent.Step): WalkthroughAction = when (step.actionLabel) {
        "Choose default phone app" -> WalkthroughAction.DEFAULT_PHONE
        "Review permissions" -> WalkthroughAction.PERMISSIONS
        "Check Shizuku" -> WalkthroughAction.SHIZUKU
        "Open transcription settings" -> WalkthroughAction.TRANSCRIPTION_SETTINGS
        "Open coach settings" -> WalkthroughAction.COACH_SETTINGS
        "Check call audio" -> WalkthroughAction.CALL_AUDIO
        else -> WalkthroughAction.NONE
    }
}

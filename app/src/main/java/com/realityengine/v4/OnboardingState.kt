package com.realityengine.v4

import android.content.Context

/** Persistent state for the optional first-launch setup walkthrough. */
class OnboardingState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True until the user finishes setup or explicitly chooses Don't show this again. */
    fun shouldShowOnLaunch(): Boolean = !prefs.getBoolean(KEY_HANDLED, false)

    /** Completing setup permanently dismisses automatic onboarding. */
    fun complete() = markHandled()

    /** Skip only exits the walkthrough for the current app session. */
    fun skip() = Unit

    /** Explicit permanent dismissal. */
    fun dontShowAgain() = markHandled()

    /** Allows a Settings action to intentionally show the walkthrough automatically again. */
    fun markForNextLaunch() {
        prefs.edit().putBoolean(KEY_HANDLED, false).apply()
    }

    private fun markHandled() {
        prefs.edit().putBoolean(KEY_HANDLED, true).apply()
    }

    companion object {
        private const val PREFS = "reality_engine_onboarding"
        private const val KEY_HANDLED = "first_launch_walkthrough_handled"
    }
}

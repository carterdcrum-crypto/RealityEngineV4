package com.realityengine.v4

import android.content.Context

/** Persistent state for the optional first-launch setup walkthrough. */
class OnboardingState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True only until the user finishes, skips, or permanently dismisses onboarding. */
    fun shouldShowOnLaunch(): Boolean = !prefs.getBoolean(KEY_HANDLED, false)

    /** Marks onboarding handled for future launches. It remains manually reopenable from Settings. */
    fun complete() = markHandled()
    fun skip() = markHandled()
    fun dontShowAgain() = markHandled()

    /** Allows a Settings action to intentionally show the walkthrough again without changing launch state. */
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

package com.realityengine.v4

import android.app.Activity

/**
 * Single entry point for presenting onboarding without coupling launch policy to MainActivity.
 * The host supplies the normal app renderer so exiting onboarding always restores the phone UI.
 */
class WalkthroughLauncher(
    private val activity: Activity,
    private val state: OnboardingState = OnboardingState(activity)
) {
    fun showOnFirstLaunch(
        showApp: () -> Unit,
        onAction: (WalkthroughContent.Step) -> Unit = {}
    ): Boolean {
        if (!state.shouldShowOnLaunch()) return false
        show(showApp, onAction)
        return true
    }

    fun showFromSettings(
        showApp: () -> Unit,
        onAction: (WalkthroughContent.Step) -> Unit = {}
    ) {
        show(showApp, onAction)
    }

    private fun show(
        showApp: () -> Unit,
        onAction: (WalkthroughContent.Step) -> Unit
    ) {
        WalkthroughScreen(
            activity = activity,
            state = state,
            onExit = showApp,
            onAction = onAction
        ).show()
    }
}

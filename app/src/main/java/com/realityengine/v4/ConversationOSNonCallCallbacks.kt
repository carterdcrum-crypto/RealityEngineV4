package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Keeps Conversation OS decoration on non-call screens while the integrated Pulse Deck owns
 * CallActivity directly. This avoids running the legacy call-session overlay beside Pulse Deck.
 */
object ConversationOSNonCallCallbacks {
    private val delegate = ConversationOSOverlay.callbacks

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity !is CallActivity) delegate.onActivityCreated(activity, savedInstanceState)
        }

        override fun onActivityStarted(activity: Activity) {
            if (activity !is CallActivity) delegate.onActivityStarted(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity !is CallActivity) delegate.onActivityResumed(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity !is CallActivity) delegate.onActivityPaused(activity)
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity !is CallActivity) delegate.onActivityStopped(activity)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            if (activity !is CallActivity) delegate.onActivitySaveInstanceState(activity, outState)
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity !is CallActivity) delegate.onActivityDestroyed(activity)
        }
    }
}

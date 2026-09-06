package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import java.util.Collections
import java.util.WeakHashMap

/**
 * Minimal runtime bridge for the integrated Pulse Deck call screen.
 *
 * Older presentation overlays predate Pulse Deck and used independent layout/intelligence loops.
 * Running those loops on top of the integrated CallActivity adds avoidable main-thread churn during
 * Telecom handoff. Pulse Deck already owns its transcript, coach and signal surfaces, so this bridge
 * only repaints the embedded signal instrument from the existing live state and never mutates the
 * call hierarchy.
 */
object PulseDeckRuntime {
    private val sessions = Collections.synchronizedMap(WeakHashMap<CallActivity, Session>())

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            if (activity !is CallActivity) return
            val session = synchronized(sessions) {
                sessions[activity] ?: Session(activity).also { sessions[activity] = it }
            }
            session.resume()
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity is CallActivity) sessions[activity]?.pause()
        }

        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            if (activity is CallActivity) sessions.remove(activity)?.destroy()
        }
    }

    private class Session(private val activity: CallActivity) {
        private val handler = Handler(Looper.getMainLooper())
        private val tick = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return
                renderSafely()
                handler.postDelayed(this, 500L)
            }
        }

        fun resume() {
            handler.removeCallbacks(tick)
            renderSafely()
            handler.postDelayed(tick, 500L)
        }

        fun pause() {
            handler.removeCallbacks(tick)
        }

        fun destroy() {
            handler.removeCallbacks(tick)
        }

        private fun renderSafely() {
            runCatching {
                val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@runCatching
                val visual = findSignalVisual(content) ?: return@runCatching
                visual.render(
                    LiveSignalState.snapshot(),
                    LiveTranscriptState.snapshot(),
                    ConversationInsightSnapshot(),
                )
            }
        }
    }

    private fun findSignalVisual(root: View): LiveSignalVisualView? {
        if (root is LiveSignalVisualView && root.tag == CallActivity.PULSE_DECK_SIGNAL_TAG) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findSignalVisual(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}

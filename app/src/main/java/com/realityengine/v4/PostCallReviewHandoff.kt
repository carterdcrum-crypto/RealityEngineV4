package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import java.util.Collections
import java.util.WeakHashMap

/**
 * Guarantees that a user-approved recording reaches the explicit Save / Permanently Delete review.
 *
 * Android may reject an Activity launch initiated from InCallService after the call is no longer
 * considered foreground. This handoff watches the already-visible call Activity, and also performs
 * a catch-up check whenever MainActivity returns to the foreground. The recording remains pending
 * until PostCallReviewActivity receives an explicit user decision.
 */
object PostCallReviewHandoff {
    private val handler = Handler(Looper.getMainLooper())
    private val polls = Collections.synchronizedMap(WeakHashMap<Activity, Runnable>())
    @Volatile private var launchingStartedAtMs: Long = -1L

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            when (activity) {
                is CallActivity -> startCallScreenPoll(activity)
                is MainActivity -> handler.post { launchIfPending(activity) }
            }
        }

        override fun onActivityPaused(activity: Activity) {
            stopPoll(activity)
        }

        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            stopPoll(activity)
        }
    }

    private fun startCallScreenPoll(activity: CallActivity) {
        stopPoll(activity)
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val poll = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) {
                    polls.remove(activity)
                    return
                }
                if (launchIfPending(activity)) {
                    polls.remove(activity)
                    return
                }
                // Keep the foreground rescue alive through the normal post-disconnect finish delay.
                if (android.os.SystemClock.elapsedRealtime() - startedAt < MAX_CALL_POLL_MS) {
                    handler.postDelayed(this, POLL_MS)
                } else {
                    polls.remove(activity)
                }
            }
        }
        polls[activity] = poll
        handler.post(poll)
    }

    private fun stopPoll(activity: Activity) {
        polls.remove(activity)?.let(handler::removeCallbacks)
    }

    private fun launchIfPending(activity: Activity): Boolean {
        if (activity is PostCallReviewActivity || activity.isFinishing || activity.isDestroyed) return false
        val pending = CallRecordingState.peek() ?: return false
        val current = CallSessionRegistry.primary()
        if (current != null && current.state != Call.STATE_DISCONNECTED) return false

        synchronized(this) {
            if (launchingStartedAtMs == pending.startedAtMs) return true
            launchingStartedAtMs = pending.startedAtMs
        }

        return runCatching {
            activity.startActivity(Intent(activity, PostCallReviewActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            true
        }.getOrElse {
            // Allow another foreground Activity to retry if this Activity was transitioning away.
            synchronized(this) {
                if (launchingStartedAtMs == pending.startedAtMs) launchingStartedAtMs = -1L
            }
            false
        }
    }

    /** Called when the review has consumed a pending decision so a future recording can launch. */
    fun decisionConsumed(startedAtMs: Long) {
        synchronized(this) {
            if (launchingStartedAtMs == startedAtMs) launchingStartedAtMs = -1L
        }
    }

    private const val POLL_MS = 120L
    private const val MAX_CALL_POLL_MS = 8_000L
}

package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint

/**
 * Gives the in-call screen native dialer-style proximity behavior.
 *
 * A PROXIMITY_SCREEN_OFF_WAKE_LOCK lets Android turn the physical display/touch surface off while
 * the phone is against the user's ear, rather than merely dimming the Activity. The lock is held
 * only while an active call is using the earpiece; speaker/Bluetooth routes remain fully usable.
 */
object CallProximityScreenGuard {
    private const val POLL_MS = 300L

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundCallActivity: CallActivity? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private val reconcileTick = object : Runnable {
        override fun run() {
            reconcile()
            if (foregroundCallActivity != null) {
                handler.postDelayed(this, POLL_MS)
            }
        }
    }

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity !is CallActivity) return
            foregroundCallActivity = activity
            ensureWakeLock(activity)
            handler.removeCallbacks(reconcileTick)
            handler.post(reconcileTick)
        }

        override fun onActivityPaused(activity: Activity) {
            if (foregroundCallActivity !== activity) return
            foregroundCallActivity = null
            handler.removeCallbacks(reconcileTick)
            release(waitForFar = true)
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (foregroundCallActivity !== activity) return
            foregroundCallActivity = null
            handler.removeCallbacks(reconcileTick)
            release(waitForFar = true)
            proximityWakeLock = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    private fun ensureWakeLock(activity: CallActivity) {
        if (proximityWakeLock != null) return
        val powerManager = activity.getSystemService(PowerManager::class.java) ?: return
        if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return

        proximityWakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "${activity.packageName}:call-proximity-screen-off",
        ).apply {
            setReferenceCounted(false)
        }
    }

    private fun reconcile() {
        val lock = proximityWakeLock ?: return
        if (foregroundCallActivity == null) {
            release(waitForFar = true)
            return
        }

        if (shouldUseEarpieceProximity()) {
            if (!lock.isHeld) lock.acquire()
        } else {
            release(waitForFar = true)
        }
    }

    private fun shouldUseEarpieceProximity(): Boolean {
        val call = CallSessionRegistry.primary() ?: return false
        if (call.state != Call.STATE_ACTIVE) return false

        val service = RealityInCallService.instance ?: return false
        val audio = service.callAudioState
        val legacyEarpiece = audio == null || audio.route == CallAudioState.ROUTE_EARPIECE

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val endpoint = service.currentCallEndpointSnapshot()
            endpoint?.endpointType == CallEndpoint.TYPE_EARPIECE || (endpoint == null && legacyEarpiece)
        } else {
            legacyEarpiece
        }
    }

    private fun release(waitForFar: Boolean) {
        val lock = proximityWakeLock ?: return
        if (!lock.isHeld) return
        if (waitForFar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        } else {
            lock.release()
        }
    }
}

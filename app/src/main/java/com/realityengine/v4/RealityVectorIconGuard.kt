package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * Ensures functional UI icons are Android vector drawables, never Unicode glyphs.
 *
 * Samsung and other OEM fonts may render symbols such as the telephone glyph as full-color emoji.
 * That is unacceptable for the Reality Engine HUD, so the dock and backspace control are converted
 * to packaged vector resources after each view-tree rebuild. Existing click listeners and behavior
 * remain untouched.
 */
object RealityVectorIconGuard {
    private val listeners = Collections.synchronizedMap(
        WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()
    )
    private val passQueued = Collections.synchronizedMap(WeakHashMap<Activity, Boolean>())

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) {
            attach(activity)
            queue(activity)
        }
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = detach(activity)
    }

    private fun attach(activity: Activity) {
        if (listeners.containsKey(activity)) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val listener = ViewTreeObserver.OnGlobalLayoutListener { queue(activity) }
        listeners[activity] = listener
        runCatching { content.viewTreeObserver.addOnGlobalLayoutListener(listener) }
    }

    private fun detach(activity: Activity) {
        passQueued.remove(activity)
        val listener = listeners.remove(activity) ?: return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.viewTreeObserver.isAlive) {
            runCatching { content.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
        }
    }

    private fun queue(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        synchronized(passQueued) {
            if (passQueued[activity] == true) return
            passQueued[activity] = true
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: run {
            passQueued.remove(activity)
            return
        }
        content.post {
            passQueued.remove(activity)
            if (!activity.isFinishing && !activity.isDestroyed) {
                runCatching { sanitizeTree(activity, content, 0) }
            }
        }
    }

    private fun sanitizeTree(activity: Activity, view: View, depth: Int) {
        if (depth > 16) return

        if (view is LinearLayout && convertDockItem(activity, view)) return

        if (view is TextView && view.text?.toString() == "⌫") {
            view.tag = RealityVisuals.HUD_OWNED_TAG
            view.text = ""
            view.gravity = Gravity.CENTER
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_re_backspace, 0, 0, 0)
            view.compoundDrawableTintList = ColorStateList.valueOf(RealityVisuals.Colors.Cyan)
            view.compoundDrawablePadding = 0
        }

        if (view is ViewGroup) {
            for (i in 0 until min(view.childCount, 160)) {
                sanitizeTree(activity, view.getChildAt(i), depth + 1)
            }
        }
    }

    private fun convertDockItem(activity: Activity, item: LinearLayout): Boolean {
        if (item.childCount != 2) return false
        val icon = item.getChildAt(0) as? TextView ?: return false
        val label = item.getChildAt(1) as? TextView ?: return false
        val labelText = label.text?.toString().orEmpty()
        val iconRes = when (labelText) {
            "Phone" -> R.drawable.ic_re_call
            "Traffic" -> R.drawable.ic_re_traffic
            "Intel" -> R.drawable.ic_re_intel
            "Index" -> R.drawable.ic_re_index
            "Settings" -> R.drawable.ic_re_settings
            else -> return false
        }

        val active = colorDistance(label.currentTextColor, RealityVisuals.Colors.Cyan) < 115 ||
            colorDistance(label.currentTextColor, RealityVisuals.Colors.Green) < 115
        val accent = if (active) RealityVisuals.Colors.Green else Color.rgb(155, 196, 226)
        val fill = if (active) Color.rgb(0, 34, 20) else Color.rgb(2, 12, 23)
        val stroke = if (active) RealityVisuals.Colors.Green else Color.rgb(0, 67, 94)

        // Mark the complete dock tile as explicitly owned so the broad fallback skin cannot put a
        // Unicode glyph back after this vector conversion.
        item.tag = RealityVisuals.HUD_OWNED_TAG
        item.background = RealityVisuals.panel(
            activity,
            fill = fill,
            stroke = stroke,
            radiusDp = 7f,
            strokeDp = if (active) 2 else 1,
        )

        icon.tag = RealityVisuals.HUD_OWNED_TAG
        icon.text = ""
        icon.gravity = Gravity.CENTER
        icon.background = null
        icon.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
        icon.compoundDrawableTintList = ColorStateList.valueOf(accent)
        icon.compoundDrawablePadding = 0
        icon.setPadding(7.dp(activity), 3.dp(activity), 7.dp(activity), 3.dp(activity))

        label.setTextColor(accent)
        return true
    }

    private fun colorDistance(a: Int, b: Int): Int =
        abs(Color.red(a) - Color.red(b)) +
            abs(Color.green(a) - Color.green(b)) +
            abs(Color.blue(a) - Color.blue(b))

    private fun Int.dp(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()
}

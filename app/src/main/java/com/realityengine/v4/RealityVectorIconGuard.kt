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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * Makes functional iconography structurally incapable of becoming OEM emoji and applies the
 * Lucid Prism navigation treatment.
 *
 * Dock icon slots are real ImageViews, not text pretending to be an icon. That guarantee remains
 * in place while the old green/cyan tactical dock is replaced by a softer blue/lilac glass dock.
 */
object RealityVectorIconGuard {
    private val listeners = Collections.synchronizedMap(
        WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()
    )
    private val passQueued = Collections.synchronizedMap(WeakHashMap<Activity, Boolean>())

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            sanitizeNow(activity)
            attach(activity)
        }

        override fun onActivityStarted(activity: Activity) {
            sanitizeNow(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            attach(activity)
            sanitizeNow(activity)
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

    private fun sanitizeNow(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        runCatching { sanitizeTree(activity, content, 0) }
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

        // Legacy backspace text is still sanitized as a safety net. New screens use ImageButton
        // directly, but this ensures an older call/dial surface can never regress into a glyph.
        if (view is TextView && view.text?.toString() == "⌫") {
            view.tag = RealityVisuals.HUD_OWNED_TAG
            view.text = ""
            view.gravity = Gravity.CENTER
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_re_backspace, 0, 0, 0)
            view.compoundDrawableTintList = ColorStateList.valueOf(RealityVisuals.Colors.CyanSoft)
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

        val active = isActiveColor(label.currentTextColor)
        val accent = if (active) RealityVisuals.Colors.Lilac else Color.rgb(143, 157, 188)
        val fill = if (active) Color.rgb(25, 26, 57) else Color.rgb(7, 12, 27)
        val stroke = if (active) RealityVisuals.Colors.Lilac else Color.rgb(61, 78, 118)

        item.tag = RealityVisuals.HUD_OWNED_TAG
        item.background = RealityVisuals.panel(
            activity,
            fill = fill,
            stroke = stroke,
            radiusDp = 18f,
            strokeDp = 1,
        )

        val first = item.getChildAt(0)
        val iconView = if (first is ImageView) {
            first
        } else {
            val oldLayoutParams = first.layoutParams
            item.removeViewAt(0)
            ImageView(activity).apply {
                layoutParams = oldLayoutParams
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }.also { item.addView(it, 0) }
        }

        iconView.tag = RealityVisuals.HUD_OWNED_TAG
        iconView.setImageResource(iconRes)
        iconView.imageTintList = ColorStateList.valueOf(accent)
        iconView.background = null
        iconView.setPadding(10.dp(activity), 5.dp(activity), 10.dp(activity), 5.dp(activity))
        iconView.contentDescription = labelText

        label.setTextColor(accent)
        RealityTypography.displayMedium(label, 9f)
        return true
    }

    private fun isActiveColor(color: Int): Boolean =
        colorDistance(color, RealityVisuals.Colors.Cyan) < 120 ||
            colorDistance(color, RealityVisuals.Colors.CyanSoft) < 120 ||
            colorDistance(color, RealityVisuals.Colors.Lilac) < 120 ||
            colorDistance(color, RealityVisuals.Colors.Green) < 95

    private fun colorDistance(a: Int, b: Int): Int =
        abs(Color.red(a) - Color.red(b)) +
            abs(Color.green(a) - Color.green(b)) +
            abs(Color.blue(a) - Color.blue(b))

    private fun Int.dp(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()
}

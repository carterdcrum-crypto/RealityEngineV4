package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Global Lucid Prism shell for V4.
 *
 * This layer deliberately stays presentation-only. Activities, click handlers, telephony, audio,
 * AI, stores and navigation remain owned by the original V4 code. The shell supplies the shared
 * midnight atmosphere and catches older dark widgets that do not already use RealityVisuals.
 */
object RealityOperatorSkin {
    enum class Scene {
        IDLE, CALL, INCOMING, SETTINGS, SUMMARY, MEMORY
    }

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

    fun applySafely(activity: Activity) {
        runCatching { apply(activity) }
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
            if (!activity.isFinishing && !activity.isDestroyed) applySafely(activity)
        }
    }

    private fun apply(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.childCount == 0) return
        val root = content.getChildAt(0)
        val scene = sceneFor(activity, root)

        if (root.background !is LucidPrismSceneDrawable ||
            (root.background as? LucidPrismSceneDrawable)?.scene != scene
        ) {
            root.background = LucidPrismSceneDrawable(scene)
        }

        activity.window.statusBarColor = RealityVisuals.Colors.Background
        activity.window.navigationBarColor = RealityVisuals.Colors.Background
        restyleTree(activity, root, isRoot = true, depth = 0)
    }

    private fun sceneFor(activity: Activity, root: View): Scene = when (activity) {
        is CallActivity -> {
            val text = visibleText(root)
            if (text.contains("INCOMING") || text.contains("ANSWER")) Scene.INCOMING else Scene.CALL
        }
        is CallerMemoryActivity -> Scene.MEMORY
        is PostCallReviewActivity, is PostCallIntelligenceActivity, is TranscriptLibraryActivity -> Scene.SUMMARY
        is SoundboardSettingsActivity, is SupabaseSetupActivity, is CoachPersonaManagerActivity -> Scene.SETTINGS
        is MainActivity -> mainScene(primaryMainText(root))
        else -> Scene.IDLE
    }

    private fun mainScene(text: String): Scene = when {
        text.contains("SYSTEM READINESS") || text.contains("AI ROUTING") ||
            text.contains("PRIVATE UPDATE ACCESS") || text.contains("SETUP MODULE") -> Scene.SETTINGS
        text.contains("CONVERSATION STYLE") || text.contains("RECENT TOPICS") ||
            text.contains("IMPORTANT FACTS") || text.contains("CONTACT INDEX") -> Scene.MEMORY
        text.contains("TRAFFIC") || text.contains("INTELLIGENCE HUB") ||
            text.contains("CONVERSATION SEARCH") -> Scene.SUMMARY
        else -> Scene.IDLE
    }

    private fun primaryMainText(root: View): String {
        val scroll = firstScrollView(root, 0)
        return visibleText(scroll ?: root)
    }

    private fun firstScrollView(view: View, depth: Int): ScrollView? {
        if (depth > 12) return null
        if (view is ScrollView) return view
        if (view is ViewGroup) {
            for (i in 0 until min(view.childCount, 90)) {
                firstScrollView(view.getChildAt(i), depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun visibleText(root: View): String {
        val builder = StringBuilder(256)
        collectText(root, builder, 0)
        return builder.toString().uppercase()
    }

    private fun collectText(view: View, out: StringBuilder, depth: Int) {
        if (depth > 10 || out.length > 2200 || view.visibility != View.VISIBLE) return
        if (view is TextView) {
            val value = view.text?.toString().orEmpty()
            if (value.isNotBlank()) out.append(value).append('\n')
        }
        if (view is ViewGroup) {
            for (i in 0 until min(view.childCount, 90)) collectText(view.getChildAt(i), out, depth + 1)
        }
    }

    private fun restyleTree(activity: Activity, view: View, isRoot: Boolean, depth: Int) {
        if (depth > 15) return

        // Explicitly designed components own their appearance. This keeps the global compatibility
        // pass from flattening the Lucid dialer, vector dock, transcript cards or bespoke controls.
        if (view.tag == RealityVisuals.HUD_OWNED_TAG) return

        if (view is LinearLayout && isBottomNavContainer(view)) {
            view.background = RealityVisuals.panel(
                activity,
                fill = Color.rgb(7, 12, 27),
                stroke = Color.rgb(74, 91, 139),
                radiusDp = 20f,
                strokeDp = 1,
            )
        }

        if (!isRoot) {
            runCatching {
                when (view) {
                    is Button -> {
                        val rawAccent = view.currentTextColor.takeIf { Color.alpha(it) > 100 }
                            ?: RealityVisuals.Colors.CyanSoft
                        val accent = normalizeAccent(rawAccent)
                        val destructive = accentRed(rawAccent)
                        view.background = RealityVisuals.panel(
                            activity,
                            fill = if (destructive) RealityVisuals.Colors.DangerFill else RealityVisuals.Colors.Panel,
                            stroke = accent,
                            radiusDp = 17f,
                            strokeDp = 1,
                        )
                        view.stateListAnimator = null
                        view.elevation = 0f
                    }
                    is EditText -> {
                        val old = view.background
                        if (old is ColorDrawable && isDark(old.color)) {
                            view.background = RealityVisuals.panel(
                                activity,
                                fill = RealityVisuals.Colors.BackgroundRaised,
                                stroke = RealityVisuals.Colors.Border,
                                radiusDp = 17f,
                                strokeDp = 1,
                            )
                        }
                    }
                    is TextView -> {
                        val old = view.background
                        if (old != null && shouldRestyleTextPlate(view, old)) {
                            val accent = normalizeAccent(view.currentTextColor)
                            view.background = RealityVisuals.panel(
                                activity,
                                fill = RealityVisuals.Colors.Panel,
                                stroke = accent,
                                radiusDp = 15f,
                                strokeDp = 1,
                            )
                        }
                    }
                    else -> {
                        val bg = view.background
                        if (bg is ColorDrawable && isDark(bg.color)) {
                            bg.color = Color.argb(34, 7, 13, 28)
                        }
                    }
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until min(view.childCount, 140)) {
                restyleTree(activity, view.getChildAt(i), isRoot = false, depth = depth + 1)
            }
        }
    }

    private fun isBottomNavContainer(view: LinearLayout): Boolean {
        if (view.childCount != 5) return false
        val labels = (0 until view.childCount).mapNotNull { i ->
            val child = view.getChildAt(i) as? LinearLayout ?: return@mapNotNull null
            (child.getChildAtOrNull(1) as? TextView)?.text?.toString()
        }
        return labels.toSet().containsAll(setOf("Phone", "Traffic", "Intel", "Index", "Settings"))
    }

    private fun ViewGroup.getChildAtOrNull(index: Int): View? =
        if (index in 0 until childCount) getChildAt(index) else null

    private fun normalizeAccent(color: Int): Int {
        val hi = max(Color.red(color), max(Color.green(color), Color.blue(color)))
        val lo = min(Color.red(color), min(Color.green(color), Color.blue(color)))
        return if (hi - lo < 35 && hi > 135) RealityVisuals.Colors.Border else color
    }

    private fun shouldRestyleTextPlate(view: TextView, bg: Drawable): Boolean {
        if (view.text.isNullOrBlank()) return false
        if (view.height in 1..18 || view.width in 1..40) return false
        return bg !is LucidPrismSceneDrawable
    }

    private fun accentRed(color: Int): Boolean =
        Color.red(color) > 175 && Color.red(color) > Color.green(color) * 1.32f

    private fun isDark(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.alpha(color) > 150 && r < 55 && g < 62 && b < 82
    }

    private class LucidPrismSceneDrawable(
        val scene: Scene,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var drawableAlpha = 255

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val w = bounds.width().toFloat().coerceAtLeast(1f)
            val h = bounds.height().toFloat().coerceAtLeast(1f)

            paint.shader = LinearGradient(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                intArrayOf(Color.rgb(5, 11, 25), Color.rgb(2, 7, 18), Color.rgb(1, 4, 12)),
                floatArrayOf(0f, .54f, 1f),
                Shader.TileMode.CLAMP,
            )
            paint.alpha = drawableAlpha
            canvas.drawRect(bounds, paint)

            val (firstTint, secondTint, intensity) = when (scene) {
                Scene.IDLE -> Triple(RealityVisuals.Colors.Cyan, RealityVisuals.Colors.Lilac, 38)
                Scene.CALL -> Triple(RealityVisuals.Colors.Lilac, RealityVisuals.Colors.Cyan, 45)
                Scene.INCOMING -> Triple(RealityVisuals.Colors.Cyan, RealityVisuals.Colors.Lilac, 54)
                Scene.SETTINGS -> Triple(RealityVisuals.Colors.CyanSoft, RealityVisuals.Colors.Lilac, 32)
                Scene.SUMMARY -> Triple(RealityVisuals.Colors.Lilac, RealityVisuals.Colors.CyanSoft, 35)
                Scene.MEMORY -> Triple(RealityVisuals.Colors.Lilac, RealityVisuals.Colors.Cyan, 40)
            }

            paint.shader = RadialGradient(
                bounds.left + w * .18f,
                bounds.top + h * .12f,
                max(w, h) * .68f,
                intArrayOf(withAlpha(firstTint, intensity), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            paint.alpha = drawableAlpha
            canvas.drawRect(bounds, paint)

            paint.shader = RadialGradient(
                bounds.right - w * .10f,
                bounds.bottom - h * .18f,
                max(w, h) * .62f,
                intArrayOf(withAlpha(secondTint, (intensity * .72f).toInt()), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(bounds, paint)

            paint.shader = LinearGradient(
                bounds.left.toFloat(),
                bounds.bottom.toFloat(),
                bounds.right.toFloat(),
                bounds.top.toFloat(),
                intArrayOf(
                    Color.TRANSPARENT,
                    withAlpha(RealityVisuals.Colors.CyanSoft, 10),
                    withAlpha(RealityVisuals.Colors.Lilac, 15),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, .34f, .62f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(bounds, paint)
            paint.shader = null
        }

        override fun setAlpha(alpha: Int) {
            drawableAlpha = alpha.coerceIn(0, 255)
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.OPAQUE

        private fun withAlpha(color: Int, alpha: Int): Int =
            Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}

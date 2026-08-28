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

/** Presentation-only premium shell for V4. Functional behavior remains in the existing app code. */
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
        if (view.tag == RealityVisuals.HUD_OWNED_TAG || view.tag == "realityengine.nav.shell") return

        if (view is LinearLayout && isBottomNavContainer(view)) {
            view.tag = "realityengine.nav.shell"
            view.background = RealityVisuals.panel(
                activity,
                fill = Color.rgb(8, 13, 25),
                stroke = Color.rgb(48, 60, 88),
                radiusDp = 27f,
            )
            view.setPadding(4.dp(activity), 3.dp(activity), 4.dp(activity), 3.dp(activity))
            return
        }

        if (!isRoot) {
            runCatching {
                when (view) {
                    is Button -> {
                        val rawAccent = view.currentTextColor.takeIf { Color.alpha(it) > 100 }
                            ?: RealityVisuals.Colors.CyanSoft
                        val destructive = accentRed(rawAccent)
                        val accent = normalizeAccent(rawAccent)
                        view.background = RealityVisuals.panel(
                            activity,
                            fill = if (destructive) RealityVisuals.Colors.DangerFill else RealityVisuals.Colors.Panel,
                            stroke = if (destructive) accent else Color.rgb(58, 70, 101),
                            radiusDp = 20f,
                        )
                        view.stateListAnimator = null
                        view.elevation = 0f
                        view.letterSpacing = .01f
                    }
                    is EditText -> {
                        val old = view.background
                        if (old is ColorDrawable && isDark(old.color)) {
                            view.background = RealityVisuals.panel(
                                activity,
                                fill = RealityVisuals.Colors.BackgroundRaised,
                                stroke = RealityVisuals.Colors.Border,
                                radiusDp = 21f,
                            )
                        }
                    }
                    is TextView -> {
                        val old = view.background
                        if (old != null && shouldRestyleTextPlate(view, old)) {
                            view.background = RealityVisuals.panel(
                                activity,
                                fill = RealityVisuals.Colors.Panel,
                                stroke = Color.rgb(55, 67, 96),
                                radiusDp = 19f,
                            )
                        }
                    }
                    else -> {
                        val bg = view.background
                        if (bg is ColorDrawable && isDark(bg.color)) {
                            bg.color = Color.argb(24, 7, 13, 28)
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
        return if (hi - lo < 35 && hi > 135) RealityVisuals.Colors.TextDim else color
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
                intArrayOf(Color.rgb(6, 10, 22), Color.rgb(3, 7, 16), Color.rgb(2, 5, 12)),
                floatArrayOf(0f, .57f, 1f),
                Shader.TileMode.CLAMP,
            )
            paint.alpha = drawableAlpha
            canvas.drawRect(bounds, paint)

            val (firstTint, secondTint, intensity) = when (scene) {
                Scene.IDLE -> Triple(RealityVisuals.Colors.Cyan, RealityVisuals.Colors.Lilac, 26)
                Scene.CALL -> Triple(RealityVisuals.Colors.Lilac, RealityVisuals.Colors.Cyan, 30)
                Scene.INCOMING -> Triple(RealityVisuals.Colors.Cyan, RealityVisuals.Colors.Lilac, 38)
                Scene.SETTINGS -> Triple(RealityVisuals.Colors.CyanSoft, RealityVisuals.Colors.Lilac, 22)
                Scene.SUMMARY -> Triple(RealityVisuals.Colors.Lilac, RealityVisuals.Colors.CyanSoft, 24)
                Scene.MEMORY -> Triple(RealityVisuals.Colors.Lilac, RealityVisuals.Colors.Cyan, 28)
            }

            paint.shader = RadialGradient(
                bounds.left + w * .15f,
                bounds.top + h * .10f,
                max(w, h) * .74f,
                intArrayOf(withAlpha(firstTint, intensity), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            paint.alpha = drawableAlpha
            canvas.drawRect(bounds, paint)

            paint.shader = RadialGradient(
                bounds.right - w * .08f,
                bounds.bottom - h * .15f,
                max(w, h) * .68f,
                intArrayOf(withAlpha(secondTint, (intensity * .58f).toInt()), Color.TRANSPARENT),
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
                    withAlpha(RealityVisuals.Colors.CyanSoft, 5),
                    withAlpha(RealityVisuals.Colors.Lilac, 8),
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

    private fun Int.dp(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()
}

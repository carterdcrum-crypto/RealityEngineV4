package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.min

/**
 * Asset-first visual shell for the existing V4 hierarchy.
 *
 * The observer never performs decoration inside layout. It merely queues a post-layout pass and
 * de-bounces multiple layout notifications. That lets MainActivity swap Phone / Traffic / Intel /
 * Index / Settings content while keeping the new skin, without the re-entrant layout behavior that
 * caused the first operator build to crash.
 */
object RealityOperatorSkin {
    enum class Scene(val frame: Int) {
        IDLE(0), CALL(1), INCOMING(2), SETTINGS(3), SUMMARY(4), MEMORY(5)
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
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            // Important: no View mutation in the layout callback. Queue a later pass instead.
            queue(activity)
        }
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

        if (root.background !is OperatorSceneDrawable || (root.background as? OperatorSceneDrawable)?.scene != scene) {
            root.background = OperatorSceneDrawable(activity, scene)
        }
        activity.window.statusBarColor = Color.rgb(2, 7, 12)
        activity.window.navigationBarColor = Color.rgb(2, 7, 12)
        restyleTree(activity, root, isRoot = true, depth = 0)
    }

    private fun sceneFor(activity: Activity, root: View): Scene = when (activity) {
        is CallActivity -> {
            val text = visibleText(root)
            if (text.contains("● INCOMING") || text.contains("ANSWER")) Scene.INCOMING else Scene.CALL
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
        if (!isRoot) {
            runCatching {
                when (view) {
                    is Button -> {
                        val accent = view.currentTextColor.takeIf { Color.alpha(it) > 100 } ?: RealityVisuals.Colors.Cyan
                        val destructive = accentRed(accent)
                        view.background = RealityVisuals.panel(
                            activity,
                            fill = if (destructive) RealityVisuals.Colors.DangerFill else RealityVisuals.Colors.Panel,
                            stroke = accent,
                            radiusDp = 14f,
                        )
                        view.stateListAnimator = null
                        view.elevation = 0f
                    }
                    is EditText -> Unit
                    is TextView -> {
                        val old = view.background
                        if (old != null && shouldRestyleTextPlate(view, old)) {
                            val accent = view.currentTextColor.takeIf { Color.alpha(it) > 100 } ?: RealityVisuals.Colors.Border
                            view.background = RealityVisuals.panel(
                                activity,
                                fill = RealityVisuals.Colors.Panel,
                                stroke = accent,
                                radiusDp = 11f,
                            )
                        }
                    }
                    else -> {
                        val bg = view.background
                        if (bg is ColorDrawable && isDark(bg.color)) {
                            bg.color = Color.argb(34, Color.red(bg.color), Color.green(bg.color), Color.blue(bg.color))
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

    private fun shouldRestyleTextPlate(view: TextView, bg: Drawable): Boolean {
        if (view.text.isNullOrBlank()) return false
        if (view.height in 1..18 || view.width in 1..40) return false
        return bg !is OperatorSceneDrawable
    }

    private fun accentRed(color: Int): Boolean =
        Color.red(color) > 180 && Color.red(color) > Color.green(color) * 1.35f

    private fun isDark(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.alpha(color) > 180 && r < 45 && g < 55 && b < 70
    }

    private class OperatorSceneDrawable(
        activity: Activity,
        val scene: Scene,
    ) : Drawable() {
        private val bitmap = Atlas.bitmap(activity)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(3, 7, 12) }

        override fun draw(canvas: Canvas) {
            val source = bitmap
            if (bounds.isEmpty) return
            if (source == null || source.width <= 0 || source.height <= 0) {
                canvas.drawRect(bounds, fallbackPaint)
                return
            }
            val calculated = ((source.width * 13f) / 6f).toInt().coerceAtLeast(1)
            val frameHeight = if (calculated * 6 <= source.height) calculated else (source.height / 6).coerceAtLeast(1)
            val top = (scene.frame * frameHeight).coerceIn(0, (source.height - 1).coerceAtLeast(0))
            val bottom = (top + frameHeight).coerceAtMost(source.height)
            if (bottom <= top) {
                canvas.drawRect(bounds, fallbackPaint)
                return
            }
            canvas.drawBitmap(source, Rect(0, top, source.width, bottom), bounds, paint)
            shadePaint.shader = LinearGradient(
                0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
                intArrayOf(Color.argb(28, 0, 0, 0), Color.argb(4, 0, 0, 0), Color.argb(90, 0, 0, 0)),
                floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawRect(bounds, shadePaint)
            shadePaint.shader = null
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

    private object Atlas {
        @Volatile private var cached: Bitmap? = null
        @Volatile private var attempted = false

        fun bitmap(activity: Activity): Bitmap? {
            cached?.let { return it }
            if (attempted) return null
            return synchronized(this) {
                cached?.let { return@synchronized it }
                if (attempted) return@synchronized null
                attempted = true
                runCatching { BitmapFactory.decodeResource(activity.resources, R.drawable.re_operator_atlas) }
                    .getOrNull()?.also { cached = it }
            }
        }
    }
}

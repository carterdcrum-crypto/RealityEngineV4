package com.realityengine.v4

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared screen-size profile for the entire app.
 *
 * This is presentation only: it never changes click handlers, visibility, activity lifecycle,
 * Telecom, call state, audio, transcription, AI, storage, or navigation behavior.
 */
object AdaptiveUi {
    enum class SizeClass { COMPACT, STANDARD, LARGE }

    data class Profile(
        val sizeClass: SizeClass,
        val widthDp: Int,
        val heightDp: Int,
        val fontScale: Float,
        val widthScale: Float,
        val heightScale: Float,
        val spacingScale: Float,
    )

    fun profile(context: Context): Profile {
        val config = context.resources.configuration
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val widthDp = config.screenWidthDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.widthPixels / density).roundToInt()
        val heightDp = config.screenHeightDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.heightPixels / density).roundToInt()
        val fontScale = config.fontScale.coerceAtLeast(.85f)

        val sizeClass = when {
            widthDp < 360 || heightDp < 700 || fontScale >= 1.20f -> SizeClass.COMPACT
            widthDp >= 430 && heightDp >= 850 && fontScale <= 1.10f -> SizeClass.LARGE
            else -> SizeClass.STANDARD
        }

        val widthScale = when {
            widthDp < 330 -> .86f
            widthDp < 360 -> .92f
            sizeClass == SizeClass.LARGE -> 1.04f
            else -> 1f
        }
        val heightScale = when {
            heightDp < 620 -> .76f
            heightDp < 700 -> .84f
            sizeClass == SizeClass.COMPACT -> .90f
            sizeClass == SizeClass.LARGE -> 1.04f
            else -> 1f
        }
        val spacingScale = when {
            heightDp < 620 || widthDp < 330 -> .76f
            sizeClass == SizeClass.COMPACT -> .84f
            sizeClass == SizeClass.LARGE -> 1.04f
            else -> 1f
        }

        return Profile(
            sizeClass = sizeClass,
            widthDp = widthDp,
            heightDp = heightDp,
            fontScale = fontScale,
            widthScale = widthScale,
            heightScale = heightScale,
            spacingScale = spacingScale,
        )
    }

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun widthPx(context: Context, baseDp: Int): Int =
        (dp(context, baseDp.toFloat()) * profile(context).widthScale).roundToInt()

    fun heightPx(context: Context, baseDp: Int, minDp: Int = 0): Int {
        val scaled = (dp(context, baseDp.toFloat()) * profile(context).heightScale).roundToInt()
        return if (minDp > 0) max(scaled, dp(context, minDp.toFloat())) else scaled
    }

    fun spacePx(context: Context, baseDp: Int): Int =
        (dp(context, baseDp.toFloat()) * profile(context).spacingScale).roundToInt()
}

/**
 * Applies the shared adaptive profile to every Activity view hierarchy, including screens that are
 * rebuilt dynamically inside MainActivity and setContentView swaps such as onboarding.
 *
 * Each concrete View is adapted once so repeated layout passes cannot compound dimensions. New
 * views added later are picked up automatically by the content-root layout listener.
 *
 * Android 15+ enforces edge-to-edge for targetSdk 35 apps. The Activity content root therefore also
 * receives the real navigation-bar inset reported by Android so tappable controls never sit behind
 * Samsung's Recents/Home/Back bar (or a side navigation bar after rotation). This is dynamic rather
 * than device-hardcoded, so it also follows gesture/button navigation changes at runtime.
 */
object AdaptiveLayoutRuntime {
    private val sessions = Collections.synchronizedMap(WeakHashMap<Activity, Session>())

    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) {
            val session = synchronized(sessions) {
                sessions[activity] ?: Session(activity).also { sessions[activity] = it }
            }
            session.resume()
        }

        override fun onActivityPaused(activity: Activity) {
            sessions[activity]?.pause()
        }

        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            sessions.remove(activity)?.destroy()
        }
    }

    private class Session(private val activity: Activity) {
        private val processed = WeakHashMap<View, Boolean>()
        private var root: ViewGroup? = null
        private var listener: ViewTreeObserver.OnGlobalLayoutListener? = null
        private var queued = false

        private var insetTarget: ViewGroup? = null
        private var insetBaseLeft = 0
        private var insetBaseTop = 0
        private var insetBaseRight = 0
        private var insetBaseBottom = 0

        fun resume() {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            if (root !== content || listener == null) {
                detachLayoutListener()
                root = content
                listener = ViewTreeObserver.OnGlobalLayoutListener { queue() }.also { current ->
                    if (content.viewTreeObserver.isAlive) {
                        content.viewTreeObserver.addOnGlobalLayoutListener(current)
                    }
                }
            }
            queue()
        }

        fun pause() {
            detachLayoutListener()
        }

        fun destroy() {
            detachLayoutListener()
            clearSafeAreaInsets()
            processed.clear()
        }

        private fun detachLayoutListener() {
            val currentRoot = root
            val currentListener = listener
            if (currentRoot != null && currentListener != null && currentRoot.viewTreeObserver.isAlive) {
                currentRoot.viewTreeObserver.removeOnGlobalLayoutListener(currentListener)
            }
            root = null
            listener = null
            queued = false
        }

        private fun queue() {
            if (queued) return
            val content = root ?: activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            queued = true
            content.post {
                queued = false
                if (activity.isFinishing || activity.isDestroyed) return@post
                // Adapt first, then capture the adapted base padding before adding system insets.
                adaptNewViews(content)
                ensureSafeAreaInsets(content)
            }
        }

        private fun ensureSafeAreaInsets(content: ViewGroup) {
            if (insetTarget !== content) {
                clearSafeAreaInsets()
                insetTarget = content
                insetBaseLeft = content.paddingLeft
                insetBaseTop = content.paddingTop
                insetBaseRight = content.paddingRight
                insetBaseBottom = content.paddingBottom

                content.setOnApplyWindowInsetsListener { view, windowInsets ->
                    val nav = navigationBarInsets(windowInsets)
                    view.setPadding(
                        insetBaseLeft + nav.left,
                        insetBaseTop,
                        insetBaseRight + nav.right,
                        insetBaseBottom + nav.bottom,
                    )
                    // Do not consume: descendant views that explicitly handle other inset types
                    // (IME, display cutout, etc.) must still receive them.
                    windowInsets
                }
            }
            content.requestApplyInsets()
        }

        private fun clearSafeAreaInsets() {
            insetTarget?.setOnApplyWindowInsetsListener(null)
            insetTarget = null
        }

        private data class NavigationInsets(val left: Int, val right: Int, val bottom: Int)

        private fun navigationBarInsets(windowInsets: WindowInsets): NavigationInsets {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val insets = windowInsets.getInsets(WindowInsets.Type.navigationBars())
                NavigationInsets(insets.left, insets.right, insets.bottom)
            } else {
                @Suppress("DEPRECATION")
                NavigationInsets(
                    left = windowInsets.systemWindowInsetLeft,
                    right = windowInsets.systemWindowInsetRight,
                    bottom = windowInsets.systemWindowInsetBottom,
                )
            }
        }

        private fun adaptNewViews(view: View) {
            if (!processed.containsKey(view)) {
                processed[view] = true
                applySizing(view)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    adaptNewViews(view.getChildAt(index))
                }
            }
        }

        private fun applySizing(view: View) {
            val profile = AdaptiveUi.profile(activity)
            if (
                profile.widthScale == 1f &&
                profile.heightScale == 1f &&
                profile.spacingScale == 1f
            ) return

            val originalLeft = view.paddingLeft
            val originalTop = view.paddingTop
            val originalRight = view.paddingRight
            val originalBottom = view.paddingBottom
            view.setPadding(
                scalePx(originalLeft, profile.widthScale),
                scalePx(originalTop, profile.spacingScale),
                scalePx(originalRight, profile.widthScale),
                scalePx(originalBottom, profile.spacingScale),
            )

            val params = view.layoutParams
            if (params != null) {
                if (params is ViewGroup.MarginLayoutParams) {
                    params.setMargins(
                        scalePx(params.leftMargin, profile.spacingScale),
                        scalePx(params.topMargin, profile.spacingScale),
                        scalePx(params.rightMargin, profile.spacingScale),
                        scalePx(params.bottomMargin, profile.spacingScale),
                    )
                }

                if (params.width > 0) {
                    var target = scalePx(params.width, profile.widthScale)
                    if (view is Button) target = max(target, AdaptiveUi.dp(activity, 44f))
                    params.width = target
                }

                if (params.height > 0) {
                    val smallTextRow = view is TextView && view !is Button &&
                        params.height <= AdaptiveUi.dp(activity, 36f)
                    if (!smallTextRow) {
                        var target = scalePx(params.height, profile.heightScale)
                        if (view is Button) target = max(target, AdaptiveUi.dp(activity, 44f))
                        params.height = target
                    }
                }
                view.layoutParams = params
            }

            if (view.minimumWidth > 0) {
                var target = scalePx(view.minimumWidth, profile.widthScale)
                if (view.isClickable) target = max(target, AdaptiveUi.dp(activity, 44f))
                view.minimumWidth = target
            }
            if (view.minimumHeight > 0) {
                val smallTextMinimum = view is TextView && view !is Button &&
                    view.minimumHeight <= AdaptiveUi.dp(activity, 36f)
                if (!smallTextMinimum) {
                    var target = scalePx(view.minimumHeight, profile.heightScale)
                    if (view.isClickable) target = max(target, AdaptiveUi.dp(activity, 44f))
                    view.minimumHeight = target
                }
            }
        }

        private fun scalePx(value: Int, factor: Float): Int {
            if (value <= 0) return value
            return max(1, (value * factor).roundToInt())
        }
    }
}

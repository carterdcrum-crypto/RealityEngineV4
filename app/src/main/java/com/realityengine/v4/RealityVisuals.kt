package com.realityengine.v4

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.max

/**
 * Shared visual language for Reality Engine V4.
 *
 * The raster operator environment now lives underneath these controls. Surfaces are intentionally
 * translucent so the asset layer remains visible, while the original V4 control semantics and
 * color-coded states stay unchanged.
 */
object RealityVisuals {
    object Colors {
        val Background: Int = Color.rgb(3, 7, 12)
        val BackgroundRaised: Int = Color.rgb(5, 12, 19)
        val Panel: Int = Color.rgb(8, 17, 26)
        val PanelStrong: Int = Color.rgb(10, 22, 32)
        val Cyan: Int = Color.rgb(40, 224, 255)
        val CyanSoft: Int = Color.rgb(104, 207, 228)
        val Magenta: Int = Color.rgb(255, 55, 190)
        val Green: Int = Color.rgb(75, 255, 165)
        val Amber: Int = Color.rgb(255, 196, 88)
        val Text: Int = Color.rgb(222, 247, 251)
        val TextDim: Int = Color.rgb(118, 147, 163)
        val Border: Int = Color.rgb(20, 88, 108)
        val Track: Int = Color.rgb(15, 40, 51)
        val DangerFill: Int = Color.rgb(36, 9, 24)
    }

    fun panel(
        context: Context,
        fill: Int = Colors.Panel,
        stroke: Int = Colors.Border,
        radiusDp: Float = 12f,
        strokeDp: Int = 1,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(glass(fill))
        setStroke(dp(context, strokeDp), glowStroke(stroke))
        cornerRadius = radiusDp * context.resources.displayMetrics.density
    }

    fun circle(
        context: Context,
        fill: Int = Colors.PanelStrong,
        stroke: Int = Colors.Cyan,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(glass(fill, 228))
        setStroke(dp(context, 1), glowStroke(stroke))
    }

    fun styleControl(
        button: Button,
        iconRes: Int,
        accent: Int = Colors.Cyan,
        destructive: Boolean = false,
        radiusDp: Float = 14f,
    ) {
        button.apply {
            textSize = 10f
            letterSpacing = .05f
            setTextColor(accent)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            background = panel(
                context,
                fill = if (destructive) Colors.DangerFill else Colors.Panel,
                stroke = accent,
                radiusDp = radiusDp,
            )
            stateListAnimator = null
            elevation = 0f
            setAllCaps(false)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
            if (iconRes != 0) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(accent)
                compoundDrawablePadding = dp(context, 6)
            }
        }
    }

    fun styleMicroLabel(view: TextView, accent: Int = Colors.TextDim) {
        view.apply {
            textSize = 8.5f
            letterSpacing = .12f
            setTextColor(accent)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
        }
    }

    fun styleSignal(bar: ProgressBar, accent: Int) {
        bar.max = 100
        bar.progressTintList = ColorStateList.valueOf(accent)
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.argb(190, 15, 40, 51))
    }

    fun animateSignal(bar: ProgressBar, target: Int) {
        val clamped = target.coerceIn(0, 100)
        if (bar.progress == clamped) return
        ObjectAnimator.ofInt(bar, "progress", bar.progress, clamped).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    fun reveal(view: View) {
        view.animate().cancel()
        view.alpha = .72f
        view.scaleX = .985f
        view.scaleY = .985f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun pulseOnce(view: View) {
        view.animate().cancel()
        view.animate()
            .alpha(.62f)
            .setDuration(180L)
            .withEndAction {
                view.animate().alpha(1f).setDuration(260L).start()
            }
            .start()
    }

    private fun glass(color: Int, darkAlpha: Int = 214): Int {
        if (Color.alpha(color) < 255) return color
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val bright = max(r, max(g, b)) > 190 && (r + g + b) > 340
        return Color.argb(if (bright) 245 else darkAlpha, r, g, b)
    }

    private fun glowStroke(color: Int): Int {
        if (Color.alpha(color) < 255) return color
        return Color.argb(230, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

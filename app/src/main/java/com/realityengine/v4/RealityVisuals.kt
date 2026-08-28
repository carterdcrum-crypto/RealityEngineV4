package com.realityengine.v4

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
 * Behavior remains owned by the existing V4 screens and engines. This object owns presentation.
 * The core surface is now backed by a shipped raster glass plate rather than a plain generated
 * rectangle, so Settings, call controls, memory, post-call cards and every other caller of panel()
 * inherit the visual overhaul without duplicating feature code.
 */
object RealityVisuals {
    object Colors {
        val Background: Int = Color.rgb(2, 6, 12)
        val BackgroundRaised: Int = Color.rgb(5, 12, 21)
        val Panel: Int = Color.rgb(7, 15, 27)
        val PanelStrong: Int = Color.rgb(9, 20, 34)
        val Cyan: Int = Color.rgb(40, 224, 255)
        val CyanSoft: Int = Color.rgb(104, 207, 228)
        val Magenta: Int = Color.rgb(255, 55, 190)
        val Green: Int = Color.rgb(75, 255, 165)
        val Amber: Int = Color.rgb(255, 196, 88)
        val Text: Int = Color.rgb(229, 250, 253)
        val TextDim: Int = Color.rgb(123, 157, 176)
        val Border: Int = Color.rgb(22, 104, 128)
        val Track: Int = Color.rgb(12, 38, 52)
        val DangerFill: Int = Color.rgb(38, 8, 25)
    }

    fun panel(
        context: Context,
        fill: Int = Colors.Panel,
        stroke: Int = Colors.Border,
        radiusDp: Float = 12f,
        strokeDp: Int = 1,
    ): Drawable = RasterGlassDrawable(
        context = context,
        fill = fill,
        stroke = stroke,
        radiusPx = radiusDp * context.resources.displayMetrics.density,
        strokePx = dp(context, strokeDp).toFloat().coerceAtLeast(1f),
    )

    fun circle(
        context: Context,
        fill: Int = Colors.PanelStrong,
        stroke: Int = Colors.Cyan,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(glass(fill, 232))
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
            letterSpacing = .06f
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
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
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
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.argb(200, 11, 37, 50))
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
            .withEndAction { view.animate().alpha(1f).setDuration(260L).start() }
            .start()
    }

    private class RasterGlassDrawable(
        context: Context,
        private val fill: Int,
        private val stroke: Int,
        private val radiusPx: Float,
        private val strokePx: Float,
    ) : Drawable() {
        private val asset: Bitmap? = PanelAsset.bitmap(context)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glass(fill, if (isBright(fill)) 246 else 220)
            style = Paint.Style.FILL
        }
        private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 190
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glowStroke(stroke)
            style = Paint.Style.STROKE
            strokeWidth = strokePx
        }
        private var drawableAlpha = 255

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val inset = strokePx / 2f
            val rect = RectF(
                bounds.left + inset,
                bounds.top + inset,
                bounds.right - inset,
                bounds.bottom - inset,
            )

            fillPaint.alpha = drawableAlpha
            canvas.drawRoundRect(rect, radiusPx, radiusPx, fillPaint)

            asset?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }?.let { bitmap ->
                texturePaint.alpha = (drawableAlpha * 0.78f).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.clipRoundRect(rect, radiusPx, radiusPx)
                canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), bounds, texturePaint)
                canvas.restore()
            }

            strokePaint.alpha = drawableAlpha
            canvas.drawRoundRect(rect, radiusPx, radiusPx, strokePaint)
        }

        override fun setAlpha(alpha: Int) {
            drawableAlpha = alpha.coerceIn(0, 255)
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            texturePaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private object PanelAsset {
        @Volatile private var cached: Bitmap? = null
        @Volatile private var attempted = false

        fun bitmap(context: Context): Bitmap? {
            cached?.let { return it }
            if (attempted) return null
            return synchronized(this) {
                cached?.let { return@synchronized it }
                if (attempted) return@synchronized null
                attempted = true
                runCatching {
                    BitmapFactory.decodeResource(context.resources, R.drawable.re_panel_glass)
                }.getOrNull()?.also { cached = it }
            }
        }
    }

    private fun glass(color: Int, darkAlpha: Int = 214): Int {
        if (Color.alpha(color) < 255) return color
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.argb(if (isBright(color)) 245 else darkAlpha, r, g, b)
    }

    private fun isBright(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return max(r, max(g, b)) > 190 && (r + g + b) > 340
    }

    private fun glowStroke(color: Int): Int {
        if (Color.alpha(color) < 255) return color
        return Color.argb(236, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

package com.realityengine.v4

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** Shared visual language for Reality Engine V4. */
object RealityVisuals {
    const val HUD_OWNED_TAG = "realityengine.hud.owned"

    object Colors {
        val Background: Int = Color.rgb(1, 4, 9)
        val BackgroundRaised: Int = Color.rgb(3, 10, 18)
        val Panel: Int = Color.rgb(3, 14, 26)
        val PanelStrong: Int = Color.rgb(4, 22, 40)
        val Cyan: Int = Color.rgb(0, 225, 255)
        val CyanSoft: Int = Color.rgb(93, 191, 232)
        val Magenta: Int = Color.rgb(255, 28, 193)
        val Green: Int = Color.rgb(32, 255, 103)
        val Amber: Int = Color.rgb(255, 196, 88)
        val Text: Int = Color.rgb(244, 252, 255)
        val TextDim: Int = Color.rgb(112, 151, 184)
        val Border: Int = Color.rgb(0, 116, 154)
        val Track: Int = Color.rgb(8, 34, 49)
        val DangerFill: Int = Color.rgb(38, 5, 25)
    }

    fun panel(
        context: Context,
        fill: Int = Colors.Panel,
        stroke: Int = Colors.Border,
        radiusDp: Float = 12f,
        strokeDp: Int = 1,
    ): Drawable = HudPlateDrawable(
        density = context.resources.displayMetrics.density,
        fill = fill,
        stroke = stroke,
        cutDp = radiusDp.coerceIn(5f, 22f),
        strokeDp = strokeDp.coerceAtLeast(1),
    )

    fun circle(
        context: Context,
        fill: Int = Colors.PanelStrong,
        stroke: Int = Colors.Cyan,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.argb(245, Color.red(fill), Color.green(fill), Color.blue(fill)))
        setStroke(dp(context, 2), stroke)
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
            letterSpacing = .08f
            setTextColor(accent)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            background = panel(
                context,
                fill = if (destructive) Colors.DangerFill else Colors.Panel,
                stroke = accent,
                radiusDp = radiusDp,
                strokeDp = 2,
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
            textSize = 9f
            letterSpacing = .16f
            setTextColor(accent)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
        }
    }

    fun styleSignal(bar: ProgressBar, accent: Int) {
        bar.max = 100
        bar.progressTintList = ColorStateList.valueOf(accent)
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(7, 32, 45))
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
            .alpha(.64f)
            .scaleX(.985f)
            .scaleY(.985f)
            .setDuration(85L)
            .withEndAction {
                view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(145L).start()
            }
            .start()
    }

    private class HudPlateDrawable(
        private val density: Float,
        private val fill: Int,
        private val stroke: Int,
        cutDp: Float,
        strokeDp: Int,
    ) : Drawable() {
        private val cutPx = cutDp * density
        private val strokePx = strokeDp * density
        private val outerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.5f * density
            color = Color.argb(45, Color.red(stroke), Color.green(stroke), Color.blue(stroke))
        }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx.coerceAtLeast(1.4f * density)
            color = Color.argb(255, Color.red(stroke), Color.green(stroke), Color.blue(stroke))
        }
        private val innerEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
            color = Color.argb(165, 118, 229, 255)
        }
        private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(235, 130, 247, 255)
        }
        private val texture = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(60, Color.red(stroke), Color.green(stroke), Color.blue(stroke))
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var drawableAlpha = 255

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val rect = RectF(bounds)
            val c = min(cutPx, min(rect.width(), rect.height()) * .18f)
            val path = beveled(rect, c)

            val top = lighten(fill, 1.45f)
            val bottom = darken(fill, .58f)
            fillPaint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.left,
                rect.bottom,
                intArrayOf(top, fill, bottom),
                floatArrayOf(0f, .42f, 1f),
                Shader.TileMode.CLAMP,
            )
            fillPaint.alpha = drawableAlpha
            canvas.drawPath(path, fillPaint)
            fillPaint.shader = null

            outerGlow.alpha = (drawableAlpha * .34f).toInt()
            canvas.drawPath(path, outerGlow)

            edge.alpha = drawableAlpha
            canvas.drawPath(path, edge)

            val inner = RectF(rect).apply { inset(4f * density, 4f * density) }
            val innerPath = beveled(inner, max(2f * density, c - 4f * density))
            innerEdge.alpha = (drawableAlpha * .82f).toInt()
            canvas.drawPath(innerPath, innerEdge)

            val y = rect.top + 4.5f * density
            val segment = rect.width() * .22f
            val x = rect.centerX() - segment / 2f
            highlight.alpha = drawableAlpha
            canvas.drawLine(x, y, x + segment, y, highlight)

            val dotY = rect.bottom - 7f * density
            val startX = rect.left + c + 5f * density
            val cols = 8
            repeat(cols) { i ->
                val dx = startX + i * 5.2f * density
                val row = i % 2
                canvas.drawCircle(dx, dotY - row * 3.1f * density, .85f * density, texture)
                canvas.drawCircle(rect.right - (dx - rect.left), dotY - row * 3.1f * density, .85f * density, texture)
            }
        }

        private fun beveled(rect: RectF, c: Float): Path = Path().apply {
            moveTo(rect.left + c, rect.top)
            lineTo(rect.right - c, rect.top)
            lineTo(rect.right, rect.top + c)
            lineTo(rect.right, rect.bottom - c)
            lineTo(rect.right - c, rect.bottom)
            lineTo(rect.left + c, rect.bottom)
            lineTo(rect.left, rect.bottom - c)
            lineTo(rect.left, rect.top + c)
            close()
        }

        override fun setAlpha(alpha: Int) {
            drawableAlpha = alpha.coerceIn(0, 255)
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun lighten(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255),
    )

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255),
    )

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

package com.realityengine.v4

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.View
import android.widget.Button
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** Visual primitives for the selected Pulse Deck active-call interface. */
object PulseDeckVisuals {
    object Colors {
        val Background = Color.rgb(2, 11, 16)
        val BackgroundRaised = Color.rgb(7, 20, 27)
        val PanelTop = Color.rgb(17, 35, 44)
        val PanelBottom = Color.rgb(8, 22, 29)
        val PanelSoft = Color.rgb(20, 42, 52)
        val Border = Color.rgb(40, 67, 79)
        val Cyan = Color.rgb(27, 230, 240)
        val CyanDim = Color.rgb(85, 184, 204)
        val Lime = Color.rgb(139, 249, 58)
        val Green = Color.rgb(48, 237, 120)
        val Amber = Color.rgb(255, 183, 12)
        val Coral = Color.rgb(255, 62, 77)
        val Text = Color.rgb(247, 250, 252)
        val TextDim = Color.rgb(142, 164, 179)
        val Track = Color.rgb(28, 48, 57)
    }

    fun backdrop(): Drawable = PulseDeckBackdropDrawable()

    fun panel(
        context: Context,
        start: Int = Colors.PanelTop,
        end: Int = Colors.PanelBottom,
        stroke: Int = Colors.Border,
        radiusDp: Float = 18f,
        strokeDp: Int = 1,
    ): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(start, end),
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * context.resources.displayMetrics.density
        setStroke(dp(context, strokeDp), stroke)
    }

    fun circle(
        context: Context,
        start: Int,
        end: Int,
        stroke: Int,
        strokeDp: Int = 1,
    ): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(start, end),
    ).apply {
        shape = GradientDrawable.OVAL
        setStroke(dp(context, strokeDp), stroke)
    }

    fun styleCallControl(
        button: Button,
        iconRes: Int,
        accent: Int = Colors.Text,
        selected: Boolean = false,
        destructive: Boolean = false,
        circular: Boolean = false,
    ) {
        val context = button.context
        button.apply {
            textSize = 10.5f
            letterSpacing = .045f
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAllCaps = true
            gravity = android.view.Gravity.CENTER
            stateListAnimator = null
            elevation = 0f
            minWidth = 0
            minHeight = 0
            setPadding(dp(context, 5), dp(context, 8), dp(context, 5), dp(context, 7))
            if (iconRes != 0) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, iconRes, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(accent)
                compoundDrawablePadding = dp(context, 5)
            }
            background = when {
                destructive -> circle(
                    context,
                    start = Color.rgb(255, 72, 83),
                    end = Color.rgb(205, 28, 43),
                    stroke = Color.rgb(255, 91, 101),
                    strokeDp = 1,
                )
                circular -> circle(
                    context,
                    start = if (selected) Color.rgb(10, 59, 70) else Colors.PanelSoft,
                    end = Colors.PanelBottom,
                    stroke = if (selected) accent else Colors.Border,
                )
                else -> panel(
                    context,
                    start = if (selected) Color.rgb(11, 51, 60) else Colors.PanelTop,
                    end = if (selected) Color.rgb(7, 31, 38) else Colors.PanelBottom,
                    stroke = if (selected) accent else Colors.Border,
                    radiusDp = 16f,
                    strokeDp = if (selected) 2 else 1,
                )
            }
        }
    }

    fun styleUtilityControl(button: Button, iconRes: Int) {
        val context = button.context
        button.apply {
            textSize = 8.8f
            letterSpacing = .055f
            setTextColor(Colors.Text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAllCaps = true
            gravity = android.view.Gravity.CENTER
            stateListAnimator = null
            elevation = 0f
            minWidth = 0
            minHeight = 0
            setPadding(dp(context, 5), 0, dp(context, 5), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(Colors.Text)
            compoundDrawablePadding = dp(context, 5)
            background = panel(context, radiusDp = 13f)
        }
    }

    fun chip(context: Context, accent: Int): Drawable = panel(
        context = context,
        start = Color.rgb(8, 27, 33),
        end = Color.rgb(5, 20, 26),
        stroke = accent,
        radiusDp = 22f,
        strokeDp = 1,
    )

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private class PulseDeckBackdropDrawable : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var drawableAlpha = 255

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val width = bounds.width().toFloat().coerceAtLeast(1f)
            val height = bounds.height().toFloat().coerceAtLeast(1f)
            paint.shader = LinearGradient(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                intArrayOf(Color.rgb(4, 17, 23), Colors.Background, Color.rgb(1, 8, 12)),
                floatArrayOf(0f, .54f, 1f),
                Shader.TileMode.CLAMP,
            )
            paint.alpha = drawableAlpha
            canvas.drawRect(bounds, paint)

            paint.shader = RadialGradient(
                bounds.left + width * .08f,
                bounds.top + height * .08f,
                max(width, height) * .55f,
                intArrayOf(Color.argb(30, 20, 213, 229), Color.TRANSPARENT),
                null,
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
    }
}

/** Five-bar cyan Pulse Deck brand mark. */
class PulseDeckMarkView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PulseDeckVisuals.Colors.Cyan
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val heights = floatArrayOf(.32f, .66f, 1f, .66f, .32f)
        val gap = width / 6f
        paint.strokeWidth = (width * .105f).coerceAtLeast(resources.displayMetrics.density * 1.4f)
        heights.forEachIndexed { index, scale ->
            val x = gap * (index + 1)
            val half = height * .34f * scale
            canvas.drawLine(x, centerY - half, x, centerY + half, paint)
        }
    }
}

/** Caller activity waveform driven by the current transcript activity and acoustic signal. */
class PulseDeckWaveformView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PulseDeckVisuals.Colors.Cyan
        strokeCap = Paint.Cap.ROUND
    }
    private var active = false
    private var intensity = 0
    private var seed = 0L

    fun render(isActive: Boolean, acoustic: Int, timestampMs: Long) {
        active = isActive
        intensity = acoustic.coerceIn(0, 100)
        seed = timestampMs
        contentDescription = if (active) "Caller audio activity" else "Most recent caller audio"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val centerY = height / 2f
        val count = 43
        val gap = width / (count + 1f)
        val phase = if (active) SystemClock.uptimeMillis() / 145f else (seed % 997L) / 57f
        val level = .42f + intensity / 172f
        paint.strokeWidth = (resources.displayMetrics.density * 1.7f).coerceAtMost(gap * .52f)
        for (index in 0 until count) {
            val x = gap * (index + 1)
            val envelope = .26f + .74f * sin(Math.PI * (index + 1) / (count + 1)).toFloat()
            val wave = abs(sin((index * .73f + phase).toDouble()).toFloat())
            val half = (height * (.08f + .36f * wave * envelope * level)).coerceAtLeast(resources.displayMetrics.density * 1.3f)
            paint.alpha = (150 + 105 * envelope).toInt().coerceIn(0, 255)
            canvas.drawLine(x, centerY - half, x, centerY + half, paint)
        }
        paint.alpha = 255
        if (active) postInvalidateDelayed(72L)
    }
}

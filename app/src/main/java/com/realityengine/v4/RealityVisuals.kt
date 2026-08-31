package com.realityengine.v4

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
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

/** Shared premium Lucid Prism visual language for Reality Engine V4. */
object RealityVisuals {
    const val HUD_OWNED_TAG = "realityengine.hud.owned"

    object Colors {
        val Background: Int = Color.rgb(3, 7, 16)
        val BackgroundRaised: Int = Color.rgb(7, 12, 24)
        val Panel: Int = Color.rgb(11, 17, 31)
        val PanelStrong: Int = Color.rgb(16, 23, 41)
        val Cyan: Int = Color.rgb(151, 208, 255)
        val CyanSoft: Int = Color.rgb(177, 203, 237)
        val Lilac: Int = Color.rgb(181, 164, 255)
        val Magenta: Int = Lilac
        val Green: Int = Color.rgb(102, 232, 147)
        val Amber: Int = Color.rgb(241, 190, 111)
        val Text: Int = Color.rgb(247, 249, 255)
        val TextDim: Int = Color.rgb(191, 203, 226)
        val Border: Int = Color.rgb(62, 76, 110)
        val Track: Int = Color.rgb(20, 28, 47)
        val DangerFill: Int = Color.rgb(39, 16, 28)
    }

    fun panel(
        context: Context,
        fill: Int = Colors.Panel,
        stroke: Int = Colors.Border,
        radiusDp: Float = 20f,
        strokeDp: Int = 1,
    ): Drawable = PrismGlassDrawable(
        density = context.resources.displayMetrics.density,
        fill = fill,
        stroke = stroke,
        radiusDp = radiusDp.coerceIn(8f, 34f),
        strokeDp = strokeDp.coerceAtLeast(1),
    )

    fun circle(
        context: Context,
        fill: Int = Colors.PanelStrong,
        stroke: Int = Colors.Cyan,
    ): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            withAlpha(lighten(fill, 1.12f), 244),
            withAlpha(fill, 238),
            withAlpha(darken(fill, .78f), 246),
        ),
    ).apply {
        shape = GradientDrawable.OVAL
        setStroke(dp(context, 1), withAlpha(stroke, 170))
    }

    fun styleControl(
        button: Button,
        iconRes: Int,
        accent: Int = Colors.Cyan,
        destructive: Boolean = false,
        radiusDp: Float = 20f,
    ) {
        button.apply {
            textSize = 11f
            letterSpacing = .018f
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = panel(
                context,
                fill = if (destructive) Colors.DangerFill else Colors.Panel,
                stroke = if (destructive) accent else mix(accent, Colors.Border, .28f),
                radiusDp = radiusDp,
                strokeDp = 1,
            )
            stateListAnimator = null
            elevation = 0f
            setAllCaps(false)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(context, 12), 0, dp(context, 12), 0)
            if (iconRes != 0) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(accent)
                compoundDrawablePadding = dp(context, 7)
            }
        }
    }

    fun styleMicroLabel(view: TextView, accent: Int = Colors.TextDim) {
        view.apply {
            textSize = 9.5f
            letterSpacing = .06f
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }
    }

    fun styleSignal(bar: ProgressBar, accent: Int) {
        bar.max = 100
        bar.progressTintList = ColorStateList.valueOf(accent)
        bar.progressBackgroundTintList = ColorStateList.valueOf(Colors.Track)
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
        view.alpha = .84f
        view.scaleX = .996f
        view.scaleY = .996f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun pulseOnce(view: View) {
        view.animate().cancel()
        view.animate()
            .alpha(.76f)
            .scaleX(.976f)
            .scaleY(.976f)
            .setDuration(72L)
            .withEndAction {
                view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(145L).start()
            }
            .start()
    }

    private class PrismGlassDrawable(
        private val density: Float,
        private val fill: Int,
        private val stroke: Int,
        radiusDp: Float,
        strokeDp: Int,
    ) : Drawable() {
        private val radiusPx = radiusDp * density
        private val strokePx = max(.75f * density, strokeDp * density)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
        }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = .55f * density
        }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = .85f * density
            strokeCap = Paint.Cap.ROUND
        }
        private var drawableAlpha = 255

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val inset = 1.35f * density
            val rect = RectF(bounds).apply { inset(inset, inset) }
            val radius = radiusPx.coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
            val brightFill = isBright(fill)

            fillPaint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.left,
                rect.bottom,
                intArrayOf(
                    withAlpha(lighten(fill, if (brightFill) 1.06f else 1.18f), if (brightFill) 248 else 236),
                    withAlpha(fill, if (brightFill) 246 else 232),
                    withAlpha(darken(fill, if (brightFill) .90f else .82f), if (brightFill) 250 else 240),
                ),
                floatArrayOf(0f, .42f, 1f),
                Shader.TileMode.CLAMP,
            )
            fillPaint.alpha = drawableAlpha
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            fillPaint.shader = null

            washPaint.shader = LinearGradient(
                rect.left,
                rect.bottom,
                rect.right,
                rect.top,
                intArrayOf(
                    Color.TRANSPARENT,
                    withAlpha(Colors.Cyan, if (brightFill) 7 else 13),
                    withAlpha(Colors.Lilac, if (brightFill) 9 else 17),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, .32f, .70f, 1f),
                Shader.TileMode.CLAMP,
            )
            washPaint.alpha = drawableAlpha
            canvas.drawRoundRect(rect, radius, radius, washPaint)
            washPaint.shader = null

            borderPaint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    withAlpha(stroke, 145),
                    withAlpha(Colors.CyanSoft, 76),
                    withAlpha(Colors.Lilac, 102),
                ),
                floatArrayOf(0f, .56f, 1f),
                Shader.TileMode.CLAMP,
            )
            borderPaint.alpha = drawableAlpha
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
            borderPaint.shader = null

            val inner = RectF(rect).apply { inset(2.3f * density, 2.3f * density) }
            val innerRadius = (radius - 2.3f * density).coerceAtLeast(3f * density)
            innerPaint.color = withAlpha(Color.WHITE, if (brightFill) 30 else 16)
            innerPaint.alpha = drawableAlpha
            canvas.drawRoundRect(inner, innerRadius, innerRadius, innerPaint)

            val y = rect.top + 3.1f * density
            val span = rect.width() * .27f
            val start = rect.left + rect.width() * .13f
            highlightPaint.shader = LinearGradient(
                start,
                y,
                start + span,
                y,
                intArrayOf(Color.TRANSPARENT, withAlpha(Color.WHITE, 80), withAlpha(Colors.CyanSoft, 56), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
            highlightPaint.alpha = drawableAlpha
            canvas.drawLine(start, y, start + span, y, highlightPaint)
            highlightPaint.shader = null
        }

        override fun setAlpha(alpha: Int) {
            drawableAlpha = alpha.coerceIn(0, 255)
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            washPaint.colorFilter = colorFilter
            borderPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

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

    private fun mix(a: Int, b: Int, amountA: Float): Int {
        val t = amountA.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * t + Color.red(b) * (1f - t)).toInt(),
            (Color.green(a) * t + Color.green(b) * (1f - t)).toInt(),
            (Color.blue(a) * t + Color.blue(b) * (1f - t)).toInt(),
        )
    }

    private fun isBright(color: Int): Boolean {
        val peak = max(Color.red(color), max(Color.green(color), Color.blue(color)))
        return peak > 185 && Color.red(color) + Color.green(color) + Color.blue(color) > 330
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

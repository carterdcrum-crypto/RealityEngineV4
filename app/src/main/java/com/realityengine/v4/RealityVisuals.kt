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

/**
 * Shared Lucid Prism visual language for Reality Engine V4.
 *
 * The functional screens still own behavior. Keeping the visual system here means the same
 * midnight glass, blue/lilac edge light, spacing and motion automatically carries through the
 * dialer, contacts, call UI, settings, memory, transcripts and post-call surfaces.
 */
object RealityVisuals {
    const val HUD_OWNED_TAG = "realityengine.hud.owned"

    object Colors {
        val Background: Int = Color.rgb(2, 6, 16)
        val BackgroundRaised: Int = Color.rgb(7, 13, 28)
        val Panel: Int = Color.rgb(10, 18, 36)
        val PanelStrong: Int = Color.rgb(14, 25, 49)
        val Cyan: Int = Color.rgb(142, 205, 255)
        val CyanSoft: Int = Color.rgb(168, 196, 238)
        val Lilac: Int = Color.rgb(188, 161, 255)
        // Preserve the old semantic name so existing screens inherit the prism accent safely.
        val Magenta: Int = Lilac
        val Green: Int = Color.rgb(99, 244, 142)
        val Amber: Int = Color.rgb(247, 193, 111)
        val Text: Int = Color.rgb(246, 248, 255)
        val TextDim: Int = Color.rgb(151, 164, 195)
        val Border: Int = Color.rgb(83, 111, 164)
        val Track: Int = Color.rgb(18, 31, 56)
        val DangerFill: Int = Color.rgb(42, 13, 27)
    }

    fun panel(
        context: Context,
        fill: Int = Colors.Panel,
        stroke: Int = Colors.Border,
        radiusDp: Float = 16f,
        strokeDp: Int = 1,
    ): Drawable = PrismGlassDrawable(
        density = context.resources.displayMetrics.density,
        fill = fill,
        stroke = stroke,
        radiusDp = radiusDp.coerceIn(7f, 30f),
        strokeDp = strokeDp.coerceAtLeast(1),
    )

    fun circle(
        context: Context,
        fill: Int = Colors.PanelStrong,
        stroke: Int = Colors.Cyan,
    ): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            withAlpha(lighten(fill, 1.24f), 242),
            withAlpha(fill, 232),
            withAlpha(darken(fill, .72f), 242),
        ),
    ).apply {
        shape = GradientDrawable.OVAL
        setStroke(dp(context, 1), withAlpha(stroke, 218))
    }

    fun styleControl(
        button: Button,
        iconRes: Int,
        accent: Int = Colors.Cyan,
        destructive: Boolean = false,
        radiusDp: Float = 17f,
    ) {
        button.apply {
            textSize = 10.5f
            letterSpacing = .055f
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = panel(
                context,
                fill = if (destructive) Colors.DangerFill else Colors.Panel,
                stroke = accent,
                radiusDp = radiusDp,
                strokeDp = 1,
            )
            stateListAnimator = null
            elevation = 0f
            setAllCaps(false)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(context, 11), 0, dp(context, 11), 0)
            if (iconRes != 0) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(accent)
                compoundDrawablePadding = dp(context, 7)
            }
        }
    }

    fun styleMicroLabel(view: TextView, accent: Int = Colors.TextDim) {
        view.apply {
            textSize = 9f
            letterSpacing = .115f
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
            duration = 280L
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    fun reveal(view: View) {
        view.animate().cancel()
        view.alpha = .78f
        view.scaleX = .992f
        view.scaleY = .992f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun pulseOnce(view: View) {
        view.animate().cancel()
        view.animate()
            .alpha(.78f)
            .scaleX(.985f)
            .scaleY(.985f)
            .setDuration(85L)
            .withEndAction {
                view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170L).start()
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
        private val strokePx = max(1f * density, strokeDp * density)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val prismWash = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4.2f * density
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
        }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = .75f * density
        }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f * density
            strokeCap = Paint.Cap.ROUND
        }
        private var drawableAlpha = 255

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val outerInset = 2.4f * density
            val rect = RectF(bounds).apply { inset(outerInset, outerInset) }
            val radius = radiusPx.coerceAtMost(minOf(rect.width(), rect.height()) / 2f)

            val brightFill = isBright(fill)
            val top = withAlpha(lighten(fill, if (brightFill) 1.12f else 1.55f), if (brightFill) 246 else 228)
            val middle = withAlpha(fill, if (brightFill) 244 else 216)
            val bottom = withAlpha(darken(fill, if (brightFill) .82f else .68f), if (brightFill) 248 else 232)
            fillPaint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(top, middle, bottom),
                floatArrayOf(0f, .46f, 1f),
                Shader.TileMode.CLAMP,
            )
            fillPaint.alpha = drawableAlpha
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            fillPaint.shader = null

            // Very low-alpha blue-to-lilac wash creates the iridescent prism shift without making
            // controls look like neon signs.
            prismWash.shader = LinearGradient(
                rect.left,
                rect.bottom,
                rect.right,
                rect.top,
                intArrayOf(
                    Color.TRANSPARENT,
                    withAlpha(Colors.Cyan, if (brightFill) 12 else 24),
                    withAlpha(Colors.Lilac, if (brightFill) 15 else 31),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, .30f, .72f, 1f),
                Shader.TileMode.CLAMP,
            )
            prismWash.alpha = drawableAlpha
            canvas.drawRoundRect(rect, radius, radius, prismWash)
            prismWash.shader = null

            glowPaint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(withAlpha(stroke, 58), withAlpha(Colors.Lilac, 48), withAlpha(Colors.Cyan, 54)),
                null,
                Shader.TileMode.CLAMP,
            )
            glowPaint.alpha = (drawableAlpha * .72f).toInt()
            canvas.drawRoundRect(rect, radius, radius, glowPaint)
            glowPaint.shader = null

            borderPaint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(withAlpha(stroke, 222), withAlpha(Colors.CyanSoft, 184), withAlpha(Colors.Lilac, 210)),
                floatArrayOf(0f, .52f, 1f),
                Shader.TileMode.CLAMP,
            )
            borderPaint.alpha = drawableAlpha
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
            borderPaint.shader = null

            val inner = RectF(rect).apply { inset(3.2f * density, 3.2f * density) }
            val innerRadius = (radius - 3.2f * density).coerceAtLeast(3f * density)
            innerPaint.color = withAlpha(Color.WHITE, if (brightFill) 46 else 30)
            innerPaint.alpha = drawableAlpha
            canvas.drawRoundRect(inner, innerRadius, innerRadius, innerPaint)

            // A short glass reflection along the upper edge gives controls the polished Lucid Prism
            // look while keeping the center clean for text and icons.
            val y = rect.top + 4.2f * density
            val span = rect.width() * .38f
            val start = rect.centerX() - span / 2f
            highlightPaint.shader = LinearGradient(
                start,
                y,
                start + span,
                y,
                intArrayOf(Color.TRANSPARENT, withAlpha(Color.WHITE, 150), withAlpha(Colors.Lilac, 112), Color.TRANSPARENT),
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
            prismWash.colorFilter = colorFilter
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

    private fun isBright(color: Int): Boolean {
        val peak = max(Color.red(color), max(Color.green(color), Color.blue(color)))
        return peak > 185 && Color.red(color) + Color.green(color) + Color.blue(color) > 330
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

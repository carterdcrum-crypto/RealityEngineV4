package com.realityengine.v4

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

/**
 * Pulse Deck Fused Core signal instrument.
 *
 * Presentation only: values still come directly from LiveSignalState. This view does not calculate,
 * threshold, publish, or otherwise change acoustic, linguistic, factual, fused, haptic, transcript,
 * or call behavior.
 */
class LiveSignalVisualView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var signal = LiveSignalState.State()

    init {
        tag = RealityVisuals.HUD_OWNED_TAG
        isClickable = true
        isFocusable = true
        contentDescription = "Fused conversation signal with acoustic, linguistic, and factual satellites"
    }

    fun render(
        signalState: LiveSignalState.State,
        transcriptState: LiveTranscriptState.State,
        insightState: ConversationInsightSnapshot,
    ) {
        // Keep the existing render contract intact. The extra states remain accepted because the
        // surrounding signal system still owns them; Fused Core only needs the already-computed scores.
        signal = signalState
        contentDescription = buildString {
            append("Fused signal ").append(signal.combined).append(" percent. ")
            append("Acoustic ").append(signal.acoustic).append(". ")
            append("Linguistic ").append(signal.linguistic).append(". ")
            append("Factual ").append(signal.factual).append(".")
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val expanded = height >= dp(132)
        val outer = RectF(
            0.5f * density,
            0.5f * density,
            width - 0.5f * density,
            height - 0.5f * density,
        )
        drawGlass(canvas, outer)

        if (expanded) drawExpanded(canvas) else drawCompact(canvas)
    }

    private fun drawCompact(canvas: Canvas) {
        val centerX = width / 2f
        val coreY = dpF(34f)
        val coreRadius = minOf(dpF(25f), height * .29f)

        drawSectionLabel(canvas, "FUSED CORE", centerX, dpF(12f))
        drawCore(canvas, centerX, coreY, coreRadius, signal.combined, compact = true)

        val satelliteY = height - dpF(12.5f)
        val span = width * .62f
        val left = centerX - span / 2f
        val step = span / 2f
        drawSatellite(canvas, left, satelliteY, "A", signal.acoustic, PulseDeckVisuals.Colors.Cyan, compact = true)
        drawSatellite(canvas, left + step, satelliteY, "L", signal.linguistic, PulseDeckVisuals.Colors.Amber, compact = true)
        drawSatellite(canvas, left + step * 2f, satelliteY, "F", signal.factual, PulseDeckVisuals.Colors.Green, compact = true)
    }

    private fun drawExpanded(canvas: Canvas) {
        val centerX = width / 2f
        val coreY = height * .39f
        val coreRadius = minOf(dpF(45f), height * .28f, width * .19f)

        drawSectionLabel(canvas, "FUSED CONVERSATION STATE", centerX, dpF(18f))
        drawCore(canvas, centerX, coreY, coreRadius, signal.combined, compact = false)

        val satelliteY = height - dpF(27f)
        val span = width * .64f
        val left = centerX - span / 2f
        val step = span / 2f
        drawSatellite(canvas, left, satelliteY, "ACOUSTIC", signal.acoustic, PulseDeckVisuals.Colors.Cyan, compact = false)
        drawSatellite(canvas, left + step, satelliteY, "LINGUISTIC", signal.linguistic, PulseDeckVisuals.Colors.Amber, compact = false)
        drawSatellite(canvas, left + step * 2f, satelliteY, "FACTUAL", signal.factual, PulseDeckVisuals.Colors.Green, compact = false)
    }

    private fun drawCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        score: Int,
        compact: Boolean,
    ) {
        val clamped = score.coerceIn(0, 100)
        val accent = fusedAccent(clamped)
        val ringWidth = dpF(if (compact) 4.2f else 6f)
        val haloRadius = radius + dpF(if (compact) 5f else 8f)

        fill.color = Color.argb(
            (18 + clamped * .24f).toInt().coerceIn(18, 46),
            Color.red(accent),
            Color.green(accent),
            Color.blue(accent),
        )
        canvas.drawCircle(cx, cy, haloRadius, fill)

        line.style = Paint.Style.STROKE
        line.strokeWidth = ringWidth
        line.color = Color.argb(90, 57, 79, 88)
        canvas.drawCircle(cx, cy, radius, line)

        val ring = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        line.strokeWidth = ringWidth
        line.color = accent
        canvas.drawArc(ring, -90f, 360f * (clamped / 100f), false, line)

        line.strokeWidth = dpF(1f)
        line.color = Color.argb(95, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, cy, radius - ringWidth * 1.15f, line)

        text.shader = null
        text.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        text.textAlign = Paint.Align.CENTER
        text.color = PulseDeckVisuals.Colors.Text
        text.textSize = dpF(if (compact) 16f else 27f)
        canvas.drawText("$clamped", cx, cy + dpF(if (compact) 5.5f else 8f), text)

        if (!compact) {
            text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            text.textSize = dpF(7.5f)
            text.letterSpacingCompat(.10f)
            text.color = Color.argb(205, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawText(fusedStatus(clamped), cx, cy + radius + dpF(16f), text)
            text.letterSpacingCompat(0f)
        }
    }

    private fun drawSatellite(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        label: String,
        score: Int,
        accent: Int,
        compact: Boolean,
    ) {
        val clamped = score.coerceIn(0, 100)
        val radius = dpF(if (compact) 6.5f else 11f)
        val ringWidth = dpF(if (compact) 1.7f else 2.3f)
        val ring = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        line.strokeWidth = ringWidth
        line.color = Color.argb(80, 74, 91, 98)
        canvas.drawCircle(cx, cy, radius, line)
        line.color = accent
        canvas.drawArc(ring, -90f, 360f * (clamped / 100f), false, line)

        if (compact) {
            text.textAlign = Paint.Align.CENTER
            text.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            text.textSize = dpF(6.3f)
            text.color = PulseDeckVisuals.Colors.Text
            canvas.drawText(label, cx, cy + dpF(2.2f), text)

            text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            text.textSize = dpF(7f)
            text.color = Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawText("$clamped", cx + dpF(14f), cy + dpF(2.5f), text)
        } else {
            text.textAlign = Paint.Align.CENTER
            text.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            text.textSize = dpF(8f)
            text.color = PulseDeckVisuals.Colors.Text
            canvas.drawText("$clamped", cx, cy + dpF(2.8f), text)

            text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            text.textSize = dpF(7.2f)
            text.letterSpacingCompat(.07f)
            text.color = Color.argb(205, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawText(label, cx, cy + radius + dpF(12f), text)
            text.letterSpacingCompat(0f)
        }
    }

    private fun drawSectionLabel(canvas: Canvas, label: String, cx: Float, baseline: Float) {
        text.shader = null
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text.textSize = dpF(7.6f)
        text.letterSpacingCompat(.12f)
        text.color = PulseDeckVisuals.Colors.CyanDim
        canvas.drawText(label, cx, baseline, text)
        text.letterSpacingCompat(0f)
    }

    private fun drawGlass(canvas: Canvas, rect: RectF) {
        val radius = dpF(18f)
        fill.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                Color.rgb(10, 28, 35),
                Color.rgb(5, 18, 24),
                Color.rgb(3, 13, 18),
            ),
            floatArrayOf(0f, .52f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null

        line.strokeWidth = max(1f, density * .8f)
        line.color = Color.argb(145, 40, 92, 104)
        canvas.drawRoundRect(rect, radius, radius, line)

        val topLine = RectF(rect).apply { inset(dpF(2f), dpF(2f)) }
        line.strokeWidth = dpF(.7f)
        line.color = Color.argb(40, 27, 230, 240)
        canvas.drawRoundRect(topLine, radius - dpF(2f), radius - dpF(2f), line)
    }

    private fun fusedAccent(score: Int): Int = when {
        score >= 70 -> PulseDeckVisuals.Colors.Coral
        score >= 42 -> PulseDeckVisuals.Colors.Amber
        else -> PulseDeckVisuals.Colors.Cyan
    }

    private fun fusedStatus(score: Int): String = when {
        score >= 70 -> "HIGH SIGNAL"
        score >= 42 -> "ELEVATED"
        else -> "STABLE"
    }

    private fun Paint.letterSpacingCompat(value: Float) {
        // Canvas Paint has no public letterSpacing property; approximate the visual hierarchy with
        // typography size/weight while keeping this helper as a no-op for API stability.
    }

    private fun dp(value: Int): Int = (value * density).toInt()
    private fun dpF(value: Float): Float = value * density
}

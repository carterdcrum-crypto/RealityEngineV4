package com.realityengine.v4

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import java.util.ArrayDeque
import kotlin.math.max

/**
 * Lucid Prism live signal instrument selected for the Conversation OS call screen.
 *
 * Every visible state is driven by live evidence already produced by V4. The acoustic lane is a
 * rolling history of the real acoustic-change score (not a fabricated frequency spectrum), the
 * linguistic nodes come from the current caller transcript, and factual highlights only elevate
 * when the consistency engine has actual evidence to review.
 */
class LiveSignalVisualView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    private val acousticHistory = ArrayDeque<Int>()
    private val factualHistory = ArrayDeque<Int>()
    private var signal = LiveSignalState.State()
    private var transcript = LiveTranscriptState.State()
    private var insight = ConversationInsightSnapshot()
    private var linguistic = LinguisticSignalAnalyzer.Result(0, emptyList())
    private var lastSignalAt = -1L
    private var lastTranscriptAt = -1L

    init {
        tag = RealityVisuals.HUD_OWNED_TAG
        isClickable = true
        isFocusable = true
        contentDescription = "Live acoustic, linguistic and factual signal visualizations"
    }

    fun render(
        signalState: LiveSignalState.State,
        transcriptState: LiveTranscriptState.State,
        insightState: ConversationInsightSnapshot,
    ) {
        signal = signalState
        transcript = transcriptState
        insight = insightState

        if (signalState.updatedAtMs != lastSignalAt) {
            lastSignalAt = signalState.updatedAtMs
            push(acousticHistory, signalState.acoustic, 52)
            push(factualHistory, signalState.factual, 18)
        }
        if (transcriptState.updatedAtMs != lastTranscriptAt) {
            lastTranscriptAt = transcriptState.updatedAtMs
            linguistic = if (transcriptState.isCaller != false) {
                LinguisticSignalAnalyzer.analyze(transcriptState.text)
            } else {
                LinguisticSignalAnalyzer.Result(signalState.linguistic, emptyList())
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val expanded = height >= dp(132)
        val outer = RectF(0.5f * density, 0.5f * density, width - 0.5f * density, height - 0.5f * density)
        drawGlass(canvas, outer)

        val pad = dp(if (expanded) 12 else 9)
        val contentLeft = pad
        val contentRight = width - pad
        val laneTop = dp(if (expanded) 11 else 6)
        val laneGap = dp(if (expanded) 5 else 2)
        val usable = height - laneTop - dp(if (expanded) 9 else 5) - laneGap * 2
        val laneH = usable / 3f

        drawAcoustic(canvas, contentLeft, contentRight, laneTop.toFloat(), laneH, expanded)
        drawDivider(canvas, contentLeft, contentRight, laneTop + laneH + laneGap / 2f)
        drawLinguistic(canvas, contentLeft, contentRight, laneTop + laneH + laneGap, laneH, expanded)
        drawDivider(canvas, contentLeft, contentRight, laneTop + laneH * 2f + laneGap * 1.5f)
        drawFactual(canvas, contentLeft, contentRight, laneTop + laneH * 2f + laneGap * 2f, laneH, expanded)
    }

    private fun drawGlass(canvas: Canvas, rect: RectF) {
        val radius = dp(18).toFloat()
        fill.shader = LinearGradient(
            0f, rect.top, rect.right, rect.bottom,
            intArrayOf(Color.rgb(8, 17, 32), Color.rgb(8, 14, 27), Color.rgb(12, 12, 30)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null
        line.strokeWidth = max(1f, density * .75f)
        line.color = Color.argb(105, 126, 155, 255)
        canvas.drawRoundRect(rect, radius, radius, line)
    }

    private fun drawAcoustic(canvas: Canvas, left: Int, right: Int, top: Float, laneH: Float, expanded: Boolean) {
        val cyan = RealityVisuals.Colors.CyanSoft
        val labelW = dp(if (expanded) 102 else 80)
        drawLabel(canvas, "ACOUSTIC", signal.acoustic, left.toFloat(), top + dp(if (expanded) 17 else 13), cyan, expanded)
        if (expanded) drawSub(canvas, "LIVE PULSE", left.toFloat(), top + dp(32), cyan)

        val x0 = left + labelW
        val yMid = top + laneH * .57f
        val graphH = laneH * if (expanded) .64f else .72f
        val values = acousticHistory.toList().ifEmpty { listOf(signal.acoustic) }
        val count = values.size.coerceAtLeast(1)
        val step = (right - x0).toFloat() / count

        fill.shader = LinearGradient(x0.toFloat(), 0f, right.toFloat(), 0f,
            intArrayOf(Color.argb(120, 72, 191, 255), Color.argb(240, 76, 229, 255), Color.argb(150, 140, 126, 255)),
            null, Shader.TileMode.CLAMP)
        values.forEachIndexed { index, value ->
            val normalized = (value.coerceIn(0, 100) / 100f)
            val h = dp(2) + graphH * normalized * .52f
            val x = x0 + index * step + step * .28f
            val w = max(1.2f * density, step * .42f)
            canvas.drawRoundRect(RectF(x, yMid - h, x + w, yMid + h), w, w, fill)
        }
        fill.shader = null

        line.strokeWidth = density
        line.color = Color.argb(90, Color.red(cyan), Color.green(cyan), Color.blue(cyan))
        canvas.drawLine(x0.toFloat(), yMid, right.toFloat(), yMid, line)
    }

    private fun drawLinguistic(canvas: Canvas, left: Int, right: Int, top: Float, laneH: Float, expanded: Boolean) {
        val violet = RealityVisuals.Colors.Lilac
        val labelW = dp(if (expanded) 102 else 80)
        drawLabel(canvas, "LINGUISTIC", signal.linguistic, left.toFloat(), top + dp(if (expanded) 17 else 13), violet, expanded)
        if (expanded) drawSub(canvas, "PATTERN SCAN", left.toFloat(), top + dp(32), violet)

        val x0 = left + labelW
        val y = top + laneH * .52f
        line.strokeWidth = dpF(1.4f)
        line.shader = LinearGradient(x0.toFloat(), 0f, right.toFloat(), 0f,
            intArrayOf(Color.argb(80, 135, 109, 255), Color.rgb(181, 110, 255), Color.argb(90, 108, 211, 255)),
            null, Shader.TileMode.CLAMP)
        canvas.drawLine(x0.toFloat(), y, right.toFloat(), y, line)
        line.shader = null

        val markers = linguistic.markers.take(if (expanded) 5 else 3)
        if (markers.isEmpty()) {
            val nodes = 4
            for (i in 0 until nodes) {
                val x = x0 + (right - x0) * (i + 1f) / (nodes + 1f)
                drawNode(canvas, x, y, violet, false)
            }
            if (expanded) drawTiny(canvas, "SCANNING", x0.toFloat(), top + laneH - dp(5), Color.argb(150, 189, 177, 224))
            return
        }

        markers.forEachIndexed { index, marker ->
            val x = x0 + (right - x0) * (index + 1f) / (markers.size + 1f)
            drawNode(canvas, x, y, violet, true)
            if (expanded) {
                val short = markerLabel(marker)
                drawChip(canvas, short, x, top + laneH - dp(12), violet)
            }
        }
    }

    private fun drawFactual(canvas: Canvas, left: Int, right: Int, top: Float, laneH: Float, expanded: Boolean) {
        val mint = RealityVisuals.Colors.Green
        val labelW = dp(if (expanded) 102 else 80)
        drawLabel(canvas, "FACTUAL", signal.factual, left.toFloat(), top + dp(if (expanded) 17 else 13), mint, expanded)
        if (expanded) drawSub(canvas, factualStatus(), left.toFloat(), top + dp(32), factualAccent())

        val x0 = left + labelW
        val center = top + laneH * .55f
        val values = factualHistory.toList().takeLast(if (expanded) 12 else 8).ifEmpty { listOf(signal.factual) }
        val step = if (values.size <= 1) 0f else (right - x0).toFloat() / (values.size - 1)

        path.reset()
        values.forEachIndexed { i, value ->
            val x = if (values.size <= 1) (x0 + right) / 2f else x0 + step * i
            val y = center - ((value.coerceIn(0, 100) - 35f) / 100f) * laneH * .72f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        line.strokeWidth = dpF(1.2f)
        line.color = Color.argb(130, 77, 245, 193)
        canvas.drawPath(path, line)

        values.forEachIndexed { i, value ->
            val x = if (values.size <= 1) (x0 + right) / 2f else x0 + step * i
            val y = center - ((value.coerceIn(0, 100) - 35f) / 100f) * laneH * .72f
            val accent = when {
                value >= 60 -> Color.rgb(255, 111, 118)
                value >= 35 -> RealityVisuals.Colors.Amber
                else -> Color.rgb(87, 231, 196)
            }
            fill.color = accent
            canvas.drawCircle(x, y, dpF(if (value >= 60) 3.2f else 2.2f), fill)
            line.strokeWidth = density
            line.color = Color.argb(if (value >= 60) 190 else 90, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(x, y, dpF(if (value >= 60) 6f else 4.4f), line)
        }
    }

    private fun factualStatus(): String = when {
        signal.factual >= 60 || insight.changes.isNotEmpty() -> "CONFLICT EVIDENCE"
        signal.factual >= 35 -> "REVIEW"
        transcript.text.isNotBlank() || transcript.entries.isNotEmpty() -> "NO CONFLICT FOUND"
        else -> "WAITING FOR CLAIMS"
    }

    private fun factualAccent(): Int = when {
        signal.factual >= 60 || insight.changes.isNotEmpty() -> Color.rgb(255, 111, 118)
        signal.factual >= 35 -> RealityVisuals.Colors.Amber
        else -> RealityVisuals.Colors.Green
    }

    private fun drawLabel(canvas: Canvas, label: String, score: Int, x: Float, baseline: Float, color: Int, expanded: Boolean) {
        text.shader = null
        text.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        text.textSize = dpF(if (expanded) 10.2f else 8.8f)
        text.letterSpacingCompat(if (expanded) .12f else .08f)
        text.color = color
        canvas.drawText(label, x, baseline, text)

        text.textSize = dpF(if (expanded) 9.5f else 8f)
        text.color = Color.argb(180, 226, 232, 255)
        text.letterSpacingCompat(0f)
        val value = "$score"
        canvas.drawText(value, x + dp(if (expanded) 70 else 58), baseline, text)
    }

    private fun drawSub(canvas: Canvas, label: String, x: Float, baseline: Float, color: Int) {
        text.textSize = dpF(8.1f)
        text.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        text.letterSpacingCompat(.04f)
        text.color = Color.argb(175, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawText(label, x, baseline, text)
    }

    private fun drawTiny(canvas: Canvas, label: String, x: Float, baseline: Float, color: Int) {
        text.textSize = dpF(7.2f)
        text.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        text.letterSpacingCompat(.08f)
        text.color = color
        canvas.drawText(label, x, baseline, text)
    }

    private fun drawNode(canvas: Canvas, x: Float, y: Float, color: Int, active: Boolean) {
        fill.color = Color.argb(if (active) 245 else 70, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(x, y, dpF(if (active) 3.1f else 1.8f), fill)
        if (active) {
            line.strokeWidth = density
            line.color = Color.argb(110, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle(x, y, dpF(6.3f), line)
        }
    }

    private fun drawChip(canvas: Canvas, label: String, centerX: Float, baseline: Float, color: Int) {
        text.textSize = dpF(7.1f)
        text.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        text.letterSpacingCompat(.02f)
        val w = text.measureText(label) + dp(11)
        val h = dpF(15f)
        val rect = RectF(centerX - w / 2f, baseline - h + dpF(3f), centerX + w / 2f, baseline + dpF(3f))
        fill.color = Color.argb(42, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRoundRect(rect, h / 2f, h / 2f, fill)
        line.color = Color.argb(105, Color.red(color), Color.green(color), Color.blue(color))
        line.strokeWidth = density * .7f
        canvas.drawRoundRect(rect, h / 2f, h / 2f, line)
        text.color = Color.argb(230, 225, 214, 255)
        canvas.drawText(label, centerX - text.measureText(label) / 2f, baseline - dpF(1.2f), text)
    }

    private fun drawDivider(canvas: Canvas, left: Int, right: Int, y: Float) {
        line.strokeWidth = density * .55f
        line.color = Color.argb(35, 154, 171, 224)
        canvas.drawLine(left.toFloat(), y, right.toFloat(), y, line)
    }

    private fun markerLabel(marker: String): String = when {
        marker.contains("uncertainty") || marker.contains("hedge") -> "HEDGE"
        marker.contains("qualifier") -> "QUALIFIER"
        marker.contains("disfluency") -> "PAUSE"
        marker.contains("self-correction") || marker.contains("restart") -> "CORRECT"
        marker.contains("distancing") -> "DISTANCE"
        marker.contains("negation") -> "NEGATION"
        marker.contains("absolute") -> "CERTAINTY"
        marker.contains("temporal") -> "TIME"
        marker.contains("repetition") -> "REPEAT"
        marker.contains("clause") -> "COMPLEX"
        marker.contains("response length") -> "LOAD"
        else -> marker.uppercase().take(10)
    }

    private fun push(queue: ArrayDeque<Int>, value: Int, maxSize: Int) {
        queue.addLast(value.coerceIn(0, 100))
        while (queue.size > maxSize) queue.removeFirst()
    }

    private fun Paint.letterSpacingCompat(value: Float) {
        // Canvas Paint has no TextView letterSpacing property. Keep this helper as a semantic no-op
        // so the drawing code remains readable without requiring API-specific text shaping.
        @Suppress("UNUSED_VARIABLE") val ignored = value
    }

    private fun dp(value: Int): Int = (value * density).toInt()
    private fun dpF(value: Float): Float = value * density
}

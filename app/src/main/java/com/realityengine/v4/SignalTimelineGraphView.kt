package com.realityengine.v4

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

/** Compact post-call plot of persisted signal samples. These are cues, not deception verdicts. */
class SignalTimelineGraphView(context: Context) : View(context) {
    private var events: List<CallerProfileStore.EvidenceEvent> = emptyList()
    private var baseMs: Long = 0L

    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RealityVisuals.Colors.Border
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RealityVisuals.Colors.TextDim
        textSize = sp(9f)
        typeface = Typeface.MONOSPACE
    }
    private val legendText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RealityVisuals.Colors.Text
        textSize = sp(9f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val acoustic = linePaint(RealityVisuals.Colors.Cyan, 1.7f)
    private val linguistic = linePaint(RealityVisuals.Colors.Green, 1.7f)
    private val factual = linePaint(RealityVisuals.Colors.Magenta, 1.7f)
    private val fused = linePaint(RealityVisuals.Colors.Text, 2.6f)

    init {
        minimumHeight = dp(220f).toInt()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setData(items: List<CallerProfileStore.EvidenceEvent>, callStartedAtMs: Long) {
        events = items.sortedBy { it.timestampMs }
        baseMs = callStartedAtMs.takeIf { it > 0L } ?: events.firstOrNull()?.timestampMs ?: 0L
        contentDescription = if (events.isEmpty()) {
            "No saved signal samples"
        } else {
            "Signal graph with ${events.size} saved samples from this call"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val left = dp(38f)
        val right = width - dp(12f)
        val top = dp(34f)
        val bottom = height - dp(32f)
        if (right <= left || bottom <= top) return

        canvas.drawText("A", left, dp(13f), acoustic)
        canvas.drawText("ACOUSTIC", left + dp(10f), dp(13f), legendText)
        canvas.drawText("L", left + dp(78f), dp(13f), linguistic)
        canvas.drawText("LINGUISTIC", left + dp(88f), dp(13f), legendText)
        canvas.drawText("F", left + dp(172f), dp(13f), factual)
        canvas.drawText("FACTUAL", left + dp(182f), dp(13f), legendText)
        canvas.drawText("● FUSED", right - dp(55f), dp(13f), legendText)

        listOf(0, 25, 50, 75, 100).forEach { pct ->
            val y = yFor(pct / 100f, top, bottom)
            canvas.drawLine(left, y, right, y, grid)
            canvas.drawText("$pct", dp(4f), y + dp(3f), axisText)
        }

        if (events.isEmpty()) {
            canvas.drawText("NO SAVED SAMPLES", left + dp(20f), (top + bottom) / 2f, axisText)
            return
        }

        val maxTs = events.maxOf { it.timestampMs }
        val spanMs = max(1L, maxTs - baseMs)
        drawSeries(canvas, events.map { it.acoustic }, acoustic, left, right, top, bottom, spanMs)
        drawSeries(canvas, events.map { it.linguistic }, linguistic, left, right, top, bottom, spanMs)
        drawSeries(canvas, events.map { it.factual }, factual, left, right, top, bottom, spanMs)
        drawSeries(canvas, events.map { it.combined }, fused, left, right, top, bottom, spanMs)

        events.forEach { event ->
            val x = xFor(event.timestampMs, left, right, spanMs)
            val y = yFor(event.combined, top, bottom)
            canvas.drawCircle(x, y, dp(2.6f), fused)
        }

        canvas.drawText("0:00", left, height - dp(8f), axisText)
        val endSeconds = spanMs / 1000L
        val endLabel = "%d:%02d".format(endSeconds / 60L, endSeconds % 60L)
        canvas.drawText(endLabel, right - axisText.measureText(endLabel), height - dp(8f), axisText)
    }

    private fun drawSeries(
        canvas: Canvas,
        scores: List<Float>,
        paint: Paint,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        spanMs: Long,
    ) {
        if (scores.isEmpty()) return
        val path = Path()
        events.forEachIndexed { index, event ->
            val x = xFor(event.timestampMs, left, right, spanMs)
            val y = yFor(scores[index], top, bottom)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun xFor(timestampMs: Long, left: Float, right: Float, spanMs: Long): Float {
        val fraction = ((timestampMs - baseMs).coerceAtLeast(0L).toDouble() / spanMs.toDouble()).coerceIn(0.0, 1.0)
        return left + ((right - left) * fraction).toFloat()
    }

    private fun yFor(score: Float, top: Float, bottom: Float): Float {
        return bottom - (score.coerceIn(0f, 1f) * (bottom - top))
    }

    private fun linePaint(colorValue: Int, widthDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorValue
        strokeWidth = dp(widthDp)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}

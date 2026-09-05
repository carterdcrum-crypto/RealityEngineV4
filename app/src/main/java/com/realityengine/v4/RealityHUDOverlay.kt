package com.realityengine.v4

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin

data class HUDState(
    val honesty: Int = 100,
    val acoustic: Int = 0,
    val linguistic: Int = 0,
    val factual: Int = 0,
    val cognitive: Int = 0,
    val latencyMs: Long = 0,
    val pitchJitterHigh: Boolean = false,
    val fillerCount: Int = 0,
    val evasiveness: Int = 0,
    val transcript: String = "",
    val discrepancy: Boolean = false,
    val logOdds: Double = 0.0
)

@Composable
fun RealityHUDOverlay(state: HUDState, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "hud").animateFloat(
        initialValue = 0.82f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    ).value

    Column(
        modifier = modifier
            .background(Color(0xF003080D))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("PHONE // MULTIMODAL HUD", color = Color(0xFF55EFFF), fontSize = 12.sp)
        HonestyCore(state, pulse)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StreamPanel("E1 // ACOUSTIC", Modifier.weight(1f)) { AudioViz(state, pulse) }
            StreamPanel("E3 // KNOWLEDGE", Modifier.weight(1f)) { Radar(state, pulse) }
        }
        StreamPanel("E2 // LINGUISTIC", Modifier.fillMaxWidth()) { LinguisticFeed(state) }
        if (state.discrepancy) {
            Text("DISCREPANCY DETECTED", color = Color(0xFFFF315C), fontSize = 14.sp)
        }
    }
}

@Composable
private fun HonestyCore(state: HUDState, pulse: Float) {
    val gaugeColor = when {
        state.honesty > 80 -> Color(0xFF35E7FF)
        state.honesty >= 50 -> Color(0xFFFFB52E)
        else -> Color(0xFFFF2D70)
    }
    val formattedOdds = "%.2f".format(state.logOdds)
    val oddsText = if (state.logOdds >= 0.0) {
        "LOG-ODDS UP +$formattedOdds"
    } else {
        "LOG-ODDS DOWN $formattedOdds"
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(155.dp)) {
            val strokeWidth = 12.dp.toPx()
            drawArc(
                color = Color(0x332EDFFF),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(strokeWidth)
            )
            drawArc(
                color = gaugeColor,
                startAngle = -90f,
                sweepAngle = 360f * state.honesty.coerceIn(0, 100) / 100f,
                useCenter = false,
                style = Stroke(strokeWidth * pulse, cap = StrokeCap.Round)
            )
            drawCircle(
                color = gaugeColor.copy(alpha = 0.12f),
                radius = size.minDimension * 0.38f * pulse
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("HONESTY SCORE", color = Color(0xFF9DDDE8), fontSize = 11.sp)
            Text("${state.honesty}%", color = gaugeColor, fontSize = 36.sp)
            Text(oddsText, color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
private fun StreamPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .background(Color(0xAA091721), RoundedCornerShape(10.dp))
            .padding(9.dp)
    ) {
        Text(title, color = Color(0xFF52E9FF), fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun AudioViz(state: HUDState, pulse: Float) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(82.dp)) {
            val barCount = 22
            val stress = state.cognitive / 100f
            for (index in 0 until barCount) {
                val x = size.width * index / (barCount - 1f)
                val phase = index * 0.71f + state.acoustic * 0.08f
                val wave = (0.15f + 0.75f * abs(sin(phase))) * (0.35f + stress)
                drawLine(
                    color = Color(0xFF41E9FF),
                    start = Offset(x, size.height / 2f - wave * size.height / 2f),
                    end = Offset(x, size.height / 2f + wave * size.height / 2f),
                    strokeWidth = 3f
                )
            }
            if (state.latencyMs > 1500L || state.pitchJitterHigh) {
                drawCircle(
                    color = Color(0xFFFF2D8A).copy(alpha = 0.7f),
                    radius = size.minDimension * 0.47f * pulse,
                    style = Stroke(3f)
                )
            }
        }
        Text("LATENCY: ${state.latencyMs} ms", color = Color.White, fontSize = 9.sp)
        Text(
            text = "PITCH JITTER: ${if (state.pitchJitterHigh) "HIGH" else "LOW"}",
            color = if (state.pitchJitterHigh) Color(0xFFFF3B75) else Color(0xFF6FFFC1),
            fontSize = 9.sp
        )
        Text("COGNITIVE LOAD: ${state.cognitive}%", color = Color(0xFFFF75C8), fontSize = 9.sp)
    }
}

@Composable
private fun LinguisticFeed(state: HUDState) {
    Column {
        Text(
            text = highlightFillers(state.transcript),
            color = Color(0xFFE4F5F8),
            fontSize = 11.sp,
            maxLines = 4
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "FILLER COUNT: ${state.fillerCount}   EVASIVENESS: ${state.evasiveness}/10",
            color = Color(0xFFFFD45B),
            fontSize = 9.sp
        )
    }
}

private fun highlightFillers(text: String): String {
    val regex = Regex("(?i)\\b(um|uh|like|you know|honestly|basically)\\b")
    return regex.replace(text) { match -> "[${match.value.uppercase()}]" }
}

@Composable
private fun Radar(state: HUDState, pulse: Float) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(105.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.42f
            for (ring in 1..3) {
                drawCircle(
                    color = Color(0x443FE7FF),
                    radius = radius * ring / 3f,
                    center = center,
                    style = Stroke(2f)
                )
            }
            drawLine(
                Color(0x553FE7FF),
                Offset(center.x - radius, center.y),
                Offset(center.x + radius, center.y),
                1f
            )
            drawLine(
                Color(0x553FE7FF),
                Offset(center.x, center.y - radius),
                Offset(center.x, center.y + radius),
                1f
            )
            if (state.discrepancy) {
                val point = Offset(center.x + radius * 0.45f, center.y - radius * 0.25f)
                drawCircle(Color(0xFFFF244F), radius = 8f * pulse, center = point)
                drawCircle(
                    Color(0x88FF244F),
                    radius = 20f * pulse,
                    center = point,
                    style = Stroke(3f)
                )
            }
        }
        Text("FACTUAL SIGNAL: ${state.factual}%", color = Color.White, fontSize = 9.sp)
    }
}

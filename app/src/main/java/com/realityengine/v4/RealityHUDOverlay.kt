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
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    ).value
    Column(
        modifier.background(Color(0xF003080D)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("REALITY ENGINE // MULTIMODAL HUD", color = Color(0xFF55EFFF), fontSize = 12.sp)
        HonestyCore(state, pulse)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StreamPanel("E1 // ACOUSTIC", Modifier.weight(1f)) { AudioViz(state, pulse) }
            StreamPanel("E3 // KNOWLEDGE", Modifier.weight(1f)) { Radar(state, pulse) }
        }
        StreamPanel("E2 // LINGUISTIC", Modifier.fillMaxWidth()) { LinguisticFeed(state) }
        if (state.discrepancy) Text("DISCREPANCY DETECTED", color = Color(0xFFFF315C), fontSize = 14.sp)
    }
}

@Composable
private fun HonestyCore(s: HUDState, pulse: Float) {
    val color = when {
        s.honesty > 80 -> Color(0xFF35E7FF)
        s.honesty >= 50 -> Color(0xFFFFB52E)
        else -> Color(0xFFFF2D70)
    }
    val oddsText = if (s.logOdds >= 0.0) {
        "LOG-ODDS  UP +${"%.2f".format(s.logOdds)}"
    } else {
        "LOG-ODDS  DOWN ${"%.2f".format(s.logOdds)}"
    }
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(155.dp)) {
            val sw = 12.dp.toPx()
            drawArc(Color(0x332EDFFF), -90f, 360f, false, style = Stroke(sw))
            drawArc(color, -90f, 360f * s.honesty.coerceIn(0, 100) / 100f, false, style = Stroke(sw * pulse, cap = StrokeCap.Round))
            drawCircle(color.copy(alpha = 0.12f), radius = size.minDimension * 0.38f * pulse)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("HONESTY SCORE", color = Color(0xFF9DDDE8), fontSize = 11.sp)
            Text("${s.honesty}%", color = color, fontSize = 36.sp)
            Text(oddsText, color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
private fun StreamPanel(title: String, modifier: Modifier, body: @Composable () -> Unit) {
    Column(modifier.background(Color(0xAA091721), RoundedCornerShape(10.dp)).padding(9.dp)) {
        Text(title, color = Color(0xFF52E9FF), fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        body()
    }
}

@Composable
private fun AudioViz(s: HUDState, pulse: Float) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(82.dp)) {
            val n = 22
            val stress = s.cognitive / 100f
            for (i in 0 until n) {
                val x = size.width * i / (n - 1f)
                val wave = (0.15f + 0.75f * abs(sin(i * 0.71f + s.acoustic * 0.08f)).toFloat()) * (0.35f + stress)
                drawLine(Color(0xFF41E9FF), Offset(x, size.height / 2 - wave * size.height / 2), Offset(x, size.height / 2 + wave * size.height / 2), 3f)
            }
            if (s.latencyMs > 1500 || s.pitchJitterHigh) {
                drawCircle(Color(0xFFFF2D8A).copy(alpha = 0.7f), radius = size.minDimension * 0.47f * pulse, style = Stroke(3f))
            }
        }
        Text("LATENCY: ${s.latencyMs} ms", color = Color.White, fontSize = 9.sp)
        Text("PITCH JITTER: ${if (s.pitchJitterHigh) "HIGH" else "LOW"}", color = if (s.pitchJitterHigh) Color(0xFFFF3B75) else Color(0xFF6FFFC1), fontSize = 9.sp)
        Text("COGNITIVE LOAD: ${s.cognitive}%", color = Color(0xFFFF75C8), fontSize = 9.sp)
    }
}

@Composable
private fun LinguisticFeed(s: HUDState) {
    Column {
        Text(highlightFillers(s.transcript), color = Color(0xFFE4F5F8), fontSize = 11.sp, maxLines = 4)
        Spacer(Modifier.height(5.dp))
        Text("FILLER COUNT: ${s.fillerCount}   EVASIVENESS: ${s.evasiveness}/10", color = Color(0xFFFFD45B), fontSize = 9.sp)
    }
}

private fun highlightFillers(text: String): String {
    val regex = Regex("(?i)\\b(um|uh|like|you know|honestly|basically)\\b")
    return regex.replace(text) { "[${it.value.uppercase()}]" }
}

@Composable
private fun Radar(s: HUDState, pulse: Float) {
    Canvas(Modifier.fillMaxWidth().height(105.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.42f
        for (k in 1..3) drawCircle(Color(0x443FE7FF), radius * k / 3, center, style = Stroke(2f))
        drawLine(Color(0x553FE7FF), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1f)
        drawLine(Color(0x553FE7FF), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1f)
        if (s.discrepancy) {
            val point = Offset(center.x + radius * 0.45f, center.y - radius * 0.25f)
            drawCircle(Color(0xFFFF244F), 8f * pulse, point)
            drawCircle(Color(0x88FF244F), 20f * pulse, point, style = Stroke(3f))
        }
    }
    Text("FACTUAL SIGNAL: ${s.factual}%", color = Color.White, fontSize = 9.sp)
}

package com.sient.mytimer.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Pomodoro palette — tomato body, cream flesh, leaf green.
val TomatoRed = Color(0xFFE5533D)
val TomatoDeep = Color(0xFFB33A27)
val FocusCream = Color(0xFFFFF6EC)
val LeafGreen = Color(0xFF6FBF4B)

/**
 * A face for the fixed 25+5 pomodoro cycle: one full turn of the ring is
 * 30 minutes — a cream 25-minute focus arc and a green 5-minute break arc
 * that sits at the top like the tomato's leaf. Small tomato-colored dots
 * divide the focus arc into 5-minute slices.
 */
@Composable
fun PomodoroFace(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 10.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.88f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        // 12° per minute. Focus: top → 300° clockwise. Break: the last 60°,
        // ending back at the top. Small gaps keep the segments distinct.
        drawArc(
            color = FocusCream,
            startAngle = -86f, sweepAngle = 292f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = LeafGreen,
            startAngle = 214f, sweepAngle = 52f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )

        // 5-minute dividers on the focus arc.
        for (i in 1..4) {
            val angleRad = Math.toRadians((i * 60 - 90).toDouble())
            drawCircle(
                color = TomatoRed,
                radius = stroke * 0.28f,
                center = Offset(
                    center.x + radius * cos(angleRad).toFloat(),
                    center.y + radius * sin(angleRad).toFloat()
                )
            )
        }
    }
}

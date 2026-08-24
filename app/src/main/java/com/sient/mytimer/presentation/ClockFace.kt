package com.sient.mytimer.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A clock-face style dial with 12 marks labeled 5..60 (5-minute steps).
 * Drag or tap anywhere on the face to move the selection to the nearest mark.
 */
@Composable
fun ClockFace(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        minutesForPosition(pos, size)?.let(onSelect)
                    },
                    onDrag = { change, _ ->
                        minutesForPosition(change.position, size)?.let(onSelect)
                        change.consume()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    minutesForPosition(pos, size)?.let(onSelect)
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val ringRadius = radius * 0.90f
        val labelRadius = radius * 0.70f
        val ringStroke = 4.dp.toPx()

        // Faint full track ring.
        drawCircle(
            color = dimColor.copy(alpha = 0.15f),
            radius = ringRadius,
            center = center,
            style = Stroke(width = ringStroke)
        )

        // Accent arc from 12 o'clock to the selected mark.
        val sweep = selectedMinutes / 60f * 360f
        drawArc(
            color = accent,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2, ringRadius * 2),
            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
        )

        // Marks and labels.
        for (i in 1..12) {
            val minutes = i * 5
            val angleRad = Math.toRadians((i * 30 - 90).toDouble())
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()
            val selected = minutes == selectedMinutes

            // Dot on the ring for each mark (the selected one gets the knob instead).
            if (!selected) {
                drawCircle(
                    color = dimColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(center.x + ringRadius * cosA, center.y + ringRadius * sinA)
                )
            }

            val label = textMeasurer.measure(
                AnnotatedString(minutes.toString()),
                style = TextStyle(
                    color = if (selected) accent else labelColor,
                    fontSize = if (selected) 16.sp else 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            )
            drawText(
                label,
                topLeft = Offset(
                    center.x + labelRadius * cosA - label.size.width / 2f,
                    center.y + labelRadius * sinA - label.size.height / 2f
                )
            )
        }

        // Knob at the selected mark, on top of the arc.
        val knobRad = Math.toRadians((selectedMinutes * 6 - 90).toDouble())
        drawCircle(
            color = accent,
            radius = 7.dp.toPx(),
            center = Offset(
                center.x + ringRadius * cos(knobRad).toFloat(),
                center.y + ringRadius * sin(knobRad).toFloat()
            )
        )
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = Offset(
                center.x + ringRadius * cos(knobRad).toFloat(),
                center.y + ringRadius * sin(knobRad).toFloat()
            )
        )
    }
}

/**
 * Maps a touch position to the nearest 5-minute mark (5..60),
 * or null when the touch is too close to the center to be meaningful.
 */
private fun minutesForPosition(pos: Offset, size: IntSize): Int? {
    val dx = pos.x - size.width / 2f
    val dy = pos.y - size.height / 2f
    val minDistance = size.width.coerceAtMost(size.height) / 2f * 0.25f
    if (hypot(dx, dy) < minDistance) return null

    // 0° at 12 o'clock, growing clockwise.
    val degrees = (Math.toDegrees(atan2(dy, dx).toDouble()) + 450.0) % 360.0
    val index = (degrees / 30.0).roundToInt() % 12
    return if (index == 0) 60 else index * 5
}

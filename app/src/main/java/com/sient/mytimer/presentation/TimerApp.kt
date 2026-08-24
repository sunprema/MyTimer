package com.sient.mytimer.presentation

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.sient.mytimer.TimerService
import com.sient.mytimer.TimerSnapshot
import com.sient.mytimer.presentation.theme.MyTimerTheme
import kotlinx.coroutines.delay

@Composable
fun TimerApp() {
    MyTimerTheme {
        val snapshot by TimerService.state.collectAsState()
        val context = LocalContext.current
        var selectedMinutes by rememberSaveable { mutableIntStateOf(15) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val snap = snapshot
            when {
                snap == null -> DialPickerScreen(
                    selectedMinutes = selectedMinutes,
                    onSelect = { selectedMinutes = it },
                    onStart = { TimerService.start(context, selectedMinutes) }
                )

                snap.finished -> FinishedScreen(
                    minutes = snap.minutes,
                    onStop = { TimerService.stop(context) }
                )

                else -> CountdownScreen(
                    snapshot = snap,
                    onCancel = { TimerService.stop(context) }
                )
            }
        }
    }
}

@Composable
private fun DialPickerScreen(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                // One bezel detent arrives as one event → exactly one 5-minute step.
                val delta = if (event.verticalScrollPixels > 0f) 5 else -5
                onSelect((selectedMinutes + delta).coerceIn(5, 60))
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        ClockFace(
            selectedMinutes = selectedMinutes,
            onSelect = onSelect,
            modifier = Modifier.fillMaxSize()
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$selectedMinutes",
                fontSize = 40.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "min",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Text("Start")
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun CountdownScreen(snapshot: TimerSnapshot, onCancel: () -> Unit) {
    // Keep the screen on while the countdown is visible.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var millisLeft by remember(snapshot.endElapsedRealtime) {
        mutableLongStateOf(
            (snapshot.endElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        )
    }
    LaunchedEffect(snapshot.endElapsedRealtime) {
        while (true) {
            millisLeft =
                (snapshot.endElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            delay(100)
        }
    }

    val progressColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = 5.dp.toPx()
            val ringRadius = size.minDimension / 2f - stroke
            val topLeft = Offset(size.width / 2f - ringRadius, size.height / 2f - ringRadius)
            val arcSize = Size(ringRadius * 2, ringRadius * 2)
            drawArc(
                color = trackColor,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke)
            )
            val fraction =
                if (snapshot.totalMillis > 0) millisLeft.toFloat() / snapshot.totalMillis else 0f
            drawArc(
                color = progressColor,
                startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val totalSeconds = (millisLeft + 999) / 1000
            Text(
                text = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60),
                fontSize = 44.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Box(Modifier.height(10.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun FinishedScreen(minutes: Int, onStop: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Time's up!",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$minutes min",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.height(12.dp))
            Button(onClick = onStop) {
                Text("Stop")
            }
        }
    }
}

@WearPreviewDevices
@Composable
fun TimerAppPreview() {
    TimerApp()
}

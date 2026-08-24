package com.sient.mytimer.presentation

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.sient.mytimer.TimerMode
import com.sient.mytimer.TimerService
import com.sient.mytimer.TimerSnapshot
import com.sient.mytimer.presentation.theme.MyTimerTheme
import kotlinx.coroutines.delay

@Composable
fun TimerApp() {
    MyTimerTheme {
        val snapshot by TimerService.state.collectAsState()
        val context = LocalContext.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val snap = snapshot
            when {
                snap == null -> PickerPager()

                snap.mode == TimerMode.SIMPLE && snap.finished -> FinishedScreen(
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

/** Page 0: simple timer dial. Page 1 (swipe up): pomodoro dial. */
@Composable
private fun PickerPager() {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    var timerMinutes by rememberSaveable { mutableIntStateOf(15) }

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> PickerPage(
                    selectedMinutes = timerMinutes,
                    onSelect = { timerMinutes = it },
                    isActive = pagerState.currentPage == 0,
                    title = null,
                    caption = "min",
                    onStart = { TimerService.start(context, timerMinutes) }
                )

                else -> PomodoroPickerPage(
                    isActive = pagerState.currentPage == 1,
                    onStart = {
                        TimerService.startPomodoro(context, TimerService.FOCUS_MINUTES)
                    }
                )
            }
        }

        PageDots(
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
        )
    }
}

@Composable
private fun PageDots(currentPage: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { index ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        // White reads on both the dark timer page and the
                        // tomato pomodoro page.
                        if (index == currentPage) Color.White
                        else Color.White.copy(alpha = 0.35f)
                    )
            )
        }
    }
}

/** Fixed 25+5 pomodoro: no picking, just the tomato face and Start. */
@Composable
private fun PomodoroPickerPage(isActive: Boolean, onStart: () -> Unit) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TomatoRed)
            // Nothing to adjust here; consume bezel input so it doesn't
            // change the timer dial on the page below.
            .onRotaryScrollEvent { true }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        PomodoroFace(modifier = Modifier.fillMaxSize())

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Pomodoro",
                fontSize = 14.sp,
                color = FocusCream
            )
            Text(
                text = "${TimerService.FOCUS_MINUTES}",
                fontSize = 40.sp,
                color = Color.White
            )
            Text(
                text = "min focus · ${TimerService.BREAK_MINUTES} break",
                fontSize = 12.sp,
                color = FocusCream.copy(alpha = 0.85f)
            )
            Box(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusCream,
                    contentColor = TomatoDeep
                )
            ) {
                Text("Start")
            }
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) focusRequester.requestFocus()
    }
}

@Composable
private fun PickerPage(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    isActive: Boolean,
    title: String?,
    caption: String,
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
            modifier = Modifier.fillMaxSize()
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (title != null) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "$selectedMinutes",
                fontSize = 40.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = caption,
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

    LaunchedEffect(isActive) {
        if (isActive) focusRequester.requestFocus()
    }
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

    val isPomodoro = snapshot.mode == TimerMode.POMODORO
    val progressColor = when {
        isPomodoro && snapshot.onBreak -> LeafGreen
        isPomodoro -> FocusCream
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor =
        if (isPomodoro) Color.White.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val textColor =
        if (isPomodoro) Color.White else MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isPomodoro) TomatoRed else MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
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
            if (isPomodoro) {
                Text(
                    text = snapshot.phaseLabel,
                    fontSize = 14.sp,
                    color = progressColor
                )
            }
            val totalSeconds = (millisLeft + 999) / 1000
            Text(
                text = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60),
                fontSize = 44.sp,
                color = textColor
            )
            if (isPomodoro) {
                Box(Modifier.height(6.dp))
                SessionDots(session = snapshot.session)
            }
            Box(Modifier.height(10.dp))
            OutlinedButton(onClick = onCancel) {
                Text(if (isPomodoro) "Stop" else "Cancel")
            }
        }
    }
}

@Composable
private fun SessionDots(session: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(TimerService.SESSIONS_PER_CYCLE) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < session) Color.White
                        else Color.White.copy(alpha = 0.3f)
                    )
            )
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

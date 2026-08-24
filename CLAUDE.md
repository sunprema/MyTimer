## REQUIREMENT

I want to work on a simple timer. I like to set the timer using the 12, 
5 minutes clock face like application. under each 5 minute mark, It should show, 5, 10, 15, 20 till 60. I will use the dial to select the number and leave it and press start for that timer to start.

That's basically a rotary dial timer picker — doable with a custom Composable using `Canvas` for the clock face and drag/rotation gesture (or physical bezel input if the watch has one) to select the value.
claude
**Approach:**
- Draw a circle with 12 tick marks, labeled 5,10,15...60 (each mark = one position, like clock numbers but 5-min steps instead of hours).
- Track a `selectedMinutes` state (5–60, step 5).
- Support two input methods: finger-drag around the circle, and rotating bezel (`onRotaryScrollEvent`) if the watch has a physical bezel — Galaxy Watch4+ do.
- A "Start" button in the center; tapping starts a countdown from `selectedMinutes * 60` seconds, vibrating at the end.

**Code sketch:**

```kotlin
@Composable
fun DialTimerScreen(vm: TimerViewModel, vibrator: Vibrator) {
    var selectedMinutes by remember { mutableStateOf(5) }
    val focusRequester = remember { FocusRequester() }

    if (!vm.isRunning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    // Each detent = one 5-min step
                    val delta = if (event.verticalScrollPixels > 0) 5 else -5
                    selectedMinutes = (selectedMinutes + delta).coerceIn(5, 60)
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            ClockFace(selectedMinutes = selectedMinutes, onDrag = { minutes ->
                selectedMinutes = minutes
            })

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$selectedMinutes", fontSize = 32.sp)
                Button(onClick = {
                    vm.timeLeft = selectedMinutes * 60
                    vm.start(vibrator)
                }) { Text("Start") }
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    } else {
        // countdown display + vibrate on finish (reuse logic from before)
        CountdownDisplay(vm)
    }
}
```

**ClockFace with drag support:**

```kotlin
@Composable
fun ClockFace(selectedMinutes: Int, onDrag: (Int) -> Unit) {
    val marks = (5..60 step 5).toList() // 12 marks
    Canvas(
        modifier = Modifier
            .size(200.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val angle = atan2(
                        change.position.y - center.y,
                        change.position.x - center.x
                    ) * 180 / PI + 90 // rotate so 12 o'clock = 0°
                    val normalized = ((angle + 360) % 360)
                    val index = (normalized / 30).toInt() // 30° per mark
                    onDrag(marks[index % 12])
                }
            }
    ) {
        val radius = size.minDimension / 2 * 0.85f
        val center = Offset(size.width / 2, size.height / 2)
        marks.forEachIndexed { i, value ->
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val x = center.x + radius * cos(angle).toFloat()
            val y = center.y + radius * sin(angle).toFloat()
            drawContext.canvas.nativeCanvas.drawText(
                value.toString(), x, y, android.graphics.Paint().apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = if (value == selectedMinutes) 40f else 28f
                    color = if (value == selectedMinutes) android.graphics.Color.CYAN else android.graphics.Color.WHITE
                }
            )
        }
    }
}
```

This gives you: 12 marks around the dial labeled 5→60, drag or bezel-rotate to select, tap Start to begin countdown with vibrate-on-finish (reusing your existing `TimerViewModel.start()`).


## Icon for the application

Create a nice looking icon for this timer so that the app displays a proper icon.

package com.sient.mytimer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.sient.mytimer.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class TimerMode { SIMPLE, POMODORO }

data class TimerSnapshot(
    val mode: TimerMode,
    val phaseLabel: String,
    val session: Int,
    val onBreak: Boolean,
    val minutes: Int,
    val totalMillis: Long,
    val endElapsedRealtime: Long,
    val finished: Boolean,
)

/**
 * Foreground service that owns the countdown, so timers keep running when
 * the user returns to the watch face or opens another app.
 *
 * Phase ends are scheduled with [AlarmManager] rather than a sleeping
 * coroutine: a foreground service keeps the process alive, but only an alarm
 * wakes the CPU once the watch has dozed off. Each alarm re-enters
 * [onStartCommand] with [ACTION_PHASE_END], which advances the state machine
 * and schedules the next alarm.
 *
 * SIMPLE mode counts one duration down and alerts until dismissed.
 * POMODORO mode loops focus/break phases (long break after 4 sessions),
 * alerting briefly at each transition, until the user stops it.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val alarmManager: AlarmManager by lazy {
        getSystemService(AlarmManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSimple(intent.getIntExtra(EXTRA_MINUTES, 5))
            ACTION_START_POMODORO -> startPomodoro(intent.getIntExtra(EXTRA_MINUTES, 25))
            ACTION_PHASE_END -> onPhaseEnd()
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    private fun startSimple(minutes: Int) {
        cancelAlarm()
        vibrator?.cancel()
        createChannel()

        val totalMillis = minutes * 60_000L
        val end = SystemClock.elapsedRealtime() + totalMillis
        _state.value = TimerSnapshot(
            TimerMode.SIMPLE, "Timer", 0, false, minutes, totalMillis, end, finished = false
        )
        goForeground(
            countdownNotification("Timer running", "$minutes minute timer", totalMillis),
            "#left# left", end
        )
        scheduleAlarm(end)
    }

    private fun startPomodoro(workMinutes: Int) {
        cancelAlarm()
        vibrator?.cancel()
        createChannel()
        beginPhase("Focus", workMinutes, session = 1, onBreak = false)
    }

    private fun beginPhase(label: String, minutes: Int, session: Int, onBreak: Boolean) {
        val total = minutes * 60_000L
        val end = SystemClock.elapsedRealtime() + total
        _state.value = TimerSnapshot(
            TimerMode.POMODORO, label, session, onBreak, minutes, total, end, finished = false
        )
        goForeground(
            countdownNotification(label, "Pomodoro · session $session of $SESSIONS_PER_CYCLE", total),
            "$label · #left#", end
        )
        scheduleAlarm(end)
    }

    /** Alarm fired: the current phase is over. */
    private fun onPhaseEnd() {
        val current = _state.value
        Log.d(TAG, "phase end: $current")
        if (current == null) {
            // Process was recycled and the in-memory state lost; nothing to resume.
            // The alarm started us as a foreground service, so satisfy that first.
            startForeground(NOTIFICATION_ID, countdownNotification("Timer", "", 0).build())
            stopTimer()
            return
        }

        // AlarmManager only holds the CPU while delivering the intent; keep it
        // awake long enough for the beep and vibration to actually play.
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:alert")
            .acquire(ALERT_WAKE_MILLIS)

        when (current.mode) {
            TimerMode.SIMPLE -> {
                // Re-assert foreground: the alarm's PendingIntent started us as
                // a foreground service, which requires a matching startForeground.
                startForeground(NOTIFICATION_ID, finishedNotification().build())
                onSimpleFinished()
            }

            TimerMode.POMODORO -> {
                transitionAlert()
                if (!current.onBreak) {
                    val longBreak = current.session == SESSIONS_PER_CYCLE
                    beginPhase(
                        if (longBreak) "Long break" else "Break",
                        if (longBreak) LONG_BREAK_MINUTES else BREAK_MINUTES,
                        current.session, onBreak = true
                    )
                } else {
                    val next = if (current.session == SESSIONS_PER_CYCLE) 1 else current.session + 1
                    beginPhase("Focus", FOCUS_MINUTES, next, onBreak = false)
                }
            }
        }
    }

    private fun scheduleAlarm(endElapsedRealtime: Long) {
        val delay = endElapsedRealtime - SystemClock.elapsedRealtime()
        val triggerAtRtc = System.currentTimeMillis() + delay
        // setAlarmClock: exact, fires in doze, and unlike
        // setExactAndAllowWhileIdle it is not throttled to one alarm per
        // 9 minutes — pomodoro breaks are shorter than that.
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtRtc, tapIntent()),
            phaseEndIntent()
        )
        Log.d(TAG, "alarm scheduled in ${delay / 1000}s")
    }

    private fun cancelAlarm() {
        alarmManager.cancel(phaseEndIntent())
    }

    private fun phaseEndIntent(): PendingIntent =
        PendingIntent.getForegroundService(
            this,
            1,
            Intent(this, TimerService::class.java).setAction(ACTION_PHASE_END),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Brief beep + vibration between pomodoro phases; no dismissal needed. */
    private fun transitionAlert() {
        playDoubleBeep()
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
    }

    private fun onSimpleFinished() {
        _state.value = _state.value?.copy(finished = true)
        playDoubleBeep()
        vibrator?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 800), 0)
        )
        OngoingActivity.recoverOngoingActivity(applicationContext)
            ?.update(
                applicationContext,
                Status.Builder().addTemplate("Time's up!").build()
            )
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, finishedNotification().build())
    }

    private fun playDoubleBeep() {
        scope.launch {
            try {
                val toneGenerator =
                    ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2)
                delay(600)
                toneGenerator.release()
            } catch (_: RuntimeException) {
                // No audio output available; the vibration still alerts.
            }
        }
    }

    private fun stopTimer() {
        cancelAlarm()
        vibrator?.cancel()
        _state.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cancelAlarm()
        vibrator?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(
        builder: NotificationCompat.Builder,
        statusTemplate: String,
        endElapsedRealtime: Long,
    ) {
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_timer_notification)
            .setTouchIntent(tapIntent())
            .setStatus(
                Status.Builder()
                    .addTemplate(statusTemplate)
                    .addPart("left", Status.TimerPart(endElapsedRealtime))
                    .build()
            )
            .build()
            .apply(applicationContext)
        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun tapIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun countdownNotification(
        title: String,
        text: String,
        millisLeft: Long,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis() + millisLeft)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(tapIntent())

    private fun finishedNotification(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("Time's up!")
            .setContentText("${_state.value?.minutes ?: 0} minute timer finished")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(tapIntent())

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Timer", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            // The service vibrates on its own; keep the channel quiet.
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val FOCUS_MINUTES = 25
        const val BREAK_MINUTES = 5
        const val LONG_BREAK_MINUTES = 15
        const val SESSIONS_PER_CYCLE = 4

        private const val CHANNEL_ID = "timer"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.sient.mytimer.action.START"
        private const val ACTION_START_POMODORO = "com.sient.mytimer.action.START_POMODORO"
        private const val ACTION_STOP = "com.sient.mytimer.action.STOP"
        private const val ACTION_PHASE_END = "com.sient.mytimer.action.PHASE_END"
        private const val TAG = "TimerService"
        private const val ALERT_WAKE_MILLIS = 5_000L
        private const val EXTRA_MINUTES = "minutes"

        private val _state = MutableStateFlow<TimerSnapshot?>(null)
        val state: StateFlow<TimerSnapshot?> = _state

        fun start(context: Context, minutes: Int) {
            context.startForegroundService(
                Intent(context, TimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_MINUTES, minutes)
            )
        }

        fun startPomodoro(context: Context, workMinutes: Int) {
            context.startForegroundService(
                Intent(context, TimerService::class.java)
                    .setAction(ACTION_START_POMODORO)
                    .putExtra(EXTRA_MINUTES, workMinutes)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TimerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

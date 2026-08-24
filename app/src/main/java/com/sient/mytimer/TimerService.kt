package com.sient.mytimer

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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.sient.mytimer.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TimerSnapshot(
    val minutes: Int,
    val totalMillis: Long,
    val endElapsedRealtime: Long,
    val finished: Boolean,
)

/**
 * Foreground service that owns the countdown, so the timer keeps running
 * when the user returns to the watch face or opens another app.
 * The UI observes [state] and sends commands via [start] / [stop].
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer(intent.getIntExtra(EXTRA_MINUTES, 5))
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(minutes: Int) {
        countdownJob?.cancel()
        vibrator?.cancel()

        val totalMillis = minutes * 60_000L
        val end = SystemClock.elapsedRealtime() + totalMillis
        _state.value = TimerSnapshot(minutes, totalMillis, end, finished = false)

        createChannel()
        val builder = countdownNotification(minutes, totalMillis)
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_timer_notification)
            .setTouchIntent(tapIntent())
            .setStatus(
                Status.Builder()
                    .addTemplate("#left# left")
                    .addPart("left", Status.TimerPart(end))
                    .build()
            )
            .build()
            .apply(applicationContext)
        startForeground(NOTIFICATION_ID, builder.build())

        countdownJob = scope.launch {
            delay(totalMillis)
            onTimerFinished()
        }
    }

    private fun onTimerFinished() {
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
        countdownJob?.cancel()
        vibrator?.cancel()
        _state.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        vibrator?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun tapIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun countdownNotification(minutes: Int, millisLeft: Long): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("Timer running")
            .setContentText("$minutes minute timer")
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
        private const val CHANNEL_ID = "timer"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.sient.mytimer.action.START"
        private const val ACTION_STOP = "com.sient.mytimer.action.STOP"
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

        fun stop(context: Context) {
            context.startService(
                Intent(context, TimerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

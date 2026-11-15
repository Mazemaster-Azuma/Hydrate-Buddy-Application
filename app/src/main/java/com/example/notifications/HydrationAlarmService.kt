package com.example.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.R
import com.example.model.MeasurementUnit
import com.example.utils.SoundFeedbackPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HydrationAlarmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var fallbackAudioJob: Job? = null
    private var autoStopJob: Job? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userName = intent?.getStringExtra(EXTRA_USER_NAME).orEmpty()
        val totalMl = intent?.getIntExtra(EXTRA_TOTAL_ML, 0) ?: 0
        val goalMl = intent?.getIntExtra(EXTRA_GOAL_ML, 2000) ?: 2000
        val unit = MeasurementUnit.fromName(intent?.getStringExtra(EXTRA_UNIT_NAME) ?: MeasurementUnit.ML.name)
        val vibrationEnabled = intent?.getBooleanExtra(EXTRA_VIBRATION_ENABLED, true) ?: true

        NotificationHelper.ensureChannel(this)
        val notification = NotificationHelper.buildAlarmNotification(
            context = this,
            userName = userName,
            totalMl = totalMl,
            goalMl = goalMl,
            unit = unit,
            silent = true
        )
        startForeground(NotificationHelper.ALARM_NOTIFICATION_ID, notification)
        NotificationHelper.wakeUpScreen(this, durationMillis = 30_000L)

        startAlarmSound()
        if (vibrationEnabled) {
            startAlarmVibration()
        }

        // Safety auto-stop after 60 seconds if unanswered
        autoStopJob?.cancel()
        autoStopJob = serviceScope.launch {
            delay(60_000L)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startAlarmSound() {
        stopAlarmSound()

        val rawStarted = runCatching {
            val afd = resources.openRawResourceFd(R.raw.hydrate_alarm_chime) ?: return@runCatching false
            val player = MediaPlayer()
            afd.use { descriptor ->
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                player.isLooping = true
                player.prepare()
                player.start()
            }
            mediaPlayer = player
            true
        }.getOrDefault(false)

        if (rawStarted) return

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val playerStarted = if (uri != null) {
            runCatching {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(applicationContext, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                mediaPlayer = player
                true
            }.getOrDefault(false)
        } else {
            false
        }

        if (!playerStarted) {
            fallbackAudioJob = serviceScope.launch(Dispatchers.Default) {
                while (isActive) {
                    SoundFeedbackPlayer.playAlarmPulse()
                    delay(450L)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startAlarmVibration() {
        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib
            if (!vib.hasVibrator()) return

            val pattern = longArrayOf(0L, 450L, 350L, 450L, 850L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                vib.vibrate(pattern, 0)
            }
        }
    }

    private fun stopAlarmSound() {
        fallbackAudioJob?.cancel()
        fallbackAudioJob = null
        runCatching {
            mediaPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        mediaPlayer = null
    }

    private fun stopAlarmVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    override fun onDestroy() {
        autoStopJob?.cancel()
        stopAlarmSound()
        stopAlarmVibration()
        AlarmRingController.clearStateOnly()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_TOTAL_ML = "extra_total_ml"
        const val EXTRA_GOAL_ML = "extra_goal_ml"
        const val EXTRA_UNIT_NAME = "extra_unit_name"
        const val EXTRA_VIBRATION_ENABLED = "extra_vibration_enabled"
    }
}

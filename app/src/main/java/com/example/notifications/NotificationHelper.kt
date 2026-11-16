package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.MainActivity
import com.example.R
import com.example.model.MeasurementUnit
import com.example.utils.UnitConverter

object NotificationHelper {
    const val CHANNEL_ID = "hydrate_reminders_v3"
    const val ALARM_CHANNEL_ID = "hydrate_alarm_reminders_v2"
    const val ALARM_SILENT_RING_CHANNEL_ID = "hydrate_alarm_screen_v1"
    const val NOTIFICATION_ID = 1001
    const val ALARM_NOTIFICATION_ID = 1002

    const val ACTION_QUICK_LOG = "com.example.ACTION_QUICK_LOG"
    const val ACTION_DISMISS = "com.example.ACTION_DISMISS"
    const val ACTION_ALARM_QUICK_LOG = "com.example.ACTION_ALARM_QUICK_LOG"
    const val ACTION_STOP_ALARM = "com.example.ACTION_STOP_ALARM"
    const val ACTION_SNOOZE_ALARM = "com.example.ACTION_SNOOZE_ALARM"
    const val EXTRA_AMOUNT_ML = "extra_amount_ml"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Clean up legacy channels so devices receive the custom water chimes & high-visibility alerts
            manager.deleteNotificationChannel("hydrate_reminders")
            manager.deleteNotificationChannel("hydrate_reminders_v2")
            manager.deleteNotificationChannel("hydrate_alarm_reminders")

            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val dropUri = "android.resource://${context.packageName}/${R.raw.hydrate_water_drop}".toUri()
                val dropAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    setSound(dropUri, dropAttributes)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }

            if (manager.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
                val alarmUri = "android.resource://${context.packageName}/${R.raw.hydrate_alarm_chime}".toUri()
                val alarmAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val alarmChannel = NotificationChannel(
                    ALARM_CHANNEL_ID,
                    "Ringing Hydration Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Water-harp alarm chime reminders when Ringing Alarm Mode is enabled."
                    setSound(alarmUri, alarmAttributes)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 450L, 350L, 450L, 850L)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(alarmChannel)
            }

            if (manager.getNotificationChannel(ALARM_SILENT_RING_CHANNEL_ID) == null) {
                val screenAlarmChannel = NotificationChannel(
                    ALARM_SILENT_RING_CHANNEL_ID,
                    "Active Hydration Alarm Screen",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Wakes the screen and shows alarm controls while the hydration chime is ringing."
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(screenAlarmChannel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.canUseFullScreenIntent() == true
        } else {
            true
        }
    }

    fun showHydrationReminder(
        context: Context,
        userName: String,
        totalMl: Int,
        goalMl: Int,
        unit: MeasurementUnit,
        silent: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val quickAmountMl = if (unit == MeasurementUnit.OZ) 237 else 250
        val quickLabel = UnitConverter.quickAddButtonLabel(quickAmountMl, unit)
        val remainingMl = (goalMl - totalMl).coerceAtLeast(0)

        val greetingPrefix = if (userName.isNotBlank()) "Hey ${userName.trim()}! " else ""
        val title = "${greetingPrefix}Droplet is ready for a sip"
        val body = if (remainingMl > 0) {
            "You're at ${UnitConverter.formatVolume(totalMl, unit)} of ${UnitConverter.formatVolume(goalMl, unit)}. ${UnitConverter.formatVolume(remainingMl, unit)} left today!"
        } else {
            "Great hydration today! Keep your buddy smiling."
        }

        val openPendingIntent = createOpenAppPendingIntent(context, requestCode = 100)

        val quickLogIntent = Intent(context, HydrationNotificationReceiver::class.java).apply {
            action = ACTION_QUICK_LOG
            putExtra(EXTRA_AMOUNT_ML, quickAmountMl)
        }
        val quickLogPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            quickLogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, HydrationNotificationReceiver::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setSilent(silent)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(
                0,
                context.getString(R.string.notification_action_log_250, quickLabel),
                quickLogPendingIntent
            )
            .addAction(
                0,
                context.getString(R.string.notification_action_dismiss),
                dismissPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun buildAlarmNotification(
        context: Context,
        userName: String,
        totalMl: Int,
        goalMl: Int,
        unit: MeasurementUnit,
        useSilentAudioChannel: Boolean = false
    ): Notification {
        ensureChannel(context)
        val quickAmountMl = if (unit == MeasurementUnit.OZ) 237 else 250
        val quickLabel = UnitConverter.quickAddButtonLabel(quickAmountMl, unit)
        val remainingMl = (goalMl - totalMl).coerceAtLeast(0)

        val greetingPrefix = if (userName.isNotBlank()) "${userName.trim()}, " else ""
        val title = "${greetingPrefix}Time to drink water!"
        val body = if (remainingMl > 0) {
            "Hydration alarm ringing • ${UnitConverter.formatVolume(remainingMl, unit)} remaining today."
        } else {
            "Hydration alarm ringing • Tap to log or stop."
        }

        val openPendingIntent = createOpenAppPendingIntent(context, requestCode = 200)
        val fullScreenPendingIntent = createFullScreenAlarmPendingIntent(context, requestCode = 204)

        val logAndStopIntent = Intent(context, HydrationNotificationReceiver::class.java).apply {
            action = ACTION_ALARM_QUICK_LOG
            putExtra(EXTRA_AMOUNT_ML, quickAmountMl)
        }
        val logAndStopPendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            logAndStopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, HydrationNotificationReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, HydrationNotificationReceiver::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            203,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (useSilentAudioChannel) ALARM_SILENT_RING_CHANNEL_ID else ALARM_CHANNEL_ID

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_water_drop_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setDeleteIntent(stopPendingIntent)
            .addAction(0, "+$quickLabel", logAndStopPendingIntent)
            .addAction(0, "Snooze 10m", snoozePendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    fun showInsistentAlarmNotification(
        context: Context,
        userName: String,
        totalMl: Int,
        goalMl: Int,
        unit: MeasurementUnit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = buildAlarmNotification(
            context = context,
            userName = userName,
            totalMl = totalMl,
            goalMl = goalMl,
            unit = unit,
            useSilentAudioChannel = false
        ).apply {
            flags = flags or Notification.FLAG_INSISTENT
        }
        try {
            NotificationManagerCompat.from(context).notify(ALARM_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    private fun createOpenAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createFullScreenAlarmPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val alarmIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancelReminderNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun cancelAlarmNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(ALARM_NOTIFICATION_ID)
    }
}

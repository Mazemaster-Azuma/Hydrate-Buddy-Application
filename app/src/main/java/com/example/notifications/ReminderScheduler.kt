package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.AlarmManagerCompat
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReminderScheduler {
    private const val LEGACY_UNIQUE_WORK_NAME = "hydrate_buddy_periodic_reminder"
    private const val LEGACY_SNOOZE_WORK_NAME = "hydrate_buddy_snooze_reminder"

    private const val PREFS_NAME = "hydrate_reminder_schedule_v1"
    private const val KEY_NEXT_TRIGGER_MS = "next_trigger_ms"
    private const val KEY_SCHEDULE_SIGNATURE = "schedule_signature"
    private const val KEY_NEXT_SNOOZE_MS = "next_snooze_ms"
    private const val KEY_RINGING_MODE = "ringing_mode"

    private const val PERIODIC_REQUEST_CODE = 4101
    private const val SNOOZE_REQUEST_CODE = 4102
    private const val SHOW_APP_REQUEST_CODE = 4103

    const val ACTION_TRIGGER_REMINDER = "com.example.ACTION_TRIGGER_REMINDER"
    const val ACTION_TRIGGER_SNOOZE = "com.example.ACTION_TRIGGER_SNOOZE"

    @VisibleForTesting
    internal var exactAlarmOverrideForTesting: Boolean? = null

    private val _nextScheduledTriggerMillis = MutableStateFlow<Long?>(null)
    val nextScheduledTriggerMillis: StateFlow<Long?> = _nextScheduledTriggerMillis.asStateFlow()

    fun canScheduleExactAlarms(context: Context): Boolean {
        exactAlarmOverrideForTesting?.let { return it }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    @Synchronized
    fun getNextScheduledReminderMillis(context: Context): Long? {
        val stored = prefs(context).getLong(KEY_NEXT_TRIGGER_MS, 0L)
        val valid = stored.takeIf { it > System.currentTimeMillis() }
        _nextScheduledTriggerMillis.value = valid
        return valid
    }

    @Synchronized
    fun syncReminders(
        context: Context,
        prefs: UserPreferences,
        forceReschedule: Boolean = false
    ) {
        cancelLegacyWorkManager(context)

        if (!prefs.remindersEnabled || !prefs.onboardingCompleted) {
            cancelReminders(context)
            return
        }

        val now = System.currentTimeMillis()
        val store = prefs(context)
        val newSignature = ReminderScheduleCalculator.formatScheduleSignature(
            intervalMinutes = prefs.reminderIntervalMinutes,
            wakeHour = prefs.wakeHour,
            wakeMinute = prefs.wakeMinute,
            bedHour = prefs.bedHour,
            bedMinute = prefs.bedMinute
        )
        val savedSignature = store.getString(KEY_SCHEDULE_SIGNATURE, null)
        val savedNextMs = store.getLong(KEY_NEXT_TRIGGER_MS, 0L)

        val targetTriggerMs = if (!forceReschedule && savedSignature == newSignature && savedNextMs > now + 2_000L) {
            savedNextMs
        } else {
            val anchor = if (!forceReschedule && savedSignature == newSignature && savedNextMs > 0L) {
                savedNextMs
            } else {
                null
            }
            ReminderScheduleCalculator.computeNextReminderTriggerMillis(
                nowMillis = now,
                intervalMinutes = prefs.reminderIntervalMinutes,
                wakeHour = prefs.wakeHour,
                wakeMinute = prefs.wakeMinute,
                bedHour = prefs.bedHour,
                bedMinute = prefs.bedMinute,
                previousTriggerAtMillis = anchor
            )
        }

        store.edit()
            .putString(KEY_SCHEDULE_SIGNATURE, newSignature)
            .putLong(KEY_NEXT_TRIGGER_MS, targetTriggerMs)
            .putBoolean(KEY_RINGING_MODE, prefs.ringingReminderEnabled)
            .apply()
        _nextScheduledTriggerMillis.value = targetTriggerMs

        armAlarm(
            context = context,
            requestCode = PERIODIC_REQUEST_CODE,
            action = ACTION_TRIGGER_REMINDER,
            triggerAtMillis = targetTriggerMs,
            useAlarmClock = prefs.ringingReminderEnabled
        )
    }

    @Synchronized
    fun scheduleNextAfterTrigger(
        context: Context,
        prefs: UserPreferences,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (!prefs.remindersEnabled || !prefs.onboardingCompleted) {
            cancelReminders(context)
            return 0L
        }

        val store = prefs(context)
        val newSignature = ReminderScheduleCalculator.formatScheduleSignature(
            intervalMinutes = prefs.reminderIntervalMinutes,
            wakeHour = prefs.wakeHour,
            wakeMinute = prefs.wakeMinute,
            bedHour = prefs.bedHour,
            bedMinute = prefs.bedMinute
        )
        val savedSignature = store.getString(KEY_SCHEDULE_SIGNATURE, null)
        val savedNextMs = store.getLong(KEY_NEXT_TRIGGER_MS, 0L)
        val anchor = if (savedSignature == newSignature && savedNextMs > 0L) savedNextMs else null

        val nextTriggerMs = ReminderScheduleCalculator.computeNextReminderTriggerMillis(
            nowMillis = nowMillis,
            intervalMinutes = prefs.reminderIntervalMinutes,
            wakeHour = prefs.wakeHour,
            wakeMinute = prefs.wakeMinute,
            bedHour = prefs.bedHour,
            bedMinute = prefs.bedMinute,
            previousTriggerAtMillis = anchor
        )

        store.edit()
            .putString(KEY_SCHEDULE_SIGNATURE, newSignature)
            .putLong(KEY_NEXT_TRIGGER_MS, nextTriggerMs)
            .putBoolean(KEY_RINGING_MODE, prefs.ringingReminderEnabled)
            .apply()
        _nextScheduledTriggerMillis.value = nextTriggerMs

        armAlarm(
            context = context,
            requestCode = PERIODIC_REQUEST_CODE,
            action = ACTION_TRIGGER_REMINDER,
            triggerAtMillis = nextTriggerMs,
            useAlarmClock = prefs.ringingReminderEnabled
        )
        return nextTriggerMs
    }

    @Synchronized
    fun scheduleSnoozeReminder(
        context: Context,
        snoozeMinutes: Int = 10,
        ringingMode: Boolean = prefs(context).getBoolean(KEY_RINGING_MODE, true)
    ) {
        val snoozeAtMs = ReminderScheduleCalculator.computeSnoozeTriggerMillis(
            nowMillis = System.currentTimeMillis(),
            snoozeMinutes = snoozeMinutes
        )
        prefs(context).edit()
            .putLong(KEY_NEXT_SNOOZE_MS, snoozeAtMs)
            .apply()

        armAlarm(
            context = context,
            requestCode = SNOOZE_REQUEST_CODE,
            action = ACTION_TRIGGER_SNOOZE,
            triggerAtMillis = snoozeAtMs,
            useAlarmClock = ringingMode
        )
    }

    @Synchronized
    fun rearmPendingSnoozeIfNeeded(context: Context, ringingMode: Boolean) {
        val snoozeAtMs = prefs(context).getLong(KEY_NEXT_SNOOZE_MS, 0L)
        if (snoozeAtMs > System.currentTimeMillis() + 2_000L) {
            armAlarm(
                context = context,
                requestCode = SNOOZE_REQUEST_CODE,
                action = ACTION_TRIGGER_SNOOZE,
                triggerAtMillis = snoozeAtMs,
                useAlarmClock = ringingMode
            )
        } else if (snoozeAtMs > 0L) {
            clearSnoozeTimestamp(context)
        }
    }

    @Synchronized
    fun clearSnoozeTimestamp(context: Context) {
        prefs(context).edit().remove(KEY_NEXT_SNOOZE_MS).apply()
    }

    @Synchronized
    fun cancelSnoozeReminder(context: Context) {
        clearSnoozeTimestamp(context)
        cancelPendingAlarm(context, SNOOZE_REQUEST_CODE, ACTION_TRIGGER_SNOOZE)
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_SNOOZE_WORK_NAME)
        }
    }

    @Synchronized
    fun cancelReminders(context: Context) {
        cancelLegacyWorkManager(context)
        cancelPendingAlarm(context, PERIODIC_REQUEST_CODE, ACTION_TRIGGER_REMINDER)
        cancelPendingAlarm(context, SNOOZE_REQUEST_CODE, ACTION_TRIGGER_SNOOZE)
        prefs(context).edit()
            .remove(KEY_NEXT_TRIGGER_MS)
            .remove(KEY_SCHEDULE_SIGNATURE)
            .remove(KEY_NEXT_SNOOZE_MS)
            .apply()
        _nextScheduledTriggerMillis.value = null
    }

    private fun armAlarm(
        context: Context,
        requestCode: Int,
        action: String,
        triggerAtMillis: Long,
        useAlarmClock: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val operation = createTriggerPendingIntent(context, requestCode, action)
        val canExact = canScheduleExactAlarms(context)

        val scheduledExact = if (canExact) {
            runCatching {
                if (useAlarmClock) {
                    val showIntent = createShowAppPendingIntent(context)
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                        operation
                    )
                } else {
                    AlarmManagerCompat.setExactAndAllowWhileIdle(
                        alarmManager,
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operation
                    )
                }
                true
            }.getOrDefault(false)
        } else {
            false
        }

        if (!scheduledExact) {
            runCatching {
                AlarmManagerCompat.setAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
            }
        }
    }

    private fun cancelPendingAlarm(context: Context, requestCode: Int, action: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, HydrationNotificationReceiver::class.java).apply {
            this.action = action
        }
        val existing = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existing != null) {
            alarmManager.cancel(existing)
            existing.cancel()
        }
    }

    private fun createTriggerPendingIntent(
        context: Context,
        requestCode: Int,
        action: String
    ): PendingIntent {
        val intent = Intent(context, HydrationNotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createShowAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            SHOW_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelLegacyWorkManager(context: Context) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(LEGACY_UNIQUE_WORK_NAME)
            wm.cancelUniqueWork(LEGACY_SNOOZE_WORK_NAME)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

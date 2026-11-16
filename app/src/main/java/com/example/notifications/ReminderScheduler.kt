package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.AlarmManagerCompat
import androidx.work.WorkManager
import com.example.model.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

object ReminderScheduler {
    private const val LEGACY_UNIQUE_WORK_NAME = "hydrate_buddy_periodic_reminder"
    private const val LEGACY_SNOOZE_WORK_NAME = "hydrate_buddy_snooze_reminder"

    private const val PREFS_NAME = "hydrate_reminder_schedule_v1"
    private const val KEY_NEXT_TRIGGER_MS = "next_trigger_ms"
    private const val KEY_SCHEDULE_SIGNATURE = "schedule_signature"
    private const val KEY_NEXT_SNOOZE_MS = "next_snooze_ms"
    private const val KEY_RINGING_MODE = "ringing_mode"
    private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    private const val KEY_INTERVAL_MINUTES = "interval_minutes"
    private const val KEY_WAKE_HOUR = "wake_hour"
    private const val KEY_WAKE_MINUTE = "wake_minute"
    private const val KEY_BED_HOUR = "bed_hour"
    private const val KEY_BED_MINUTE = "bed_minute"
    private const val KEY_LAST_CLAIMED_MS = "last_claimed_ms"
    private const val KEY_LAST_SNOOZE_CLAIMED_MS = "last_snooze_claimed_ms"

    private const val PERIODIC_REQUEST_CODE = 4101
    private const val SNOOZE_REQUEST_CODE = 4102

    const val ACTION_TRIGGER_REMINDER = "com.example.ACTION_TRIGGER_REMINDER"
    const val ACTION_TRIGGER_SNOOZE = "com.example.ACTION_TRIGGER_SNOOZE"

    @VisibleForTesting
    internal var exactAlarmOverrideForTesting: Boolean? = null

    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var inProcessReminderJob: Job? = null
    private var inProcessSnoozeJob: Job? = null

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
        val appContext = context.applicationContext
        cancelLegacyWorkManager(appContext)

        if (!prefs.remindersEnabled || !prefs.onboardingCompleted) {
            cancelReminders(appContext)
            return
        }

        val now = System.currentTimeMillis()
        val store = prefs(appContext)
        val newSignature = ReminderScheduleCalculator.formatScheduleSignature(
            intervalMinutes = prefs.reminderIntervalMinutes,
            wakeHour = prefs.wakeHour,
            wakeMinute = prefs.wakeMinute,
            bedHour = prefs.bedHour,
            bedMinute = prefs.bedMinute
        )
        val savedSignature = store.getString(KEY_SCHEDULE_SIGNATURE, null)
        val savedNextMs = store.getLong(KEY_NEXT_TRIGGER_MS, 0L)
        val intervalMillis = prefs.reminderIntervalMinutes
            .coerceAtLeast(ReminderScheduleCalculator.MIN_INTERVAL_MINUTES) * 60_000L

        var fireOverdueNow = false
        val targetTriggerMs = when {
            !forceReschedule && savedSignature == newSignature && savedNextMs > now -> {
                // Active future alarm with unchanged schedule: keep exact target without resetting!
                savedNextMs
            }
            !forceReschedule && savedSignature == newSignature && savedNextMs in 1..now -> {
                // Alarm time passed while device was asleep/dozing: fire overdue if within 2 intervals
                if (now - savedNextMs <= intervalMillis * 2L) {
                    fireOverdueNow = true
                }
                ReminderScheduleCalculator.computeNextReminderTriggerMillis(
                    nowMillis = now,
                    intervalMinutes = prefs.reminderIntervalMinutes,
                    wakeHour = prefs.wakeHour,
                    wakeMinute = prefs.wakeMinute,
                    bedHour = prefs.bedHour,
                    bedMinute = prefs.bedMinute,
                    previousTriggerAtMillis = savedNextMs
                )
            }
            else -> {
                ReminderScheduleCalculator.computeNextReminderTriggerMillis(
                    nowMillis = now,
                    intervalMinutes = prefs.reminderIntervalMinutes,
                    wakeHour = prefs.wakeHour,
                    wakeMinute = prefs.wakeMinute,
                    bedHour = prefs.bedHour,
                    bedMinute = prefs.bedMinute,
                    previousTriggerAtMillis = null
                )
            }
        }

        persistScheduleMetadata(store, prefs, newSignature, targetTriggerMs)
        _nextScheduledTriggerMillis.value = targetTriggerMs

        armAlarm(
            context = appContext,
            requestCode = PERIODIC_REQUEST_CODE,
            action = ACTION_TRIGGER_REMINDER,
            triggerAtMillis = targetTriggerMs
        )
        armInProcessReminderTimer(appContext, targetTriggerMs)

        if (fireOverdueNow) {
            val lastClaimed = store.getLong(KEY_LAST_CLAIMED_MS, 0L)
            if (lastClaimed <= 0L || abs(now - lastClaimed) >= 4_000L) {
                store.edit().putLong(KEY_LAST_CLAIMED_MS, now).apply()
                HydrationNotificationReceiver.deliverAsync(appContext, isSnooze = false)
            }
        }
    }

    @Synchronized
    fun claimDueReminderAndScheduleNext(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val appContext = context.applicationContext
        val store = prefs(appContext)
        val enabled = store.getBoolean(KEY_REMINDERS_ENABLED, true)
        val onboarded = store.getBoolean(KEY_ONBOARDING_COMPLETED, true)
        if (!enabled || !onboarded) {
            cancelReminders(appContext)
            return false
        }

        val lastClaimed = store.getLong(KEY_LAST_CLAIMED_MS, 0L)
        if (lastClaimed > 0L && abs(nowMillis - lastClaimed) < 4_000L) {
            return false
        }

        val savedNextMs = store.getLong(KEY_NEXT_TRIGGER_MS, 0L)
        if (savedNextMs > nowMillis + 2_500L) {
            // Next slot is already in the future (>2.5s ahead) — already claimed
            return false
        }

        store.edit().putLong(KEY_LAST_CLAIMED_MS, nowMillis).apply()

        val cachedPrefs = UserPreferences(
            onboardingCompleted = onboarded,
            remindersEnabled = enabled,
            ringingReminderEnabled = store.getBoolean(KEY_RINGING_MODE, false),
            reminderIntervalMinutes = store.getInt(KEY_INTERVAL_MINUTES, 90),
            wakeHour = store.getInt(KEY_WAKE_HOUR, 7),
            wakeMinute = store.getInt(KEY_WAKE_MINUTE, 0),
            bedHour = store.getInt(KEY_BED_HOUR, 22),
            bedMinute = store.getInt(KEY_BED_MINUTE, 30)
        )
        scheduleNextAfterTrigger(appContext, cachedPrefs, nowMillis)
        return true
    }

    @Synchronized
    fun claimDueSnooze(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val appContext = context.applicationContext
        val store = prefs(appContext)
        val lastSnoozeClaimed = store.getLong(KEY_LAST_SNOOZE_CLAIMED_MS, 0L)
        if (lastSnoozeClaimed > 0L && abs(nowMillis - lastSnoozeClaimed) < 4_000L) {
            return false
        }
        val snoozeAtMs = store.getLong(KEY_NEXT_SNOOZE_MS, 0L)
        if (snoozeAtMs <= 0L || snoozeAtMs > nowMillis + 2_500L) {
            return false
        }
        store.edit()
            .putLong(KEY_LAST_SNOOZE_CLAIMED_MS, nowMillis)
            .remove(KEY_NEXT_SNOOZE_MS)
            .apply()
        inProcessSnoozeJob?.cancel()
        inProcessSnoozeJob = null
        return true
    }

    @Synchronized
    fun scheduleNextAfterTrigger(
        context: Context,
        prefs: UserPreferences,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val appContext = context.applicationContext
        if (!prefs.remindersEnabled || !prefs.onboardingCompleted) {
            cancelReminders(appContext)
            return 0L
        }

        val store = prefs(appContext)
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

        persistScheduleMetadata(store, prefs, newSignature, nextTriggerMs)
        _nextScheduledTriggerMillis.value = nextTriggerMs

        armAlarm(
            context = appContext,
            requestCode = PERIODIC_REQUEST_CODE,
            action = ACTION_TRIGGER_REMINDER,
            triggerAtMillis = nextTriggerMs
        )
        armInProcessReminderTimer(appContext, nextTriggerMs)
        return nextTriggerMs
    }

    @Synchronized
    fun scheduleSnoozeReminder(
        context: Context,
        snoozeMinutes: Int = 10,
        ringingMode: Boolean = prefs(context).getBoolean(KEY_RINGING_MODE, true)
    ) {
        val appContext = context.applicationContext
        val snoozeAtMs = ReminderScheduleCalculator.computeSnoozeTriggerMillis(
            nowMillis = System.currentTimeMillis(),
            snoozeMinutes = snoozeMinutes
        )
        prefs(appContext).edit()
            .putLong(KEY_NEXT_SNOOZE_MS, snoozeAtMs)
            .putBoolean(KEY_RINGING_MODE, ringingMode)
            .apply()

        armAlarm(
            context = appContext,
            requestCode = SNOOZE_REQUEST_CODE,
            action = ACTION_TRIGGER_SNOOZE,
            triggerAtMillis = snoozeAtMs
        )
        armInProcessSnoozeTimer(appContext, snoozeAtMs)
    }

    @Synchronized
    fun rearmPendingSnoozeIfNeeded(context: Context, ringingMode: Boolean) {
        val appContext = context.applicationContext
        val snoozeAtMs = prefs(appContext).getLong(KEY_NEXT_SNOOZE_MS, 0L)
        val now = System.currentTimeMillis()
        if (snoozeAtMs > now) {
            armAlarm(
                context = appContext,
                requestCode = SNOOZE_REQUEST_CODE,
                action = ACTION_TRIGGER_SNOOZE,
                triggerAtMillis = snoozeAtMs
            )
            armInProcessSnoozeTimer(appContext, snoozeAtMs)
        } else if (snoozeAtMs > 0L) {
            clearSnoozeTimestamp(appContext)
        }
    }

    @Synchronized
    fun clearSnoozeTimestamp(context: Context) {
        prefs(context.applicationContext).edit().remove(KEY_NEXT_SNOOZE_MS).apply()
        inProcessSnoozeJob?.cancel()
        inProcessSnoozeJob = null
    }

    @Synchronized
    fun cancelSnoozeReminder(context: Context) {
        val appContext = context.applicationContext
        clearSnoozeTimestamp(appContext)
        cancelPendingAlarm(appContext, SNOOZE_REQUEST_CODE, ACTION_TRIGGER_SNOOZE)
        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(LEGACY_SNOOZE_WORK_NAME)
        }
    }

    @Synchronized
    fun cancelReminders(context: Context) {
        val appContext = context.applicationContext
        inProcessReminderJob?.cancel()
        inProcessReminderJob = null
        inProcessSnoozeJob?.cancel()
        inProcessSnoozeJob = null
        cancelLegacyWorkManager(appContext)
        cancelPendingAlarm(appContext, PERIODIC_REQUEST_CODE, ACTION_TRIGGER_REMINDER)
        cancelPendingAlarm(appContext, SNOOZE_REQUEST_CODE, ACTION_TRIGGER_SNOOZE)
        prefs(appContext).edit()
            .remove(KEY_NEXT_TRIGGER_MS)
            .remove(KEY_SCHEDULE_SIGNATURE)
            .remove(KEY_NEXT_SNOOZE_MS)
            .putBoolean(KEY_REMINDERS_ENABLED, false)
            .apply()
        _nextScheduledTriggerMillis.value = null
    }

    private fun persistScheduleMetadata(
        store: android.content.SharedPreferences,
        prefs: UserPreferences,
        signature: String,
        targetTriggerMs: Long
    ) {
        store.edit()
            .putString(KEY_SCHEDULE_SIGNATURE, signature)
            .putLong(KEY_NEXT_TRIGGER_MS, targetTriggerMs)
            .putBoolean(KEY_RINGING_MODE, prefs.ringingReminderEnabled)
            .putBoolean(KEY_REMINDERS_ENABLED, prefs.remindersEnabled)
            .putBoolean(KEY_ONBOARDING_COMPLETED, prefs.onboardingCompleted)
            .putInt(KEY_INTERVAL_MINUTES, prefs.reminderIntervalMinutes)
            .putInt(KEY_WAKE_HOUR, prefs.wakeHour)
            .putInt(KEY_WAKE_MINUTE, prefs.wakeMinute)
            .putInt(KEY_BED_HOUR, prefs.bedHour)
            .putInt(KEY_BED_MINUTE, prefs.bedMinute)
            .apply()
    }

    private fun armInProcessReminderTimer(context: Context, triggerAtMillis: Long) {
        inProcessReminderJob?.cancel()
        val delayMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(100L)
        inProcessReminderJob = schedulerScope.launch {
            delay(delayMs)
            if (claimDueReminderAndScheduleNext(context, System.currentTimeMillis())) {
                HydrationNotificationReceiver.deliverAsync(context, isSnooze = false)
            }
        }
    }

    private fun armInProcessSnoozeTimer(context: Context, snoozeAtMillis: Long) {
        inProcessSnoozeJob?.cancel()
        val delayMs = (snoozeAtMillis - System.currentTimeMillis()).coerceAtLeast(100L)
        inProcessSnoozeJob = schedulerScope.launch {
            delay(delayMs)
            if (claimDueSnooze(context, System.currentTimeMillis())) {
                HydrationNotificationReceiver.deliverAsync(context, isSnooze = true)
            }
        }
    }

    private fun armAlarm(
        context: Context,
        requestCode: Int,
        action: String,
        triggerAtMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val operation = createTriggerPendingIntent(context, requestCode, action)
        // Clear any prior alarm (including any legacy setAlarmClock entry) before setting the new trigger
        runCatching { alarmManager.cancel(operation) }

        val canExact = canScheduleExactAlarms(context)
        val scheduledExact = if (canExact) {
            runCatching {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
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
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
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
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
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

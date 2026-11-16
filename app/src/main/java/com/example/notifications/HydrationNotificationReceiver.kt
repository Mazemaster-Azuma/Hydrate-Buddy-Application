package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.HydrateBuddyApplication
import com.example.model.DrinkType
import com.example.repository.HydrationRepository
import com.example.utils.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HydrationNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ReminderScheduler.ACTION_TRIGGER_REMINDER -> {
                val now = System.currentTimeMillis()
                // Claim slot and arm next alarm synchronously while AlarmManager holds CPU wake lock
                val claimed = ReminderScheduler.claimDueReminderAndScheduleNext(appContext, now)
                if (!claimed) return

                val wakeLock = acquireCpuWakeLock(appContext)
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val app = appContext as? HydrateBuddyApplication ?: return@launch
                        val repository = app.repository
                        val prefs = repository.getCurrentPreferences()
                        if (!prefs.onboardingCompleted || !prefs.remindersEnabled) {
                            ReminderScheduler.cancelReminders(appContext)
                            return@launch
                        }
                        // Ensure schedule signature stays aligned with latest DataStore preferences
                        ReminderScheduler.syncReminders(appContext, prefs, forceReschedule = false)
                        deliverReminderIfEligible(
                            context = appContext,
                            repository = repository,
                            nowMillis = now,
                            isSnooze = false
                        )
                    } finally {
                        releaseWakeLock(wakeLock)
                        pendingResult.finish()
                    }
                }
            }
            ReminderScheduler.ACTION_TRIGGER_SNOOZE -> {
                val now = System.currentTimeMillis()
                val claimed = ReminderScheduler.claimDueSnooze(appContext, now)
                if (!claimed) return

                val wakeLock = acquireCpuWakeLock(appContext)
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val app = appContext as? HydrateBuddyApplication ?: return@launch
                        deliverReminderIfEligible(
                            context = appContext,
                            repository = app.repository,
                            nowMillis = now,
                            isSnooze = true
                        )
                    } finally {
                        releaseWakeLock(wakeLock)
                        pendingResult.finish()
                    }
                }
            }
            NotificationHelper.ACTION_DISMISS -> {
                NotificationHelper.cancelReminderNotification(appContext)
            }
            NotificationHelper.ACTION_STOP_ALARM -> {
                AlarmRingController.stopRinging(appContext)
                ReminderScheduler.cancelSnoozeReminder(appContext)
            }
            NotificationHelper.ACTION_SNOOZE_ALARM -> {
                AlarmRingController.stopRinging(appContext)
                ReminderScheduler.scheduleSnoozeReminder(appContext, snoozeMinutes = 10)
            }
            NotificationHelper.ACTION_QUICK_LOG,
            NotificationHelper.ACTION_ALARM_QUICK_LOG -> {
                val amountMl = intent.getIntExtra(NotificationHelper.EXTRA_AMOUNT_ML, 250)
                val isAlarm = intent.action == NotificationHelper.ACTION_ALARM_QUICK_LOG
                val wakeLock = acquireCpuWakeLock(appContext)
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val app = appContext as? HydrateBuddyApplication
                        app?.repository?.addWaterEntry(
                            amountMl = amountMl,
                            drinkType = DrinkType.WATER
                        )
                        ReminderScheduler.cancelSnoozeReminder(appContext)
                        if (isAlarm) {
                            AlarmRingController.stopRinging(appContext)
                        } else {
                            NotificationHelper.cancelReminderNotification(appContext)
                        }
                    } finally {
                        releaseWakeLock(wakeLock)
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun deliverAsync(context: Context, isSnooze: Boolean) {
            val appContext = context.applicationContext
            val wakeLock = acquireCpuWakeLock(appContext)
            receiverScope.launch {
                try {
                    val app = appContext as? HydrateBuddyApplication ?: return@launch
                    deliverReminderIfEligible(
                        context = appContext,
                        repository = app.repository,
                        nowMillis = System.currentTimeMillis(),
                        isSnooze = isSnooze
                    )
                } finally {
                    releaseWakeLock(wakeLock)
                }
            }
        }

        suspend fun deliverReminderIfEligible(
            context: Context,
            repository: HydrationRepository,
            nowMillis: Long = System.currentTimeMillis(),
            isSnooze: Boolean = false
        ) {
            val prefs = repository.getCurrentPreferences()
            if (!prefs.onboardingCompleted || !prefs.remindersEnabled) {
                return
            }

            if (!isSnooze) {
                val withinWaking = ReminderScheduleCalculator.isWithinWakingHours(
                    timestampMillis = nowMillis,
                    wakeHour = prefs.wakeHour,
                    wakeMinute = prefs.wakeMinute,
                    bedHour = prefs.bedHour,
                    bedMinute = prefs.bedMinute,
                    bedGraceMinutes = 15
                )
                if (!withinWaking) {
                    return
                }
            }

            val todayIso = DateUtils.todayIso(nowMillis)
            val todayTotal = repository.observeTotalMlForDate(todayIso).first()

            if (prefs.ringingReminderEnabled) {
                AlarmRingController.startRinging(
                    context = context,
                    userName = prefs.userName,
                    totalMl = todayTotal,
                    goalMl = prefs.dailyGoalMl,
                    unit = prefs.unit,
                    vibrationEnabled = prefs.vibrationEnabled
                )
            } else {
                NotificationHelper.showHydrationReminder(
                    context = context,
                    userName = prefs.userName,
                    totalMl = todayTotal,
                    goalMl = prefs.dailyGoalMl,
                    unit = prefs.unit,
                    silent = !prefs.soundEnabled && !prefs.vibrationEnabled
                )
            }
        }

        private fun acquireCpuWakeLock(context: Context): PowerManager.WakeLock? {
            return runCatching {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
                pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "HydrateBuddy:ReminderBroadcastWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(15_000L)
                }
            }.getOrNull()
        }

        private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
            runCatching {
                if (wakeLock != null && wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }
    }
}

package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.HydrateBuddyApplication
import com.example.model.DrinkType
import com.example.repository.HydrationRepository
import com.example.utils.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HydrationNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ReminderScheduler.ACTION_TRIGGER_REMINDER -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = appContext as? HydrateBuddyApplication ?: return@launch
                        val repository = app.repository
                        val prefs = repository.getCurrentPreferences()
                        if (!prefs.onboardingCompleted || !prefs.remindersEnabled) {
                            ReminderScheduler.cancelReminders(appContext)
                            return@launch
                        }
                        val now = System.currentTimeMillis()
                        ReminderScheduler.scheduleNextAfterTrigger(appContext, prefs, now)
                        deliverReminderIfEligible(appContext, repository, now)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ReminderScheduler.ACTION_TRIGGER_SNOOZE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ReminderScheduler.clearSnoozeTimestamp(appContext)
                        val app = appContext as? HydrateBuddyApplication ?: return@launch
                        deliverReminderIfEligible(
                            context = appContext,
                            repository = app.repository,
                            nowMillis = System.currentTimeMillis()
                        )
                    } finally {
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
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
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
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        suspend fun deliverReminderIfEligible(
            context: Context,
            repository: HydrationRepository,
            nowMillis: Long = System.currentTimeMillis()
        ) {
            val prefs = repository.getCurrentPreferences()
            if (!prefs.onboardingCompleted || !prefs.remindersEnabled) {
                return
            }

            val withinWaking = DateUtils.isWithinWakingHours(
                nowMillis = nowMillis,
                wakeHour = prefs.wakeHour,
                wakeMinute = prefs.wakeMinute,
                bedHour = prefs.bedHour,
                bedMinute = prefs.bedMinute
            )
            if (!withinWaking) {
                return
            }

            val todayIso = DateUtils.todayIso(nowMillis)
            val todayTotal = repository.observeTotalMlForDate(todayIso).first()
            if (todayTotal >= prefs.dailyGoalMl) {
                return
            }

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
    }
}

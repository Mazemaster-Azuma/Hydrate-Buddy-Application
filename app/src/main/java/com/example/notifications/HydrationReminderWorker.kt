package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.HydrateBuddyApplication
import com.example.utils.DateUtils
import kotlinx.coroutines.flow.first

class HydrationReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? HydrateBuddyApplication
                ?: return Result.success()
            val repository = app.repository
            val prefs = repository.getCurrentPreferences()

            if (!prefs.onboardingCompleted || !prefs.remindersEnabled) {
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val withinWaking = DateUtils.isWithinWakingHours(
                nowMillis = now,
                wakeHour = prefs.wakeHour,
                wakeMinute = prefs.wakeMinute,
                bedHour = prefs.bedHour,
                bedMinute = prefs.bedMinute
            )

            if (!withinWaking) {
                return Result.success()
            }

            val todayIso = DateUtils.todayIso(now)
            val todayTotal = repository.observeTotalMlForDate(todayIso).first()
            if (todayTotal >= prefs.dailyGoalMl) {
                return Result.success()
            }

            if (prefs.ringingReminderEnabled) {
                AlarmRingController.startRinging(
                    context = applicationContext,
                    userName = prefs.userName,
                    totalMl = todayTotal,
                    goalMl = prefs.dailyGoalMl,
                    unit = prefs.unit,
                    vibrationEnabled = prefs.vibrationEnabled
                )
            } else {
                NotificationHelper.showHydrationReminder(
                    context = applicationContext,
                    userName = prefs.userName,
                    totalMl = todayTotal,
                    goalMl = prefs.dailyGoalMl,
                    unit = prefs.unit
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

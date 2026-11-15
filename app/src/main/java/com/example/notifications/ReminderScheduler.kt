package com.example.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.model.UserPreferences
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val UNIQUE_WORK_NAME = "hydrate_buddy_periodic_reminder"
    private const val SNOOZE_WORK_NAME = "hydrate_buddy_snooze_reminder"

    fun syncReminders(context: Context, prefs: UserPreferences) {
        if (prefs.remindersEnabled && prefs.onboardingCompleted) {
            schedulePeriodicReminder(context, prefs.reminderIntervalMinutes)
        } else {
            cancelReminders(context)
        }
    }

    fun schedulePeriodicReminder(context: Context, intervalMinutes: Int) {
        val safeInterval = intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<HydrationReminderWorker>(
            safeInterval,
            TimeUnit.MINUTES
        )
            .setInitialDelay(safeInterval, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleSnoozeReminder(context: Context, snoozeMinutes: Int = 10) {
        val request = OneTimeWorkRequestBuilder<HydrationReminderWorker>()
            .setInitialDelay(snoozeMinutes.coerceAtLeast(1).toLong(), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SNOOZE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminders(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(SNOOZE_WORK_NAME)
    }
}

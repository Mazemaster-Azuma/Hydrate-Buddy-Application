package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.HydrateBuddyApplication

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
            ReminderScheduler.syncReminders(applicationContext, prefs, forceReschedule = false)
            HydrationNotificationReceiver.deliverReminderIfEligible(
                context = applicationContext,
                repository = repository,
                nowMillis = System.currentTimeMillis()
            )
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

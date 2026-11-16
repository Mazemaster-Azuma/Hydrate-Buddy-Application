package com.example.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.HydrateBuddyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isSupportedAction = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED

        if (!isSupportedAction) return

        val forceReschedule = action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? HydrateBuddyApplication ?: return@launch
                val prefs = app.repository.getCurrentPreferences()
                ReminderScheduler.syncReminders(
                    context = context.applicationContext,
                    prefs = prefs,
                    forceReschedule = forceReschedule
                )
                if (prefs.onboardingCompleted && prefs.remindersEnabled) {
                    ReminderScheduler.rearmPendingSnoozeIfNeeded(
                        context = context.applicationContext,
                        ringingMode = prefs.ringingReminderEnabled
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

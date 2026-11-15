package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.HydrateBuddyApplication
import com.example.model.DrinkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HydrationNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.ACTION_DISMISS -> {
                NotificationHelper.cancelReminderNotification(context)
            }
            NotificationHelper.ACTION_STOP_ALARM -> {
                AlarmRingController.stopRinging(context)
            }
            NotificationHelper.ACTION_SNOOZE_ALARM -> {
                AlarmRingController.stopRinging(context)
                ReminderScheduler.scheduleSnoozeReminder(context, snoozeMinutes = 10)
            }
            NotificationHelper.ACTION_QUICK_LOG,
            NotificationHelper.ACTION_ALARM_QUICK_LOG -> {
                val amountMl = intent.getIntExtra(NotificationHelper.EXTRA_AMOUNT_ML, 250)
                val isAlarm = intent.action == NotificationHelper.ACTION_ALARM_QUICK_LOG
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as? HydrateBuddyApplication
                        app?.repository?.addWaterEntry(
                            amountMl = amountMl,
                            drinkType = DrinkType.WATER
                        )
                        if (isAlarm) {
                            AlarmRingController.stopRinging(context)
                        } else {
                            NotificationHelper.cancelReminderNotification(context)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}

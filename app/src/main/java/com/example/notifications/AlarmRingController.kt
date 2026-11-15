package com.example.notifications

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.model.MeasurementUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AlarmRingState(
    val userName: String,
    val totalMl: Int,
    val goalMl: Int,
    val unit: MeasurementUnit,
    val quickAmountMl: Int
)

object AlarmRingController {
    private val _ringState = MutableStateFlow<AlarmRingState?>(null)
    val ringState: StateFlow<AlarmRingState?> = _ringState.asStateFlow()

    fun startRinging(
        context: Context,
        userName: String,
        totalMl: Int,
        goalMl: Int,
        unit: MeasurementUnit,
        vibrationEnabled: Boolean
    ) {
        if (!NotificationHelper.hasNotificationPermission(context)) return

        val quickAmountMl = if (unit == MeasurementUnit.OZ) 237 else 250
        _ringState.value = AlarmRingState(
            userName = userName,
            totalMl = totalMl,
            goalMl = goalMl,
            unit = unit,
            quickAmountMl = quickAmountMl
        )

        val serviceIntent = Intent(context, HydrationAlarmService::class.java).apply {
            putExtra(HydrationAlarmService.EXTRA_USER_NAME, userName)
            putExtra(HydrationAlarmService.EXTRA_TOTAL_ML, totalMl)
            putExtra(HydrationAlarmService.EXTRA_GOAL_ML, goalMl)
            putExtra(HydrationAlarmService.EXTRA_UNIT_NAME, unit.name)
            putExtra(HydrationAlarmService.EXTRA_VIBRATION_ENABLED, vibrationEnabled)
        }

        val startedService = runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
            true
        }.getOrDefault(false)

        if (!startedService) {
            // Fallback for Android 12+ background FGS restrictions:
            // Post an insistent high-priority alarm notification that loops sound/vibration.
            NotificationHelper.showInsistentAlarmNotification(
                context = context,
                userName = userName,
                totalMl = totalMl,
                goalMl = goalMl,
                unit = unit
            )
        }
    }

    fun stopRinging(context: Context) {
        _ringState.value = null
        runCatching {
            context.stopService(Intent(context, HydrationAlarmService::class.java))
        }
        NotificationHelper.cancelAlarmNotification(context)
    }

    internal fun clearStateOnly() {
        _ringState.value = null
    }
}

package com.example.notifications

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.example.model.MeasurementUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
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

    @Volatile
    private var screenWakeLock: PowerManager.WakeLock? = null

    fun startRinging(
        context: Context,
        userName: String,
        totalMl: Int,
        goalMl: Int,
        unit: MeasurementUnit,
        vibrationEnabled: Boolean
    ) {
        if (!NotificationHelper.hasNotificationPermission(context)) return

        wakeScreenBriefly(context)

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
            // Post an insistent high-priority alarm notification that wakes screen and loops sound/vibration.
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
        releaseScreenWakeLock()
        runCatching {
            context.stopService(Intent(context, HydrationAlarmService::class.java))
        }
        NotificationHelper.cancelAlarmNotification(context)
    }

    internal fun clearStateOnly() {
        _ringState.value = null
        releaseScreenWakeLock()
    }

    @Suppress("DEPRECATION")
    internal fun wakeScreenBriefly(context: Context, durationMillis: Long = 6_000L) {
        runCatching {
            val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return
            if (!powerManager.isInteractive) {
                releaseScreenWakeLock()
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "HydrateBuddy:AlarmScreenWake"
                ).apply {
                    setReferenceCounted(false)
                    acquire(durationMillis)
                }
                screenWakeLock = wakeLock
            }
        }
    }

    private fun releaseScreenWakeLock() {
        runCatching {
            screenWakeLock?.let { wl ->
                if (wl.isHeld) wl.release()
            }
        }
        screenWakeLock = null
    }
}

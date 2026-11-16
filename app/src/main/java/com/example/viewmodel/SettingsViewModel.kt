package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.MeasurementUnit
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import com.example.notifications.AlarmRingController
import com.example.notifications.NotificationHelper
import com.example.notifications.ReminderScheduleCalculator
import com.example.notifications.ReminderScheduler
import com.example.repository.HydrationRepository
import com.example.updater.AppUpdateCoordinator
import com.example.updater.UpdateCheckUiState
import com.example.utils.DateUtils
import com.example.utils.SoundFeedbackPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val repository: HydrationRepository,
    private val appContext: Context,
    private val updateCoordinator: AppUpdateCoordinator = AppUpdateCoordinator(appContext, repository)
) : ViewModel() {

    val preferencesState: StateFlow<UserPreferences> = repository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    val updateState: StateFlow<UpdateCheckUiState> = updateCoordinator.uiState
    val nextScheduledReminderMillis: StateFlow<Long?> = ReminderScheduler.nextScheduledTriggerMillis
    val currentVersionName: String get() = updateCoordinator.currentVersionName
    val currentVersionCode: Int get() = updateCoordinator.currentVersionCode

    private val _statusMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val statusMessages: SharedFlow<String> = _statusMessages.asSharedFlow()

    init {
        ReminderScheduler.getNextScheduledReminderMillis(appContext)
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            repository.updatePreferences { it.copy(userName = name.trim().take(32)) }
            _statusMessages.emit("Name updated")
        }
    }

    fun updateDailyGoal(goalMl: Int) {
        viewModelScope.launch {
            val safeGoal = goalMl.coerceIn(400, 8000)
            repository.updatePreferences { it.copy(dailyGoalMl = safeGoal) }
            _statusMessages.emit("Daily target updated")
        }
    }

    fun updateUnit(unit: MeasurementUnit) {
        viewModelScope.launch {
            repository.updatePreferences { it.copy(unit = unit) }
        }
    }

    fun updateWakeTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val updated = repository.updatePreferences {
                it.copy(wakeHour = hour, wakeMinute = minute)
            }
            withContext(Dispatchers.IO) {
                ReminderScheduler.syncReminders(appContext, updated, forceReschedule = true)
            }
        }
    }

    fun updateBedTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val updated = repository.updatePreferences {
                it.copy(bedHour = hour, bedMinute = minute)
            }
            withContext(Dispatchers.IO) {
                ReminderScheduler.syncReminders(appContext, updated, forceReschedule = true)
            }
        }
    }

    fun updateReminderInterval(minutes: Int) {
        viewModelScope.launch {
            val safeMinutes = minutes.coerceAtLeast(ReminderScheduleCalculator.MIN_INTERVAL_MINUTES)
            val updated = repository.updatePreferences {
                it.copy(reminderIntervalMinutes = safeMinutes)
            }
            val nextMs = withContext(Dispatchers.IO) {
                ReminderScheduler.syncReminders(appContext, updated, forceReschedule = true)
                ReminderScheduler.getNextScheduledReminderMillis(appContext)
            }
            val label = if (safeMinutes >= 60 && safeMinutes % 60 == 0) "${safeMinutes / 60}h" else "${safeMinutes}m"
            val nextTimeSuffix = nextMs?.let { " • Next at ${DateUtils.formatTime(it)}" }.orEmpty()
            _statusMessages.emit("Reminders set to every $label$nextTimeSuffix")
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val updated = repository.updatePreferences {
                it.copy(remindersEnabled = enabled)
            }
            if (!enabled) {
                AlarmRingController.stopRinging(appContext)
            }
            withContext(Dispatchers.IO) {
                ReminderScheduler.syncReminders(appContext, updated, forceReschedule = enabled)
            }
        }
    }

    fun setRingingReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val updated = repository.updatePreferences {
                it.copy(ringingReminderEnabled = enabled)
            }
            if (!enabled) {
                AlarmRingController.stopRinging(appContext)
            }
            withContext(Dispatchers.IO) {
                ReminderScheduler.syncReminders(appContext, updated, forceReschedule = false)
            }
        }
    }

    fun resyncRemindersOnResume() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = repository.getCurrentPreferences()
            ReminderScheduler.syncReminders(appContext, prefs, forceReschedule = false)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePreferences { it.copy(soundEnabled = enabled) }
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePreferences { it.copy(vibrationEnabled = enabled) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.updatePreferences { it.copy(themeMode = mode) }
        }
    }

    fun sendTestReminder() {
        viewModelScope.launch {
            if (!NotificationHelper.hasNotificationPermission(appContext)) {
                _statusMessages.emit("Please allow notification permission first")
                return@launch
            }
            val prefs = repository.getCurrentPreferences()
            val todayTotal = repository.observeTotalMlForDate(DateUtils.todayIso()).first()
            if (prefs.ringingReminderEnabled) {
                AlarmRingController.startRinging(
                    context = appContext,
                    userName = prefs.userName,
                    totalMl = todayTotal,
                    goalMl = prefs.dailyGoalMl,
                    unit = prefs.unit,
                    vibrationEnabled = prefs.vibrationEnabled
                )
            } else {
                NotificationHelper.showHydrationReminder(
                    context = appContext,
                    userName = prefs.userName,
                    totalMl = todayTotal,
                    goalMl = prefs.dailyGoalMl,
                    unit = prefs.unit
                )
                _statusMessages.emit("Test reminder sent!")
            }
        }
    }

    fun resetTodayProgress() {
        viewModelScope.launch {
            repository.resetTodayProgress()
            _statusMessages.emit("Today's progress has been reset")
        }
    }

    fun clearAllData(onCleared: () -> Unit) {
        viewModelScope.launch {
            AlarmRingController.stopRinging(appContext)
            SoundFeedbackPlayer.stopAlarmChimePreview()
            NotificationHelper.cancelReminderNotification(appContext)
            withContext(Dispatchers.IO) {
                ReminderScheduler.cancelReminders(appContext)
            }
            repository.clearAllData()
            onCleared()
        }
    }

    fun checkForUpdatesManually() {
        updateCoordinator.checkForUpdatesManually()
    }

    fun openOrStartAvailableUpdate() {
        when (updateCoordinator.uiState.value) {
            is UpdateCheckUiState.UpdateAvailable -> updateCoordinator.showAvailableUpdateDialog()
            is UpdateCheckUiState.ReadyToInstall -> updateCoordinator.startDownloadAndInstall()
            else -> updateCoordinator.checkForUpdatesManually()
        }
    }
}

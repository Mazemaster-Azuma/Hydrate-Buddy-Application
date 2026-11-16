package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.MeasurementUnit
import com.example.notifications.ReminderScheduler
import com.example.repository.HydrationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val userName: String = "",
    val unit: MeasurementUnit = MeasurementUnit.ML,
    val dailyGoalMl: Int = 2000,
    val wakeHour: Int = 7,
    val wakeMinute: Int = 0,
    val bedHour: Int = 22,
    val bedMinute: Int = 30,
    val reminderIntervalMinutes: Int = 90,
    val remindersEnabled: Boolean = true,
    val ringingReminderEnabled: Boolean = false,
    val isSaving: Boolean = false
)

class OnboardingViewModel(
    private val repository: HydrationRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = repository.getCurrentPreferences()
            if (prefs.onboardingCompleted) {
                _uiState.update {
                    it.copy(
                        userName = prefs.userName,
                        unit = prefs.unit,
                        dailyGoalMl = prefs.dailyGoalMl,
                        wakeHour = prefs.wakeHour,
                        wakeMinute = prefs.wakeMinute,
                        bedHour = prefs.bedHour,
                        bedMinute = prefs.bedMinute,
                        reminderIntervalMinutes = prefs.reminderIntervalMinutes,
                        remindersEnabled = prefs.remindersEnabled,
                        ringingReminderEnabled = prefs.ringingReminderEnabled
                    )
                }
            }
        }
    }

    fun setUserName(name: String) {
        _uiState.update { it.copy(userName = name.take(32)) }
    }

    fun setUnit(unit: MeasurementUnit) {
        _uiState.update { state ->
            // Snap default goal cleanly to friendly milestone in newly selected unit if unchanged
            val adjustedGoal = when {
                unit == MeasurementUnit.OZ && state.dailyGoalMl == 2000 -> 1893 // 64 oz
                unit == MeasurementUnit.ML && state.dailyGoalMl == 1893 -> 2000
                else -> state.dailyGoalMl
            }
            state.copy(unit = unit, dailyGoalMl = adjustedGoal)
        }
    }

    fun setDailyGoalMl(goalMl: Int) {
        _uiState.update { it.copy(dailyGoalMl = goalMl.coerceIn(400, 8000)) }
    }

    fun setWakeTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(wakeHour = hour, wakeMinute = minute) }
    }

    fun setBedTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(bedHour = hour, bedMinute = minute) }
    }

    fun setReminderInterval(minutes: Int) {
        _uiState.update { it.copy(reminderIntervalMinutes = minutes) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        _uiState.update { it.copy(remindersEnabled = enabled) }
    }

    fun setRingingReminderEnabled(enabled: Boolean) {
        _uiState.update { it.copy(ringingReminderEnabled = enabled) }
    }

    fun completeOnboarding(onFinished: () -> Unit) {
        val current = _uiState.value
        if (current.isSaving) return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            repository.ensureInitialized()
            val updated = repository.updatePreferences {
                it.copy(
                    userName = current.userName.trim(),
                    unit = current.unit,
                    dailyGoalMl = current.dailyGoalMl.coerceIn(400, 8000),
                    wakeHour = current.wakeHour,
                    wakeMinute = current.wakeMinute,
                    bedHour = current.bedHour,
                    bedMinute = current.bedMinute,
                    reminderIntervalMinutes = current.reminderIntervalMinutes,
                    remindersEnabled = current.remindersEnabled,
                    ringingReminderEnabled = current.ringingReminderEnabled,
                    onboardingCompleted = true
                )
            }
            ReminderScheduler.syncReminders(appContext, updated, forceReschedule = true)
            _uiState.update { it.copy(isSaving = false) }
            onFinished()
        }
    }
}

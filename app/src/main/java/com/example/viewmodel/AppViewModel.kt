package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.DrinkType
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import com.example.notifications.AlarmRingController
import com.example.notifications.AlarmRingState
import com.example.notifications.ReminderScheduler
import com.example.repository.HydrationRepository
import com.example.utils.SoundFeedbackPlayer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppBootstrapState {
    data object Loading : AppBootstrapState
    data class Ready(
        val onboardingCompleted: Boolean,
        val themeMode: ThemeMode,
        val preferences: UserPreferences
    ) : AppBootstrapState
}

class AppViewModel(
    private val repository: HydrationRepository,
    private val appContext: Context
) : ViewModel() {

    val bootstrapState: StateFlow<AppBootstrapState> = repository.preferencesFlow
        .map { prefs ->
            AppBootstrapState.Ready(
                onboardingCompleted = prefs.onboardingCompleted,
                themeMode = prefs.themeMode,
                preferences = prefs
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppBootstrapState.Loading
        )

    val alarmRingState: StateFlow<AlarmRingState?> = AlarmRingController.ringState

    fun logAndStopAlarm(amountMl: Int) {
        AlarmRingController.stopRinging(appContext)
        viewModelScope.launch {
            val prefs = repository.getCurrentPreferences()
            val result = repository.addWaterEntry(amountMl = amountMl, drinkType = DrinkType.WATER)
            if (prefs.soundEnabled) {
                SoundFeedbackPlayer.playWaterDropChime(isGoalReached = result.goalReachedNow)
            }
        }
    }

    fun snoozeAlarm() {
        AlarmRingController.stopRinging(appContext)
        ReminderScheduler.scheduleSnoozeReminder(appContext, snoozeMinutes = 10)
    }

    fun stopAlarm() {
        AlarmRingController.stopRinging(appContext)
    }
}

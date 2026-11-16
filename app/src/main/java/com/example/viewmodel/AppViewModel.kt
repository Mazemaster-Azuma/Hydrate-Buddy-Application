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
import com.example.updater.AppUpdateCoordinator
import com.example.updater.UpdateCheckUiState
import com.example.utils.SoundFeedbackPlayer
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
sealed interface AppBootstrapState {
    data object Loading : AppBootstrapState
    @Immutable
    data class Ready(
        val onboardingCompleted: Boolean,
        val themeMode: ThemeMode,
        val preferences: UserPreferences
    ) : AppBootstrapState
}

class AppViewModel(
    private val repository: HydrationRepository,
    private val appContext: Context,
    private val updateCoordinator: AppUpdateCoordinator = AppUpdateCoordinator(appContext, repository)
) : ViewModel() {

    val bootstrapState: StateFlow<AppBootstrapState> = repository.preferencesFlow
        .map { prefs ->
            AppBootstrapState.Ready(
                onboardingCompleted = prefs.onboardingCompleted,
                themeMode = prefs.themeMode,
                preferences = prefs
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppBootstrapState.Loading
        )

    val alarmRingState: StateFlow<AlarmRingState?> = AlarmRingController.ringState
    val updateState: StateFlow<UpdateCheckUiState> = updateCoordinator.uiState
    val currentVersionName: String get() = updateCoordinator.currentVersionName
    val currentVersionCode: Int get() = updateCoordinator.currentVersionCode

    fun onForegroundNetworkConnected() {
        updateCoordinator.onForegroundNetworkConnected()
    }

    fun onForegroundResumed() {
        updateCoordinator.onForegroundResumed()
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = repository.getCurrentPreferences()
            ReminderScheduler.syncReminders(
                context = appContext,
                prefs = prefs,
                forceReschedule = false
            )
        }
    }

    fun startAppUpdate() {
        updateCoordinator.startDownloadAndInstall()
    }

    fun cancelAppUpdate() {
        updateCoordinator.cancelDownload()
    }

    fun dismissAppUpdateDialog() {
        updateCoordinator.dismissUpdateDialog()
    }

    fun openInstallPermissionSettings() {
        updateCoordinator.requestInstallPermissionFromSettings()
    }

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
        ReminderScheduler.scheduleSnoozeReminder(
            context = appContext,
            snoozeMinutes = 10,
            ringingMode = true
        )
    }

    fun stopAlarm() {
        AlarmRingController.stopRinging(appContext)
    }
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.navigation.HydrateNavHost
import com.example.notifications.ReminderScheduler
import com.example.ui.components.AlarmRingingDialog
import com.example.ui.theme.HydrateBuddyTheme
import com.example.viewmodel.AppBootstrapState
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.HydrateViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as HydrateBuddyApplication
        val factory = HydrateViewModelFactory(
            repository = app.repository,
            appContext = applicationContext
        )

        setContent {
            val appViewModel: AppViewModel = viewModel(factory = factory)
            val bootstrapState by appViewModel.bootstrapState.collectAsStateWithLifecycle()
            val alarmRingState by appViewModel.alarmRingState.collectAsStateWithLifecycle()

            when (val state = bootstrapState) {
                is AppBootstrapState.Loading -> {
                    HydrateBuddyTheme {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is AppBootstrapState.Ready -> {
                    androidx.compose.runtime.LaunchedEffect(
                        state.preferences.onboardingCompleted,
                        state.preferences.remindersEnabled,
                        state.preferences.reminderIntervalMinutes,
                        state.preferences.wakeHour,
                        state.preferences.wakeMinute,
                        state.preferences.bedHour,
                        state.preferences.bedMinute
                    ) {
                        ReminderScheduler.syncReminders(applicationContext, state.preferences)
                    }
                    HydrateBuddyTheme(themeMode = state.themeMode) {
                        HydrateNavHost(
                            onboardingCompleted = state.onboardingCompleted,
                            vibrationEnabled = state.preferences.vibrationEnabled,
                            viewModelFactory = factory
                        )
                        alarmRingState?.let { ringState ->
                            AlarmRingingDialog(
                                ringState = ringState,
                                onLogAndStop = appViewModel::logAndStopAlarm,
                                onSnooze = appViewModel::snoozeAlarm,
                                onStop = appViewModel::stopAlarm
                            )
                        }
                    }
                }
            }
        }
    }
}

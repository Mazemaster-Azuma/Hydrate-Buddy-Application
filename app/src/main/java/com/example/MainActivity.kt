package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.navigation.HydrateNavHost
import com.example.notifications.ReminderScheduler
import com.example.ui.components.AlarmRingingDialog
import com.example.ui.components.AppUpdateDialog
import com.example.ui.theme.HydrateBuddyTheme
import com.example.updater.NetworkMonitor
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
            appContext = applicationContext,
            updateCoordinator = app.updateCoordinator
        )

        setContent {
            val appViewModel: AppViewModel = viewModel(factory = factory)
            val bootstrapState by appViewModel.bootstrapState.collectAsStateWithLifecycle()
            val alarmRingState by appViewModel.alarmRingState.collectAsStateWithLifecycle()
            val updateState by appViewModel.updateState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner, appViewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        appViewModel.onForegroundResumed()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Strictly foreground-only internet check: starts in ON_START, cancelled in ON_STOP.
            LaunchedEffect(lifecycleOwner, appViewModel) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    NetworkMonitor.observeInternetConnected(applicationContext)
                        .collect { connected ->
                            if (connected) {
                                appViewModel.onForegroundNetworkConnected()
                            }
                        }
                }
            }

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
                    LaunchedEffect(
                        state.preferences.onboardingCompleted,
                        state.preferences.remindersEnabled,
                        state.preferences.reminderIntervalMinutes,
                        state.preferences.wakeHour,
                        state.preferences.wakeMinute,
                        state.preferences.bedHour,
                        state.preferences.bedMinute
                    ) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            ReminderScheduler.syncReminders(applicationContext, state.preferences)
                        }
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
                        AppUpdateDialog(
                            updateState = updateState,
                            currentVersionName = appViewModel.currentVersionName,
                            currentVersionCode = appViewModel.currentVersionCode,
                            onStartUpdate = appViewModel::startAppUpdate,
                            onCancelDownload = appViewModel::cancelAppUpdate,
                            onDismiss = appViewModel::dismissAppUpdateDialog,
                            onOpenInstallPermissionSettings = appViewModel::openInstallPermissionSettings
                        )
                    }
                }
            }
        }
    }
}

package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.example.model.ThemeMode
import com.example.navigation.HydrateNavHost
import com.example.notifications.AlarmRingController
import com.example.notifications.NotificationHelper
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
        handleAlarmWakeIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

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

            DisposableEffect(alarmRingState != null) {
                applyScreenWakeState(isAlarmRinging = alarmRingState != null)
                onDispose {}
            }

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

            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (val state = bootstrapState) {
                is AppBootstrapState.Loading -> false
                is AppBootstrapState.Ready -> when (state.themeMode) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            }

            DisposableEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
                onDispose {}
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmWakeIntent(intent)
    }

    private fun handleAlarmWakeIntent(intent: Intent?) {
        val fromAlarm = intent?.getBooleanExtra(NotificationHelper.EXTRA_FROM_ALARM_WAKEUP, false) == true
        val isRinging = AlarmRingController.ringState.value != null
        if (fromAlarm || isRinging) {
            NotificationHelper.wakeUpScreen(this, durationMillis = 20_000L)
            applyScreenWakeState(isAlarmRinging = true)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyScreenWakeState(isAlarmRinging: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(isAlarmRinging)
            setTurnScreenOn(isAlarmRinging)
        } else {
            val legacyFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (isAlarmRinging) {
                window.addFlags(legacyFlags)
            } else {
                window.clearFlags(legacyFlags)
            }
        }
        if (isAlarmRinging) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

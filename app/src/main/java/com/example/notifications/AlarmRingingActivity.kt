package com.example.notifications

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.HydrateBuddyApplication
import com.example.model.DrinkType
import com.example.ui.components.AlarmRingingCard
import com.example.ui.theme.HydrateBuddyTheme
import com.example.utils.SoundFeedbackPlayer
import kotlinx.coroutines.launch

class AlarmRingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWakeFlags()
        enableEdgeToEdge()

        val app = application as HydrateBuddyApplication
        setContent {
            val ringState by AlarmRingController.ringState.collectAsStateWithLifecycle()
            val prefs by app.repository.preferencesFlow.collectAsStateWithLifecycle(initialValue = null)

            LaunchedEffect(ringState) {
                if (ringState == null) {
                    finish()
                }
            }

            HydrateBuddyTheme(themeMode = prefs?.themeMode ?: com.example.model.ThemeMode.LIGHT) {
                ringState?.let { state ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                            .safeDrawingPadding()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AlarmRingingCard(
                            ringState = state,
                            onLogAndStop = { amountMl ->
                                AlarmRingController.stopRinging(applicationContext)
                                ReminderScheduler.cancelSnoozeReminder(applicationContext)
                                lifecycleScope.launch {
                                    val currentPrefs = app.repository.getCurrentPreferences()
                                    val result = app.repository.addWaterEntry(
                                        amountMl = amountMl,
                                        drinkType = DrinkType.WATER
                                    )
                                    if (currentPrefs.soundEnabled) {
                                        SoundFeedbackPlayer.playWaterDropChime(isGoalReached = result.goalReachedNow)
                                    }
                                    finish()
                                }
                            },
                            onSnooze = {
                                AlarmRingController.stopRinging(applicationContext)
                                ReminderScheduler.scheduleSnoozeReminder(
                                    context = applicationContext,
                                    snoozeMinutes = 10,
                                    ringingMode = true
                                )
                                finish()
                            },
                            onStop = {
                                AlarmRingController.stopRinging(applicationContext)
                                ReminderScheduler.cancelSnoozeReminder(applicationContext)
                                finish()
                            },
                            modifier = Modifier.widthIn(max = 460.dp)
                        )
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureLockScreenWakeFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

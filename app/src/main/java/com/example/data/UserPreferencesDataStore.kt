package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.MeasurementUnit
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hydrateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hydrate_buddy_prefs"
)

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val DAILY_GOAL_ML = intPreferencesKey("daily_goal_ml")
        val UNIT = stringPreferencesKey("unit")
        val WAKE_HOUR = intPreferencesKey("wake_hour")
        val WAKE_MINUTE = intPreferencesKey("wake_minute")
        val BED_HOUR = intPreferencesKey("bed_hour")
        val BED_MINUTE = intPreferencesKey("bed_minute")
        val REMINDER_INTERVAL_MINUTES = intPreferencesKey("reminder_interval_minutes")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val RINGING_REMINDER_ENABLED = booleanPreferencesKey("ringing_reminder_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val UPDATE_REPO_OVERRIDE = stringPreferencesKey("update_repo_override")
        val UPDATE_ACCESS_TOKEN = stringPreferencesKey("update_access_token")
    }

    val preferencesFlow: Flow<UserPreferences> = context.hydrateDataStore.data.map { prefs ->
        UserPreferences(
            userName = prefs[Keys.USER_NAME] ?: "",
            dailyGoalMl = prefs[Keys.DAILY_GOAL_ML] ?: 2000,
            unit = MeasurementUnit.fromName(prefs[Keys.UNIT] ?: MeasurementUnit.ML.name),
            wakeHour = prefs[Keys.WAKE_HOUR] ?: 7,
            wakeMinute = prefs[Keys.WAKE_MINUTE] ?: 0,
            bedHour = prefs[Keys.BED_HOUR] ?: 22,
            bedMinute = prefs[Keys.BED_MINUTE] ?: 30,
            reminderIntervalMinutes = prefs[Keys.REMINDER_INTERVAL_MINUTES] ?: 90,
            remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: true,
            ringingReminderEnabled = prefs[Keys.RINGING_REMINDER_ENABLED] ?: false,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE] ?: ThemeMode.LIGHT.name),
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            updateRepoOverride = prefs[Keys.UPDATE_REPO_OVERRIDE] ?: "",
            updateAccessToken = prefs[Keys.UPDATE_ACCESS_TOKEN] ?: ""
        )
    }

    suspend fun getCurrentPreferences(): UserPreferences = preferencesFlow.first()

    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences): UserPreferences {
        var updated = UserPreferences()
        context.hydrateDataStore.edit { prefs ->
            val current = UserPreferences(
                userName = prefs[Keys.USER_NAME] ?: "",
                dailyGoalMl = prefs[Keys.DAILY_GOAL_ML] ?: 2000,
                unit = MeasurementUnit.fromName(prefs[Keys.UNIT] ?: MeasurementUnit.ML.name),
                wakeHour = prefs[Keys.WAKE_HOUR] ?: 7,
                wakeMinute = prefs[Keys.WAKE_MINUTE] ?: 0,
                bedHour = prefs[Keys.BED_HOUR] ?: 22,
                bedMinute = prefs[Keys.BED_MINUTE] ?: 30,
                reminderIntervalMinutes = prefs[Keys.REMINDER_INTERVAL_MINUTES] ?: 90,
                remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: true,
                ringingReminderEnabled = prefs[Keys.RINGING_REMINDER_ENABLED] ?: false,
                soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
                vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
                themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE] ?: ThemeMode.LIGHT.name),
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                updateRepoOverride = prefs[Keys.UPDATE_REPO_OVERRIDE] ?: "",
                updateAccessToken = prefs[Keys.UPDATE_ACCESS_TOKEN] ?: ""
            )
            updated = transform(current)
            prefs[Keys.USER_NAME] = updated.userName
            prefs[Keys.DAILY_GOAL_ML] = updated.dailyGoalMl
            prefs[Keys.UNIT] = updated.unit.name
            prefs[Keys.WAKE_HOUR] = updated.wakeHour
            prefs[Keys.WAKE_MINUTE] = updated.wakeMinute
            prefs[Keys.BED_HOUR] = updated.bedHour
            prefs[Keys.BED_MINUTE] = updated.bedMinute
            prefs[Keys.REMINDER_INTERVAL_MINUTES] = updated.reminderIntervalMinutes
            prefs[Keys.REMINDERS_ENABLED] = updated.remindersEnabled
            prefs[Keys.RINGING_REMINDER_ENABLED] = updated.ringingReminderEnabled
            prefs[Keys.SOUND_ENABLED] = updated.soundEnabled
            prefs[Keys.VIBRATION_ENABLED] = updated.vibrationEnabled
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.ONBOARDING_COMPLETED] = updated.onboardingCompleted
            prefs[Keys.UPDATE_REPO_OVERRIDE] = updated.updateRepoOverride
            prefs[Keys.UPDATE_ACCESS_TOKEN] = updated.updateAccessToken
        }
        return updated
    }

    suspend fun clearAll() {
        context.hydrateDataStore.edit { it.clear() }
    }
}

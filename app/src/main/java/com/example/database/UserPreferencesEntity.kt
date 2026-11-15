package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val userName: String = "",
    val dailyGoalMl: Int = 2000,
    val unit: String = "ML",
    val wakeHour: Int = 7,
    val wakeMinute: Int = 0,
    val bedHour: Int = 22,
    val bedMinute: Int = 30,
    val reminderIntervalMinutes: Int = 90,
    val remindersEnabled: Boolean = true,
    val ringingReminderEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val themeMode: String = "SYSTEM",
    val onboardingCompleted: Boolean = false
)

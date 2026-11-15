package com.example.model

import androidx.compose.runtime.Immutable
import com.example.database.AchievementEntity
import com.example.database.DailyProgressEntity
import com.example.database.WaterEntryEntity

@Immutable
data class UserPreferences(
    val userName: String = "",
    val dailyGoalMl: Int = 2000,
    val unit: MeasurementUnit = MeasurementUnit.ML,
    val wakeHour: Int = 7,
    val wakeMinute: Int = 0,
    val bedHour: Int = 22,
    val bedMinute: Int = 30,
    val reminderIntervalMinutes: Int = 90,
    val remindersEnabled: Boolean = true,
    val ringingReminderEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val onboardingCompleted: Boolean = false,
    val updateRepoOverride: String = "",
    val updateAccessToken: String = ""
)

@Immutable
data class LevelInfo(
    val level: Int,
    val title: String,
    val totalXp: Int,
    val currentLevelBaseXp: Int,
    val nextLevelXp: Int
) {
    val xpInCurrentLevel: Int get() = (totalXp - currentLevelBaseXp).coerceAtLeast(0)
    val xpRequiredForNext: Int get() = (nextLevelXp - currentLevelBaseXp).coerceAtLeast(1)
    val progressFraction: Float get() = (xpInCurrentLevel.toFloat() / xpRequiredForNext).coerceIn(0f, 1f)
}

@Immutable
data class AddWaterResult(
    val entryId: Long,
    val newTotalMl: Int,
    val goalReachedNow: Boolean,
    val milestoneReached: Int?,
    val newlyUnlockedAchievements: List<AchievementEntity>
)

@Immutable
sealed interface HomeUiState {
    data object Loading : HomeUiState
    @Immutable
    data class Success(
        val greeting: String,
        val displayDate: String,
        val todayIso: String,
        val totalMl: Int,
        val goalMl: Int,
        val remainingMl: Int,
        val progressFraction: Float,
        val unit: MeasurementUnit,
        val buddyMood: BuddyMood,
        val buddyMessage: String,
        val entries: List<WaterEntryEntity>,
        val currentStreak: Int,
        val levelInfo: LevelInfo,
        val quickAddAmountsMl: List<Int>
    ) : HomeUiState
    @Immutable
    data class Error(val message: String) : HomeUiState
}

enum class HistoryPeriod(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month")
}

@Immutable
data class WeeklyBarPoint(
    val dateIso: String,
    val dayLabel: String,
    val totalMl: Int,
    val goalMl: Int,
    val completionPercent: Int
)

@Immutable
data class MonthlyHeatmapDay(
    val dateIso: String,
    val dayOfMonth: Int,
    val totalMl: Int,
    val goalMl: Int,
    val completionFraction: Float,
    val isFuture: Boolean
)

@Immutable
sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    @Immutable
    data class Success(
        val selectedPeriod: HistoryPeriod,
        val selectedDateIso: String,
        val todayIso: String,
        val selectedDateLabel: String,
        val isToday: Boolean,
        val unit: MeasurementUnit,
        val dailyGoalMl: Int,
        val selectedDayTotalMl: Int,
        val selectedDayGoalMl: Int,
        val selectedDayPercent: Int,
        val selectedDayEntries: List<WaterEntryEntity>,
        val weeklyPoints: List<WeeklyBarPoint>,
        val weeklyTotalMl: Int,
        val weeklyAverageMl: Int,
        val monthlyDays: List<MonthlyHeatmapDay>,
        val monthTitle: String,
        val monthlyTotalMl: Int,
        val monthlyAverageMl: Int,
        val monthlyGoalsMetCount: Int,
        val allProgressRecords: List<DailyProgressEntity>
    ) : HistoryUiState
}

enum class AchievementFilter(val label: String) {
    ALL("All"),
    UNLOCKED("Unlocked"),
    LOCKED("In Progress")
}

@Immutable
sealed interface AchievementsUiState {
    data object Loading : AchievementsUiState
    @Immutable
    data class Success(
        val achievements: List<AchievementEntity>,
        val filter: AchievementFilter,
        val levelInfo: LevelInfo,
        val currentStreak: Int,
        val longestStreak: Int,
        val lifetimeTotalMl: Int,
        val unit: MeasurementUnit,
        val unlockedCount: Int,
        val totalCount: Int
    ) : AchievementsUiState
}

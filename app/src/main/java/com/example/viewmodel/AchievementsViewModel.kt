package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AchievementFilter
import com.example.model.AchievementsUiState
import com.example.repository.HydrationRepository
import com.example.utils.DateUtils
import com.example.utils.GamificationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AchievementsViewModel(
    repository: HydrationRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(AchievementFilter.ALL)

    val uiState: StateFlow<AchievementsUiState> = combine(
        repository.achievementsFlow,
        repository.allProgressFlow,
        repository.totalXpFlow,
        repository.lifetimeTotalMlFlow,
        repository.preferencesFlow,
        filterFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allAchievements = values[0] as List<com.example.database.AchievementEntity>
        @Suppress("UNCHECKED_CAST")
        val allProgress = values[1] as List<com.example.database.DailyProgressEntity>
        val totalXp = values[2] as Int
        val lifetimeTotalMl = values[3] as Int
        val prefs = values[4] as com.example.model.UserPreferences
        val filter = values[5] as AchievementFilter

        val filtered = when (filter) {
            AchievementFilter.ALL -> allAchievements
            AchievementFilter.UNLOCKED -> allAchievements.filter { it.isUnlocked }
            AchievementFilter.LOCKED -> allAchievements.filter { !it.isUnlocked }
        }
        val (currentStreak, longestStreak) = GamificationUtils.calculateStreaks(
            allProgress,
            DateUtils.todayIso()
        )

        AchievementsUiState.Success(
            achievements = filtered,
            filter = filter,
            levelInfo = GamificationUtils.calculateLevel(totalXp),
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lifetimeTotalMl = lifetimeTotalMl,
            unit = prefs.unit,
            unlockedCount = allAchievements.count { it.isUnlocked },
            totalCount = allAchievements.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AchievementsUiState.Loading
    )

    fun setFilter(filter: AchievementFilter) {
        filterFlow.value = filter
    }
}

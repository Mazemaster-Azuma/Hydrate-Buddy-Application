package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AchievementEntity
import com.example.database.WaterEntryEntity
import com.example.model.DrinkType
import com.example.model.HomeUiState
import com.example.repository.HydrationRepository
import com.example.utils.BuddyMoodCalculator
import com.example.utils.DateUtils
import com.example.utils.GamificationUtils
import com.example.utils.SoundFeedbackPlayer
import com.example.utils.UnitConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeFeedbackEvent(
    val entryId: Long,
    val amountMl: Int,
    val goalReachedNow: Boolean,
    val milestoneReached: Int?
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: HydrationRepository
) : ViewModel() {

    private val todayIsoFlow = MutableStateFlow(DateUtils.todayIso())
    private val recentMilestoneFlow = MutableStateFlow<Int?>(null)
    private val justLoggedDrinkFlow = MutableStateFlow(false)
    private val dropletBurstTriggerFlow = MutableStateFlow(0)
    private val unlockedQueueFlow = MutableStateFlow<List<AchievementEntity>>(emptyList())

    private val _feedbackEvents = MutableSharedFlow<HomeFeedbackEvent>(extraBufferCapacity = 1)
    val feedbackEvents: SharedFlow<HomeFeedbackEvent> = _feedbackEvents.asSharedFlow()

    val dropletBurstTrigger: StateFlow<Int> = dropletBurstTriggerFlow.asStateFlow()
    val unlockedBannerQueue: StateFlow<List<AchievementEntity>> = unlockedQueueFlow.asStateFlow()

    private var lastTapMillis: Long = 0L

    private val todayEntriesFlow = todayIsoFlow.flatMapLatest { dateIso ->
        repository.observeEntriesForDate(dateIso)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        todayIsoFlow,
        todayEntriesFlow,
        repository.preferencesFlow,
        repository.allProgressFlow,
        repository.totalXpFlow,
        recentMilestoneFlow,
        justLoggedDrinkFlow
    ) { values ->
        val todayIso = values[0] as String
        @Suppress("UNCHECKED_CAST")
        val entries = values[1] as List<WaterEntryEntity>
        val prefs = values[2] as com.example.model.UserPreferences
        @Suppress("UNCHECKED_CAST")
        val allProgress = values[3] as List<com.example.database.DailyProgressEntity>
        val totalXp = values[4] as Int
        val recentMilestone = values[5] as Int?
        val justLogged = values[6] as Boolean

        val totalMl = entries.sumOf { it.amountMl }
        val safeGoal = prefs.dailyGoalMl.coerceAtLeast(1)
        val remainingMl = (safeGoal - totalMl).coerceAtLeast(0)
        val fraction = (totalMl.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1.5f)
        val lastDrinkTs = entries.maxOfOrNull { it.timestampMillis }

        val buddyStatus = BuddyMoodCalculator.evaluate(
            totalMl = totalMl,
            goalMl = safeGoal,
            lastDrinkTimestampMillis = lastDrinkTs,
            preferences = prefs,
            justLoggedMilestone = recentMilestone,
            justLoggedDrink = justLogged
        )
        val (currentStreak, _) = GamificationUtils.calculateStreaks(allProgress, todayIso)
        val levelInfo = GamificationUtils.calculateLevel(totalXp)

        HomeUiState.Success(
            greeting = DateUtils.greetingForHour(prefs.userName),
            displayDate = DateUtils.formatDisplayDate(todayIso),
            todayIso = todayIso,
            totalMl = totalMl,
            goalMl = safeGoal,
            remainingMl = remainingMl,
            progressFraction = fraction.coerceIn(0f, 1f),
            unit = prefs.unit,
            buddyMood = buddyStatus.mood,
            buddyMessage = buddyStatus.message,
            entries = entries,
            currentStreak = currentStreak,
            levelInfo = levelInfo,
            quickAddAmountsMl = UnitConverter.quickAddAmountsMl(prefs.unit)
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    fun refreshDateIfNeeded() {
        val currentIso = DateUtils.todayIso()
        if (todayIsoFlow.value != currentIso) {
            todayIsoFlow.value = currentIso
        }
    }

    fun logWater(amountMl: Int, drinkType: DrinkType = DrinkType.WATER) {
        val now = System.currentTimeMillis()
        // Prevent accidental hardware double-tap duplicates within 220ms
        if (now - lastTapMillis < 220L) return
        lastTapMillis = now

        refreshDateIfNeeded()
        dropletBurstTriggerFlow.value += 1
        justLoggedDrinkFlow.value = true

        viewModelScope.launch {
            val prefs = repository.getCurrentPreferences()
            val result = repository.addWaterEntry(
                amountMl = amountMl,
                drinkType = drinkType,
                nowMillis = now
            )

            recentMilestoneFlow.value = result.milestoneReached

            if (result.newlyUnlockedAchievements.isNotEmpty()) {
                unlockedQueueFlow.value = unlockedQueueFlow.value + result.newlyUnlockedAchievements
            }

            if (prefs.soundEnabled) {
                launch {
                    SoundFeedbackPlayer.playWaterDropChime(isGoalReached = result.goalReachedNow)
                }
            }

            _feedbackEvents.emit(
                HomeFeedbackEvent(
                    entryId = result.entryId,
                    amountMl = amountMl,
                    goalReachedNow = result.goalReachedNow,
                    milestoneReached = result.milestoneReached
                )
            )

            delay(3200L)
            justLoggedDrinkFlow.value = false
            recentMilestoneFlow.value = null
        }
    }

    fun undoEntry(entryId: Long) {
        viewModelScope.launch {
            repository.deleteWaterEntry(entryId)
            justLoggedDrinkFlow.value = false
            recentMilestoneFlow.value = null
        }
    }

    fun updateEntry(entry: WaterEntryEntity) {
        viewModelScope.launch {
            repository.updateWaterEntry(entry)
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            repository.deleteWaterEntry(entryId)
        }
    }

    fun restoreEntry(entry: WaterEntryEntity) {
        viewModelScope.launch {
            repository.restoreWaterEntry(entry)
        }
    }

    fun updateDailyGoal(goalMl: Int) {
        viewModelScope.launch {
            repository.updatePreferences { it.copy(dailyGoalMl = goalMl.coerceIn(400, 8000)) }
        }
    }

    fun triggerBuddyCheer() {
        dropletBurstTriggerFlow.value += 1
        viewModelScope.launch {
            justLoggedDrinkFlow.value = true
            delay(2000L)
            justLoggedDrinkFlow.value = false
        }
    }

    fun dismissUnlockedAchievement() {
        val current = unlockedQueueFlow.value
        if (current.isNotEmpty()) {
            unlockedQueueFlow.value = current.drop(1)
        }
    }
}

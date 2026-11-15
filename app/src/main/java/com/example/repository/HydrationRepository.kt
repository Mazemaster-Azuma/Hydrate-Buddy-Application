package com.example.repository

import com.example.data.AchievementCatalog
import com.example.data.UserPreferencesDataStore
import com.example.database.AchievementEntity
import com.example.database.DailyProgressEntity
import com.example.database.HydrateDatabase
import com.example.database.UserPreferencesEntity
import com.example.database.WaterEntryEntity
import com.example.model.AddWaterResult
import com.example.model.DrinkType
import com.example.model.UserPreferences
import com.example.utils.DateUtils
import com.example.utils.GamificationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class HydrationRepository(
    private val database: HydrateDatabase,
    private val preferencesDataStore: UserPreferencesDataStore
) {
    private val waterEntryDao = database.waterEntryDao()
    private val dailyProgressDao = database.dailyProgressDao()
    private val achievementDao = database.achievementDao()
    private val userPreferencesDao = database.userPreferencesDao()

    private val writeMutex = Mutex()
    @Volatile
    private var achievementsSeeded = false

    val preferencesFlow: Flow<UserPreferences> = preferencesDataStore.preferencesFlow

    fun observeEntriesForDate(dateIso: String): Flow<List<WaterEntryEntity>> =
        waterEntryDao.observeEntriesForDate(dateIso)

    fun observeTotalMlForDate(dateIso: String): Flow<Int> =
        waterEntryDao.observeTotalMlForDate(dateIso)

    val allProgressFlow: Flow<List<DailyProgressEntity>> =
        dailyProgressDao.observeAllProgress()

    val achievementsFlow: Flow<List<AchievementEntity>> =
        achievementDao.observeAllAchievements()

    val lifetimeTotalMlFlow: Flow<Int> =
        waterEntryDao.observeLifetimeTotalMl()

    val totalXpFlow: Flow<Int> = combine(
        achievementsFlow,
        waterEntryDao.observeTotalEntryCount(),
        allProgressFlow
    ) { achievements, entryCount, progressList ->
        val achievementXp = achievements.filter { it.isUnlocked }.sumOf { it.xpReward }
        val goalsMetDays = progressList.count { it.goalReached }
        achievementXp + (entryCount * 10) + (goalsMetDays * 25)
    }

    private suspend fun ensureAchievementsSeededLocked() {
        if (!achievementsSeeded) {
            achievementDao.insertAllIfAbsent(AchievementCatalog.initialAchievements())
            achievementsSeeded = true
        }
    }

    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            ensureAchievementsSeededLocked()
            val prefs = preferencesDataStore.getCurrentPreferences()
            syncPreferencesToRoom(prefs)
        }
    }

    suspend fun getCurrentPreferences(): UserPreferences =
        preferencesDataStore.getCurrentPreferences()

    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences): UserPreferences =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val previous = preferencesDataStore.getCurrentPreferences()
                val updated = preferencesDataStore.updatePreferences(transform)
                syncPreferencesToRoom(updated)
                if (updated.dailyGoalMl != previous.dailyGoalMl ||
                    updated.wakeHour != previous.wakeHour ||
                    updated.wakeMinute != previous.wakeMinute
                ) {
                    val today = DateUtils.todayIso()
                    recalculateDailyProgressLocked(today, updated.dailyGoalMl)
                    evaluateAchievementsLocked(today, updated, System.currentTimeMillis())
                }
                updated
            }
        }

    suspend fun addWaterEntry(
        amountMl: Int,
        drinkType: DrinkType = DrinkType.WATER,
        nowMillis: Long = System.currentTimeMillis()
    ): AddWaterResult = withContext(Dispatchers.IO) {
        val safeAmount = amountMl.coerceIn(10, 5000)
        writeMutex.withLock {
            ensureAchievementsSeededLocked()
            val prefs = preferencesDataStore.getCurrentPreferences()
            val today = DateUtils.isoFromTimestamp(nowMillis)
            val prevTotal = waterEntryDao.getTotalMlForDate(today)
            val safeGoal = prefs.dailyGoalMl.coerceAtLeast(1)
            val prevPct = (prevTotal * 100) / safeGoal

            val entryId = waterEntryDao.insertEntry(
                WaterEntryEntity(
                    amountMl = safeAmount,
                    timestampMillis = nowMillis,
                    dateIso = today,
                    drinkType = drinkType.name
                )
            )

            val newTotal = recalculateDailyProgressLocked(today, safeGoal, nowMillis)
            val newPct = (newTotal * 100) / safeGoal

            val goalReachedNow = prevTotal < safeGoal && newTotal >= safeGoal
            val milestoneReached = when {
                goalReachedNow -> 100
                prevPct < 75 && newPct >= 75 -> 75
                prevPct < 50 && newPct >= 50 -> 50
                prevPct < 25 && newPct >= 25 -> 25
                else -> null
            }

            val unlockedNow = evaluateAchievementsLocked(today, prefs, nowMillis, latestEntryMillis = nowMillis)

            AddWaterResult(
                entryId = entryId,
                newTotalMl = newTotal,
                goalReachedNow = goalReachedNow,
                milestoneReached = milestoneReached,
                newlyUnlockedAchievements = unlockedNow
            )
        }
    }

    suspend fun addWaterEntryForDate(
        dateIso: String,
        amountMl: Int,
        drinkType: DrinkType = DrinkType.WATER
    ) = withContext(Dispatchers.IO) {
        val safeAmount = amountMl.coerceIn(10, 5000)
        writeMutex.withLock {
            ensureAchievementsSeededLocked()
            val prefs = preferencesDataStore.getCurrentPreferences()
            val cal = DateUtils.parseIsoToCalendar(dateIso).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 12)
                set(java.util.Calendar.MINUTE, 0)
            }
            val timestampMillis = if (dateIso == DateUtils.todayIso()) {
                System.currentTimeMillis()
            } else {
                cal.timeInMillis
            }
            waterEntryDao.insertEntry(
                WaterEntryEntity(
                    amountMl = safeAmount,
                    timestampMillis = timestampMillis,
                    dateIso = dateIso,
                    drinkType = drinkType.name
                )
            )
            recalculateDailyProgressLocked(dateIso, prefs.dailyGoalMl, System.currentTimeMillis())
            evaluateAchievementsLocked(DateUtils.todayIso(), prefs, System.currentTimeMillis())
        }
    }

    suspend fun updateWaterEntry(entry: WaterEntryEntity) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val prefs = preferencesDataStore.getCurrentPreferences()
            waterEntryDao.updateEntry(entry)
            recalculateDailyProgressLocked(entry.dateIso, prefs.dailyGoalMl)
            evaluateAchievementsLocked(DateUtils.todayIso(), prefs, System.currentTimeMillis())
        }
    }

    suspend fun deleteWaterEntry(entryId: Long) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val entry = waterEntryDao.getEntryById(entryId) ?: return@withLock
            val prefs = preferencesDataStore.getCurrentPreferences()
            waterEntryDao.deleteEntryById(entryId)
            recalculateDailyProgressLocked(entry.dateIso, prefs.dailyGoalMl)
        }
    }

    suspend fun restoreWaterEntry(entry: WaterEntryEntity) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            ensureAchievementsSeededLocked()
            val prefs = preferencesDataStore.getCurrentPreferences()
            waterEntryDao.insertEntry(entry.copy(id = 0))
            recalculateDailyProgressLocked(entry.dateIso, prefs.dailyGoalMl)
            evaluateAchievementsLocked(DateUtils.todayIso(), prefs, System.currentTimeMillis())
        }
    }

    suspend fun resetTodayProgress(todayIso: String = DateUtils.todayIso()) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val prefs = preferencesDataStore.getCurrentPreferences()
            waterEntryDao.deleteEntriesForDate(todayIso)
            recalculateDailyProgressLocked(todayIso, prefs.dailyGoalMl)
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            waterEntryDao.deleteAllEntries()
            dailyProgressDao.deleteAllProgress()
            achievementDao.deleteAllAchievements()
            userPreferencesDao.deleteAllPreferences()
            preferencesDataStore.clearAll()
            achievementsSeeded = false
            ensureAchievementsSeededLocked()
            syncPreferencesToRoom(UserPreferences())
        }
    }

    private suspend fun recalculateDailyProgressLocked(
        dateIso: String,
        goalMl: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val entries = waterEntryDao.getEntriesForDate(dateIso)
        val totalMl = entries.sumOf { it.amountMl }
        val safeGoal = goalMl.coerceAtLeast(1)
        if (entries.isEmpty()) {
            dailyProgressDao.deleteProgressForDate(dateIso)
        } else {
            dailyProgressDao.upsertProgress(
                DailyProgressEntity(
                    dateIso = dateIso,
                    totalMl = totalMl,
                    goalMl = safeGoal,
                    entryCount = entries.size,
                    goalReached = totalMl >= safeGoal,
                    updatedAtMillis = nowMillis
                )
            )
        }
        return totalMl
    }

    private suspend fun evaluateAchievementsLocked(
        todayIso: String,
        prefs: UserPreferences,
        nowMillis: Long,
        latestEntryMillis: Long? = null
    ): List<AchievementEntity> {
        val currentList = achievementDao.getAllAchievements()
            .ifEmpty { AchievementCatalog.initialAchievements() }

        val todayEntries = waterEntryDao.getEntriesForDate(todayIso)
        val todayTotal = todayEntries.sumOf { it.amountMl }
        val safeGoal = prefs.dailyGoalMl.coerceAtLeast(1)
        val todayPct = ((todayTotal.toFloat() / safeGoal.toFloat()) * 100f).toInt()
        val totalEntryCount = waterEntryDao.getTotalEntryCount()
        val lifetimeTotalMl = waterEntryDao.getLifetimeTotalMl()
        val distinctDrinkTypes = waterEntryDao.getDistinctDrinkTypeCount()
        val allProgress = dailyProgressDao.getAllProgress()
        val (currentStreak, longestStreak) = GamificationUtils.calculateStreaks(allProgress, todayIso)
        val bestStreak = maxOf(currentStreak, longestStreak)

        val anyMorningSplashToday = todayEntries.any {
            DateUtils.isWithinMinutesAfterWake(
                timestampMillis = it.timestampMillis,
                wakeHour = prefs.wakeHour,
                wakeMinute = prefs.wakeMinute,
                windowMinutes = 60
            )
        } || (latestEntryMillis != null && DateUtils.isWithinMinutesAfterWake(
            timestampMillis = latestEntryMillis,
            wakeHour = prefs.wakeHour,
            wakeMinute = prefs.wakeMinute,
            windowMinutes = 60
        ))

        val newlyUnlocked = mutableListOf<AchievementEntity>()
        val updatedList = currentList.map { item ->
            val newProgress = when (item.id) {
                AchievementCatalog.ID_FIRST_DROP -> if (totalEntryCount >= 1) 1 else 0
                AchievementCatalog.ID_GETTING_STARTED -> todayPct.coerceAtMost(50)
                AchievementCatalog.ID_HYDRATION_HERO -> todayPct.coerceAtMost(100)
                AchievementCatalog.ID_THREE_DAY_STREAK -> bestStreak.coerceAtMost(3)
                AchievementCatalog.ID_SEVEN_DAY_CHAMPION -> bestStreak.coerceAtMost(7)
                AchievementCatalog.ID_EARLY_BIRD -> if (anyMorningSplashToday) 1 else 0
                AchievementCatalog.ID_STEADY_SIPPER -> todayEntries.size.coerceAtMost(5)
                AchievementCatalog.ID_VARIETY_SIPPER -> distinctDrinkTypes.coerceAtMost(3)
                AchievementCatalog.ID_OVERACHIEVER -> todayPct.coerceAtMost(125)
                AchievementCatalog.ID_LITER_CLUB -> lifetimeTotalMl.coerceAtMost(10000)
                else -> item.progressCurrent
            }

            val shouldUnlock = item.isUnlocked || newProgress >= item.progressTarget
            val effectiveProgress = if (shouldUnlock) item.progressTarget else maxOf(item.progressCurrent, newProgress)
            val updatedItem = item.copy(
                isUnlocked = shouldUnlock,
                unlockedAtMillis = if (shouldUnlock) (item.unlockedAtMillis ?: nowMillis) else null,
                progressCurrent = effectiveProgress
            )
            if (!item.isUnlocked && updatedItem.isUnlocked) {
                newlyUnlocked.add(updatedItem)
            }
            updatedItem
        }

        achievementDao.upsertAchievements(updatedList)
        return newlyUnlocked
    }

    private suspend fun syncPreferencesToRoom(prefs: UserPreferences) {
        userPreferencesDao.upsertPreferences(
            UserPreferencesEntity(
                id = 1,
                userName = prefs.userName,
                dailyGoalMl = prefs.dailyGoalMl,
                unit = prefs.unit.name,
                wakeHour = prefs.wakeHour,
                wakeMinute = prefs.wakeMinute,
                bedHour = prefs.bedHour,
                bedMinute = prefs.bedMinute,
                reminderIntervalMinutes = prefs.reminderIntervalMinutes,
                remindersEnabled = prefs.remindersEnabled,
                ringingReminderEnabled = prefs.ringingReminderEnabled,
                soundEnabled = prefs.soundEnabled,
                vibrationEnabled = prefs.vibrationEnabled,
                themeMode = prefs.themeMode.name,
                onboardingCompleted = prefs.onboardingCompleted
            )
        )
    }
}

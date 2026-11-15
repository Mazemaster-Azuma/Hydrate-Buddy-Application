package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterEntryDao {
    @Query("SELECT * FROM water_entries WHERE dateIso = :dateIso ORDER BY timestampMillis DESC")
    fun observeEntriesForDate(dateIso: String): Flow<List<WaterEntryEntity>>

    @Query("SELECT * FROM water_entries WHERE dateIso = :dateIso ORDER BY timestampMillis DESC")
    suspend fun getEntriesForDate(dateIso: String): List<WaterEntryEntity>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE dateIso = :dateIso")
    fun observeTotalMlForDate(dateIso: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE dateIso = :dateIso")
    suspend fun getTotalMlForDate(dateIso: String): Int

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries")
    fun observeLifetimeTotalMl(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries")
    suspend fun getLifetimeTotalMl(): Int

    @Query("SELECT COUNT(*) FROM water_entries")
    fun observeTotalEntryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM water_entries")
    suspend fun getTotalEntryCount(): Int

    @Query("SELECT COUNT(DISTINCT drinkType) FROM water_entries")
    suspend fun getDistinctDrinkTypeCount(): Int

    @Query("SELECT * FROM water_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): WaterEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WaterEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: WaterEntryEntity)

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM water_entries WHERE dateIso = :dateIso")
    suspend fun deleteEntriesForDate(dateIso: String)

    @Query("DELETE FROM water_entries")
    suspend fun deleteAllEntries()
}

@Dao
interface DailyProgressDao {
    @Query("SELECT * FROM daily_progress ORDER BY dateIso DESC")
    fun observeAllProgress(): Flow<List<DailyProgressEntity>>

    @Query("SELECT * FROM daily_progress ORDER BY dateIso DESC")
    suspend fun getAllProgress(): List<DailyProgressEntity>

    @Query("SELECT * FROM daily_progress WHERE dateIso = :dateIso LIMIT 1")
    suspend fun getProgressForDate(dateIso: String): DailyProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: DailyProgressEntity)

    @Query("DELETE FROM daily_progress WHERE dateIso = :dateIso")
    suspend fun deleteProgressForDate(dateIso: String)

    @Query("DELETE FROM daily_progress")
    suspend fun deleteAllProgress()
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY isUnlocked DESC, xpReward ASC")
    fun observeAllAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun getAllAchievements(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(achievements: List<AchievementEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAchievements(achievements: List<AchievementEntity>)

    @Query("DELETE FROM achievements")
    suspend fun deleteAllAchievements()
}

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    fun observePreferences(): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    suspend fun getPreferences(): UserPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(entity: UserPreferencesEntity)

    @Query("DELETE FROM user_preferences")
    suspend fun deleteAllPreferences()
}

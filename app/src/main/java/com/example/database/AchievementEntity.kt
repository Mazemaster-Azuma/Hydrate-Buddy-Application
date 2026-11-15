package com.example.database

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val xpReward: Int,
    val isUnlocked: Boolean,
    val unlockedAtMillis: Long?,
    val progressCurrent: Int,
    val progressTarget: Int,
    val category: String
)

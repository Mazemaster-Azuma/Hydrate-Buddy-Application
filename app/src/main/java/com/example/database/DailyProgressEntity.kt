package com.example.database

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "daily_progress")
data class DailyProgressEntity(
    @PrimaryKey val dateIso: String,
    val totalMl: Int,
    val goalMl: Int,
    val entryCount: Int,
    val goalReached: Boolean,
    val updatedAtMillis: Long
)

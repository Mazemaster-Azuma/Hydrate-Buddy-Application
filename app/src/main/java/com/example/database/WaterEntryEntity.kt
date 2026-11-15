package com.example.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "water_entries",
    indices = [Index(value = ["dateIso"]), Index(value = ["timestampMillis"])]
)
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMl: Int,
    val timestampMillis: Long,
    val dateIso: String,
    val drinkType: String
)

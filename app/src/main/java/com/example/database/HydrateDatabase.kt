package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WaterEntryEntity::class,
        DailyProgressEntity::class,
        AchievementEntity::class,
        UserPreferencesEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class HydrateDatabase : RoomDatabase() {
    abstract fun waterEntryDao(): WaterEntryDao
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun achievementDao(): AchievementDao
    abstract fun userPreferencesDao(): UserPreferencesDao

    companion object {
        @Volatile
        private var INSTANCE: HydrateDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN ringingReminderEnabled INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): HydrateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HydrateDatabase::class.java,
                    "hydrate_buddy_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

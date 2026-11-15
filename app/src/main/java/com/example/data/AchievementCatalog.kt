package com.example.data

import com.example.database.AchievementEntity

object AchievementCatalog {
    const val ID_FIRST_DROP = "first_drop"
    const val ID_GETTING_STARTED = "getting_started"
    const val ID_HYDRATION_HERO = "hydration_hero"
    const val ID_THREE_DAY_STREAK = "three_day_streak"
    const val ID_SEVEN_DAY_CHAMPION = "seven_day_champion"
    const val ID_EARLY_BIRD = "early_bird"
    const val ID_VARIETY_SIPPER = "variety_sipper"
    const val ID_OVERACHIEVER = "overachiever"
    const val ID_STEADY_SIPPER = "steady_sipper"
    const val ID_LITER_CLUB = "liter_club"

    fun initialAchievements(): List<AchievementEntity> = listOf(
        AchievementEntity(
            id = ID_FIRST_DROP,
            title = "First Drop",
            description = "Log your very first drink with Droplet.",
            xpReward = 50,
            isUnlocked = false,
            unlockedAtMillis = null,
            progressCurrent = 0,
            progressTarget = 1,
            category = "Milestones"
        ),
        AchievementEntity(
            id = ID_GETTING_STARTED,
            title = "Getting Started",
            description = "Reach 50% of your daily hydration goal.",
            xpReward = 75,
            isUnlocked = false,
            unlockedAtMillis = null,
            progressCurrent = 0,
            progressTarget = 50,
            category = "Daily"
        ),
        AchievementEntity(
            id = ID_HYDRATION_HERO,
            title = "Hydration Hero",
            description = "Complete 100% of your daily hydration goal.",
            xpReward = 150,
            isUnlocked = false,
            unlockedAtMillis = null,
            progressCurrent = 0,
            progressTarget = 100,
            category = "Daily"
        ),
        AchievementEntity(
            id = ID_THREE_DAY_STREAK,
            title = "Three-Day Streak",
            description = "Reach your daily goal 3 days in a row.",
            xpReward = 250,
            isUnlocked = false,
            unlockedAtMillis = null,
            progressCurrent = 0,
            progressTarget = 3,
            category = "Streaks"
        ),
        AchievementEntity(
            id = ID_SEVEN_DAY_CHAMPION,
            title = "Seven-Day Champion",
            description = "Complete 7 consecutive days of your hydration goal.",
            xpReward = 500,
            isUnlocked = false,
            unlockedAtMillis = null,
            progressCurrent = 0,
            progressTarget = 7,
            category = "Streaks"
        ),
        AchievementEntity(
            id = ID_EARLY_BIRD,
            title = "Morning Splash",
            description = "Log water within 1 hour of your wake-up time.",
            xpReward = 100,
            isUnlocked = false,
            unlockedAtMillis = null,
            progressCurrent = 0,
            progressTarget = 1,
            category = "Habits"
        ),
        AchievementEntity(
            id = ID_STEADY_SIPPER,
            title = "Steady Flow",
            description = "Log at least 5 separate drinks in a single day.",
            xpReward = 100,
            isUnlocked = false,
            unlockedAtMillis = null,
            progressCurrent = 0,
            progressTarget = 5,
            category = "Habits"
        ),
        AchievementEntity(
            id = ID_VARIETY_SIPPER,
            title = "Flavor Explorer",
            description = "Try 3 different drink types in your log.",
            xpReward = 100,
            isUnlocked = false,
            unlockedAtMillis = null,
            progressCurrent = 0,
            progressTarget = 3,
            category = "Habits"
        ),
        AchievementEntity(
            id = ID_OVERACHIEVER,
            title = "Aqua Overflow",
            description = "Reach 125% of your daily hydration target.",
            xpReward = 150,
            isUnlocked = false,
            unlockedAtMillis = null,
            progressCurrent = 0,
            progressTarget = 125,
            category = "Milestones"
        ),
        AchievementEntity(
            id = ID_LITER_CLUB,
            title = "10-Liter Club",
            description = "Drink a lifetime total of 10,000 ml (10 liters).",
            xpReward = 300,
            isUnlocked = false,
            unlockedAtMillis = null,
            progressCurrent = 0,
            progressTarget = 10000,
            category = "Lifetime"
        )
    )
}

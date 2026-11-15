package com.example.utils

import com.example.database.DailyProgressEntity
import com.example.model.LevelInfo

object GamificationUtils {

    private val levelTitles = listOf(
        "Dewdrop",
        "Splasher",
        "Brook",
        "Stream",
        "Wave Rider",
        "Tide Keeper",
        "Aqua Hero",
        "Ocean Guardian"
    )

    fun calculateLevel(totalXp: Int): LevelInfo {
        // Level 1: 0..149, Level 2: 150..349, Level 3: 350..649, Level 4: 650..1049, ...
        var level = 1
        var threshold = 0
        var step = 150

        while (totalXp >= threshold + step) {
            threshold += step
            level++
            step += 100
        }

        val title = levelTitles.getOrElse(level - 1) { "Hydro Legend $level" }
        return LevelInfo(
            level = level,
            title = title,
            totalXp = totalXp,
            currentLevelBaseXp = threshold,
            nextLevelXp = threshold + step
        )
    }

    /**
     * Computes current active streak and longest streak from historical daily progress records.
     * Current streak counts consecutive days reaching the goal ending today or yesterday
     * (so a streak from yesterday isn't lost at 8:00 AM before today's goal is completed).
     */
    fun calculateStreaks(
        progressList: List<DailyProgressEntity>,
        todayIso: String
    ): Pair<Int, Int> {
        val goalMetDates = progressList
            .filter { it.goalReached }
            .map { it.dateIso }
            .toSet()

        if (goalMetDates.isEmpty()) return 0 to 0

        // Current streak
        val yesterdayIso = DateUtils.dateIsoOffset(todayIso, -1)
        var currentStreak = 0
        var cursor = when {
            goalMetDates.contains(todayIso) -> todayIso
            goalMetDates.contains(yesterdayIso) -> yesterdayIso
            else -> null
        }

        while (cursor != null && goalMetDates.contains(cursor)) {
            currentStreak++
            cursor = DateUtils.dateIsoOffset(cursor, -1)
        }

        // Longest streak across all recorded dates
        val sortedDates = goalMetDates.sorted()
        var longestStreak = 0
        var runLength = 0
        var prevDate: String? = null

        for (date in sortedDates) {
            if (prevDate == null || DateUtils.dateIsoOffset(prevDate, 1) == date) {
                runLength++
            } else {
                if (runLength > longestStreak) longestStreak = runLength
                runLength = 1
            }
            prevDate = date
        }
        if (runLength > longestStreak) longestStreak = runLength

        return currentStreak to maxOf(longestStreak, currentStreak)
    }
}

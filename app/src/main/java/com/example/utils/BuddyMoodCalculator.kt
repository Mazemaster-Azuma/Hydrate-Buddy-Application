package com.example.utils

import com.example.model.BuddyMood
import com.example.model.UserPreferences

object BuddyMoodCalculator {

    data class BuddyStatus(
        val mood: BuddyMood,
        val message: String
    )

    fun evaluate(
        totalMl: Int,
        goalMl: Int,
        lastDrinkTimestampMillis: Long?,
        preferences: UserPreferences,
        justLoggedMilestone: Int? = null,
        justLoggedDrink: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): BuddyStatus {
        val safeGoal = goalMl.coerceAtLeast(1)
        val progress = totalMl.toFloat() / safeGoal.toFloat()

        // Daily goal reached or exceeded
        if (progress >= 1.0f) {
            val msg = if (progress >= 1.25f) {
                "Whoa, hydration superstar! Droplet is splashing with joy!"
            } else {
                "Daily goal complete! Droplet is doing a happy victory dance!"
            }
            return BuddyStatus(BuddyMood.CELEBRATING, msg)
        }

        // Just hit a milestone or logged water right now
        if (justLoggedMilestone != null) {
            return BuddyStatus(
                BuddyMood.EXCITED,
                "Awesome! You reached $justLoggedMilestone% of today's target!"
            )
        }

        if (justLoggedDrink) {
            return BuddyStatus(
                BuddyMood.EXCITED,
                "Splash! That sip felt so refreshing!"
            )
        }

        // No water logged yet today
        if (totalMl == 0 || lastDrinkTimestampMillis == null) {
            val wakingElapsed = DateUtils.getWakingFractionElapsed(
                nowMillis = nowMillis,
                wakeHour = preferences.wakeHour,
                wakeMinute = preferences.wakeMinute,
                bedHour = preferences.bedHour,
                bedMinute = preferences.bedMinute
            )
            return if (wakingElapsed >= 0.25f) {
                BuddyStatus(
                    BuddyMood.THIRSTY,
                    "Droplet is feeling parched! Time for your first refreshing glass?"
                )
            } else {
                BuddyStatus(
                    BuddyMood.NEUTRAL,
                    "Your buddy is waiting for some water!"
                )
            }
        }

        val hoursSinceLastDrink = (nowMillis - lastDrinkTimestampMillis) / (1000f * 60f * 60f)
        val wakingElapsed = DateUtils.getWakingFractionElapsed(
            nowMillis = nowMillis,
            wakeHour = preferences.wakeHour,
            wakeMinute = preferences.wakeMinute,
            bedHour = preferences.bedHour,
            bedMinute = preferences.bedMinute
        )

        val behindSchedule = wakingElapsed > 0.35f && progress < (wakingElapsed - 0.28f)

        return when {
            hoursSinceLastDrink >= 3.5f || (behindSchedule && hoursSinceLastDrink >= 2.0f) -> {
                BuddyStatus(
                    BuddyMood.THIRSTY,
                    "It's been a little while! Droplet could really use a sip."
                )
            }
            hoursSinceLastDrink >= 2.0f -> {
                BuddyStatus(
                    BuddyMood.NEUTRAL,
                    "Doing steady! Ready for another splash soon?"
                )
            }
            progress >= 0.75f -> {
                BuddyStatus(
                    BuddyMood.HAPPY,
                    "So close! Just a little more to reach today's goal!"
                )
            }
            else -> {
                BuddyStatus(
                    BuddyMood.HAPPY,
                    "Droplet is feeling fresh and energized with you!"
                )
            }
        }
    }
}

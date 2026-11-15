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
        justTappedCheer: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): BuddyStatus {
        val safeGoal = goalMl.coerceAtLeast(1)
        val progress = totalMl.toFloat() / safeGoal.toFloat()

        // 1. Daily goal reached or exceeded
        if (progress >= 1.0f) {
            val msg = when {
                justTappedCheer -> "High-five, hydration hero! Droplet couldn't be prouder!"
                progress >= 1.25f -> "Whoa, hydration superstar! Droplet is splashing with joy!"
                else -> "Daily goal complete! Droplet is doing a happy victory dance!"
            }
            return BuddyStatus(BuddyMood.CELEBRATING, msg)
        }

        // 2. Just hit a milestone or logged water right now
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

        val wakingElapsed = DateUtils.getWakingFractionElapsed(
            nowMillis = nowMillis,
            wakeHour = preferences.wakeHour,
            wakeMinute = preferences.wakeMinute,
            bedHour = preferences.bedHour,
            bedMinute = preferences.bedMinute
        )

        // 3. No water logged yet today
        if (totalMl == 0 || lastDrinkTimestampMillis == null) {
            return when {
                wakingElapsed >= 0.30f -> {
                    val msg = if (justTappedCheer) {
                        "Droplet is feeling weak — a glass of water would cheer them right up!"
                    } else {
                        "Droplet is feeling dry and sad — not a single sip logged today!"
                    }
                    BuddyStatus(BuddyMood.SAD, msg)
                }
                wakingElapsed >= 0.08f -> {
                    val msg = if (justTappedCheer) {
                        "Tap a cup below to quench Droplet's morning thirst!"
                    } else {
                        "Droplet is getting thirsty! How about your first glass of water?"
                    }
                    BuddyStatus(BuddyMood.THIRSTY, msg)
                }
                else -> {
                    if (justTappedCheer) {
                        BuddyStatus(
                            BuddyMood.EXCITED,
                            "Good morning! Ready to kick off today's hydration?"
                        )
                    } else {
                        BuddyStatus(
                            BuddyMood.NEUTRAL,
                            "Your buddy is waiting for today's first refreshing sip!"
                        )
                    }
                }
            }
        }

        // 4. Water logged today: evaluate time since last sip + pacing against waking schedule
        val minutesSinceLastDrink = ((nowMillis - lastDrinkTimestampMillis).coerceAtLeast(0L)) / (1000f * 60f)
        val intervalMinutes = preferences.reminderIntervalMinutes.coerceIn(30, 360).toFloat()

        // Responsive thresholds anchored to user's reminder interval
        val neutralMinutes = (intervalMinutes * 0.78f).coerceIn(50f, 120f)
        val thirstyMinutes = (intervalMinutes * 1.25f).coerceIn(75f, 150f)
        val sadMinutes = (intervalMinutes * 2.5f).coerceIn(195f, 300f)

        val moderatelyBehind = wakingElapsed >= 0.30f && progress < (wakingElapsed - 0.22f)
        val severelyBehind = wakingElapsed >= 0.55f && progress < (wakingElapsed - 0.38f)

        return when {
            minutesSinceLastDrink >= sadMinutes ||
                (severelyBehind && minutesSinceLastDrink >= intervalMinutes * 1.35f) -> {
                val msg = when {
                    justTappedCheer -> "Droplet perked up a tiny bit — please give them a refreshing sip!"
                    severelyBehind -> "Droplet is drooping and sad — you're way behind today's goal!"
                    else -> "Droplet is feeling sad and dehydrated — it's been hours since your last sip!"
                }
                BuddyStatus(BuddyMood.SAD, msg)
            }
            minutesSinceLastDrink >= thirstyMinutes ||
                (moderatelyBehind && minutesSinceLastDrink >= 45f) -> {
                val msg = when {
                    justTappedCheer -> "Droplet's throat is dry — tap a quick-add cup below!"
                    moderatelyBehind -> "Falling a bit behind today! Droplet could really use a sip."
                    else -> "Droplet is feeling thirsty! Time for a refreshing glass of water."
                }
                BuddyStatus(BuddyMood.THIRSTY, msg)
            }
            justTappedCheer -> {
                BuddyStatus(
                    BuddyMood.EXCITED,
                    "Yay! Droplet loves the high-five — keep those sips flowing!"
                )
            }
            minutesSinceLastDrink >= neutralMinutes -> {
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

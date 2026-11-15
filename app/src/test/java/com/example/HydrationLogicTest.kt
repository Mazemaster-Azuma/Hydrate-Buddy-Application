package com.example

import com.example.database.DailyProgressEntity
import com.example.model.BuddyMood
import com.example.model.MeasurementUnit
import com.example.model.UserPreferences
import com.example.utils.BuddyMoodCalculator
import com.example.utils.DateUtils
import com.example.utils.GamificationUtils
import com.example.utils.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HydrationLogicTest {

    @Test
    fun `unit converter formats ml and oz accurately`() {
        assertEquals("250 ml", UnitConverter.formatVolume(250, MeasurementUnit.ML))
        assertEquals("8 oz", UnitConverter.formatVolume(237, MeasurementUnit.OZ))
        assertEquals(2000, UnitConverter.displayInputToMl(2000f, MeasurementUnit.ML))
        assertTrue(UnitConverter.displayInputToMl(64f, MeasurementUnit.OZ) in 1890..1895)
    }

    @Test
    fun `buddy mood reflects progress, time gaps, thirsty and sad states`() {
        val prefs = UserPreferences(
            dailyGoalMl = 2000,
            wakeHour = 7,
            wakeMinute = 0,
            bedHour = 22,
            bedMinute = 30,
            reminderIntervalMinutes = 90
        )

        fun timeAt(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val morning730 = timeAt(7, 30)
        val morning930 = timeAt(9, 30)
        val afternoon1400 = timeAt(14, 0)
        val evening1830 = timeAt(18, 30)

        // Goal complete -> Celebrating
        val celebrating = BuddyMoodCalculator.evaluate(
            totalMl = 2000,
            goalMl = 2000,
            lastDrinkTimestampMillis = afternoon1400,
            preferences = prefs,
            nowMillis = afternoon1400
        )
        assertEquals(BuddyMood.CELEBRATING, celebrating.mood)

        // Milestone logged -> Excited
        val excited = BuddyMoodCalculator.evaluate(
            totalMl = 1000,
            goalMl = 2000,
            lastDrinkTimestampMillis = afternoon1400,
            preferences = prefs,
            justLoggedMilestone = 50,
            nowMillis = afternoon1400
        )
        assertEquals(BuddyMood.EXCITED, excited.mood)

        // Freshly hydrated -> Happy
        val happy = BuddyMoodCalculator.evaluate(
            totalMl = 1000,
            goalMl = 2000,
            lastDrinkTimestampMillis = timeAt(13, 30),
            preferences = prefs,
            nowMillis = afternoon1400
        )
        assertEquals(BuddyMood.HAPPY, happy.mood)

        // No sips early right after waking -> Neutral
        val earlyWaiting = BuddyMoodCalculator.evaluate(
            totalMl = 0,
            goalMl = 2000,
            lastDrinkTimestampMillis = null,
            preferences = prefs,
            nowMillis = morning730
        )
        assertEquals(BuddyMood.NEUTRAL, earlyWaiting.mood)

        // No sips by mid-morning -> Thirsty
        val morningThirsty = BuddyMoodCalculator.evaluate(
            totalMl = 0,
            goalMl = 2000,
            lastDrinkTimestampMillis = null,
            preferences = prefs,
            nowMillis = morning930
        )
        assertEquals(BuddyMood.THIRSTY, morningThirsty.mood)

        // 2 hours since last drink -> Thirsty
        val intervalThirsty = BuddyMoodCalculator.evaluate(
            totalMl = 800,
            goalMl = 2000,
            lastDrinkTimestampMillis = timeAt(12, 0),
            preferences = prefs,
            nowMillis = afternoon1400
        )
        assertEquals(BuddyMood.THIRSTY, intervalThirsty.mood)

        // No sips by afternoon -> Sad
        val zeroIntakeSad = BuddyMoodCalculator.evaluate(
            totalMl = 0,
            goalMl = 2000,
            lastDrinkTimestampMillis = null,
            preferences = prefs,
            nowMillis = afternoon1400
        )
        assertEquals(BuddyMood.SAD, zeroIntakeSad.mood)

        // 4.5 hours since last drink -> Sad
        val longGapSad = BuddyMoodCalculator.evaluate(
            totalMl = 500,
            goalMl = 2000,
            lastDrinkTimestampMillis = afternoon1400,
            preferences = prefs,
            nowMillis = evening1830
        )
        assertEquals(BuddyMood.SAD, longGapSad.mood)

        // Tapping a sad buddy preserves SAD mood instead of faking a logged drink
        val tappedWhileSad = BuddyMoodCalculator.evaluate(
            totalMl = 500,
            goalMl = 2000,
            lastDrinkTimestampMillis = afternoon1400,
            preferences = prefs,
            justTappedCheer = true,
            nowMillis = evening1830
        )
        assertEquals(BuddyMood.SAD, tappedWhileSad.mood)
    }

    @Test
    fun `streaks calculate consecutive days accurately`() {
        val today = "2026-11-14"
        val records = listOf(
            DailyProgressEntity("2026-11-14", 2000, 2000, 5, true, 1L),
            DailyProgressEntity("2026-11-13", 2100, 2000, 6, true, 1L),
            DailyProgressEntity("2026-11-12", 2000, 2000, 4, true, 1L),
            DailyProgressEntity("2026-11-10", 2000, 2000, 4, true, 1L)
        )
        val (current, longest) = GamificationUtils.calculateStreaks(records, today)
        assertEquals(3, current)
        assertEquals(3, longest)
    }

    @Test
    fun `waking hours check respects sleep schedule`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 14, 23, 30, 0)
        }
        assertFalse(
            DateUtils.isWithinWakingHours(
                nowMillis = cal.timeInMillis,
                wakeHour = 7,
                wakeMinute = 0,
                bedHour = 22,
                bedMinute = 30
            )
        )

        cal.set(Calendar.HOUR_OF_DAY, 14)
        assertTrue(
            DateUtils.isWithinWakingHours(
                nowMillis = cal.timeInMillis,
                wakeHour = 7,
                wakeMinute = 0,
                bedHour = 22,
                bedMinute = 30
            )
        )
    }
}

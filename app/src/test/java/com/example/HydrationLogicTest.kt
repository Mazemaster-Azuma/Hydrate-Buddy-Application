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
    fun `buddy mood reflects progress and celebration`() {
        val prefs = UserPreferences(dailyGoalMl = 2000)

        val celebrating = BuddyMoodCalculator.evaluate(
            totalMl = 2000,
            goalMl = 2000,
            lastDrinkTimestampMillis = System.currentTimeMillis(),
            preferences = prefs
        )
        assertEquals(BuddyMood.CELEBRATING, celebrating.mood)

        val excited = BuddyMoodCalculator.evaluate(
            totalMl = 1000,
            goalMl = 2000,
            lastDrinkTimestampMillis = System.currentTimeMillis(),
            preferences = prefs,
            justLoggedMilestone = 50
        )
        assertEquals(BuddyMood.EXCITED, excited.mood)

        val thirsty = BuddyMoodCalculator.evaluate(
            totalMl = 500,
            goalMl = 2000,
            lastDrinkTimestampMillis = System.currentTimeMillis() - (4L * 60 * 60 * 1000),
            preferences = prefs
        )
        assertEquals(BuddyMood.THIRSTY, thirsty.mood)
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

package com.example

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DrinkType
import com.example.model.MeasurementUnit
import com.example.model.UserPreferences
import com.example.notifications.HydrationNotificationReceiver
import com.example.notifications.NotificationHelper
import com.example.notifications.ReminderScheduleCalculator
import com.example.notifications.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ReminderScheduler.exactAlarmOverrideForTesting = true
        ReminderScheduler.cancelReminders(context)
    }

    @After
    fun tearDown() {
        ReminderScheduler.cancelReminders(context)
        ReminderScheduler.exactAlarmOverrideForTesting = null
    }

    private fun utcCal(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month, day, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `calculator schedules exact interval inside waking hours and chains without drift`() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = utcCal(2026, Calendar.NOVEMBER, 16, 10, 0)

        val first = ReminderScheduleCalculator.computeNextReminderTriggerMillis(
            nowMillis = now,
            intervalMinutes = 30,
            wakeHour = 7,
            wakeMinute = 0,
            bedHour = 22,
            bedMinute = 30,
            timeZone = utc
        )
        assertEquals(utcCal(2026, Calendar.NOVEMBER, 16, 10, 30), first)

        // Even if OS delivers 10:30 trigger 3 seconds late, next trigger stays anchored at 11:00
        val second = ReminderScheduleCalculator.computeNextReminderTriggerMillis(
            nowMillis = utcCal(2026, Calendar.NOVEMBER, 16, 10, 30, 3),
            intervalMinutes = 30,
            wakeHour = 7,
            wakeMinute = 0,
            bedHour = 22,
            bedMinute = 30,
            previousTriggerAtMillis = first,
            timeZone = utc
        )
        assertEquals(utcCal(2026, Calendar.NOVEMBER, 16, 11, 0), second)
    }

    @Test
    fun `calculator jumps to next morning wake time when interval lands after bedtime`() {
        val utc = TimeZone.getTimeZone("UTC")
        val lateEvening = utcCal(2026, Calendar.NOVEMBER, 16, 22, 10)

        val next = ReminderScheduleCalculator.computeNextReminderTriggerMillis(
            nowMillis = lateEvening,
            intervalMinutes = 30,
            wakeHour = 7,
            wakeMinute = 0,
            bedHour = 22,
            bedMinute = 30,
            timeZone = utc
        )
        assertEquals(utcCal(2026, Calendar.NOVEMBER, 17, 7, 0), next)
    }

    @Test
    fun `calculator supports overnight waking schedules`() {
        val utc = TimeZone.getTimeZone("UTC")
        val nightShift = utcCal(2026, Calendar.NOVEMBER, 16, 23, 30)

        val duringShift = ReminderScheduleCalculator.computeNextReminderTriggerMillis(
            nowMillis = nightShift,
            intervalMinutes = 60,
            wakeHour = 18,
            wakeMinute = 0,
            bedHour = 8,
            bedMinute = 0,
            timeZone = utc
        )
        assertEquals(utcCal(2026, Calendar.NOVEMBER, 17, 0, 30), duringShift)

        val endOfShift = utcCal(2026, Calendar.NOVEMBER, 17, 7, 45)
        val afterShift = ReminderScheduleCalculator.computeNextReminderTriggerMillis(
            nowMillis = endOfShift,
            intervalMinutes = 60,
            wakeHour = 18,
            wakeMinute = 0,
            bedHour = 8,
            bedMinute = 0,
            timeZone = utc
        )
        assertEquals(utcCal(2026, Calendar.NOVEMBER, 17, 18, 0), afterShift)
    }

    @Test
    fun `scheduler preserves active countdown across app reopen and switches modes cleanly`() {
        val shadowAlarm = Shadows.shadowOf(alarmManager)
        val prefs = UserPreferences(
            onboardingCompleted = true,
            remindersEnabled = true,
            ringingReminderEnabled = false,
            reminderIntervalMinutes = 45,
            wakeHour = 0,
            wakeMinute = 0,
            bedHour = 0,
            bedMinute = 0 // 24h test window so test never fails based on local clock
        )

        ReminderScheduler.syncReminders(context, prefs, forceReschedule = true)
        val initialTrigger = ReminderScheduler.getNextScheduledReminderMillis(context)
        assertNotNull(initialTrigger)
        assertEquals(1, shadowAlarm.scheduledAlarms.size)
        assertNotNull(alarmManager.nextAlarmClock)
        assertEquals(initialTrigger, alarmManager.nextAlarmClock?.triggerTime)

        // Simulating app reopen (forceReschedule = false) keeps the exact same trigger time
        ReminderScheduler.syncReminders(context, prefs, forceReschedule = false)
        assertEquals(initialTrigger, ReminderScheduler.getNextScheduledReminderMillis(context))

        // Enabling Ringing Alarm Mode keeps the same trigger time and AlarmClockInfo
        val ringingPrefs = prefs.copy(ringingReminderEnabled = true)
        ReminderScheduler.syncReminders(context, ringingPrefs, forceReschedule = false)
        assertEquals(initialTrigger, ReminderScheduler.getNextScheduledReminderMillis(context))
        val alarmClock = alarmManager.nextAlarmClock
        assertNotNull(alarmClock)
        assertEquals(initialTrigger, alarmClock?.triggerTime)

        // Changing interval updates the scheduled trigger to the new interval (~15m)
        val beforeChange = System.currentTimeMillis()
        val updatedIntervalPrefs = ringingPrefs.copy(reminderIntervalMinutes = 15)
        ReminderScheduler.syncReminders(context, updatedIntervalPrefs, forceReschedule = true)
        val updatedTrigger = ReminderScheduler.getNextScheduledReminderMillis(context)!!
        val deltaMinutes = (updatedTrigger - beforeChange) / 60_000.0
        assertTrue("Expected ~15m interval, got $deltaMinutes min", deltaMinutes in 14.5..15.5)

        // Disabling reminders cancels scheduled alarms
        ReminderScheduler.syncReminders(context, updatedIntervalPrefs.copy(remindersEnabled = false))
        assertNull(ReminderScheduler.getNextScheduledReminderMillis(context))
        assertTrue(shadowAlarm.scheduledAlarms.isEmpty())
    }

    @Test
    fun `scheduleNextAfterTrigger advances by exact interval and snooze arms separate alarm`() {
        val shadowAlarm = Shadows.shadowOf(alarmManager)
        val prefs = UserPreferences(
            onboardingCompleted = true,
            remindersEnabled = true,
            ringingReminderEnabled = true,
            reminderIntervalMinutes = 30,
            wakeHour = 0,
            wakeMinute = 0,
            bedHour = 0,
            bedMinute = 0
        )

        ReminderScheduler.syncReminders(context, prefs, forceReschedule = true)
        val firstTrigger = ReminderScheduler.getNextScheduledReminderMillis(context)!!
        val nextTrigger = ReminderScheduler.scheduleNextAfterTrigger(context, prefs, nowMillis = firstTrigger + 1_000L)
        assertEquals(firstTrigger + 30 * 60_000L, nextTrigger)

        ReminderScheduler.scheduleSnoozeReminder(context, snoozeMinutes = 10, ringingMode = true)
        assertEquals(2, shadowAlarm.scheduledAlarms.size)

        ReminderScheduler.cancelSnoozeReminder(context)
        assertEquals(1, shadowAlarm.scheduledAlarms.size)
    }

    @Test
    fun `claimDueReminderAndScheduleNext deduplicates rapid triggers and advances by exact interval`() {
        val prefs = UserPreferences(
            onboardingCompleted = true,
            remindersEnabled = true,
            ringingReminderEnabled = false,
            reminderIntervalMinutes = 15,
            wakeHour = 0,
            wakeMinute = 0,
            bedHour = 0,
            bedMinute = 0
        )

        ReminderScheduler.syncReminders(context, prefs, forceReschedule = true)
        val dueSlot = ReminderScheduler.getNextScheduledReminderMillis(context)!!

        // First claim at the due time succeeds and advances next slot by 15 minutes
        val firstClaimed = ReminderScheduler.claimDueReminderAndScheduleNext(context, nowMillis = dueSlot)
        assertTrue(firstClaimed)
        assertEquals(dueSlot + 15 * 60_000L, ReminderScheduler.getNextScheduledReminderMillis(context))

        // Duplicate trigger 500ms later (e.g. both in-process timer and AlarmManager firing) is ignored
        val duplicateClaimed = ReminderScheduler.claimDueReminderAndScheduleNext(context, nowMillis = dueSlot + 500L)
        assertFalse(duplicateClaimed)
        assertEquals(dueSlot + 15 * 60_000L, ReminderScheduler.getNextScheduledReminderMillis(context))
    }

    @Test
    fun `deliverReminderIfEligible continues delivering notifications even after daily goal is reached`() = runBlocking {
        val app = context as HydrateBuddyApplication
        Shadows.shadowOf(app as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val repo = app.repository
        repo.clearAllData()
        repo.updatePreferences { current ->
            current.copy(
                onboardingCompleted = true,
                remindersEnabled = true,
                ringingReminderEnabled = false,
                dailyGoalMl = 1000,
                wakeHour = 0,
                wakeMinute = 0,
                bedHour = 0,
                bedMinute = 0
            )
        }
        repo.addWaterEntry(amountMl = 1200, drinkType = DrinkType.WATER) // Exceeds dailyGoalMl (1000ml)

        NotificationHelper.ensureChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        HydrationNotificationReceiver.deliverReminderIfEligible(
            context = context,
            repository = repo,
            isSnooze = false
        )

        val activeNotifications = Shadows.shadowOf(notificationManager).allNotifications
        assertEquals(1, activeNotifications.size)
    }

    @Test
    fun `scheduler falls back cleanly when exact alarm permission is not granted`() {
        val shadowAlarm = Shadows.shadowOf(alarmManager)
        ReminderScheduler.exactAlarmOverrideForTesting = false
        val prefs = UserPreferences(
            onboardingCompleted = true,
            remindersEnabled = true,
            ringingReminderEnabled = true,
            reminderIntervalMinutes = 60,
            wakeHour = 0,
            wakeMinute = 0,
            bedHour = 0,
            bedMinute = 0
        )

        ReminderScheduler.syncReminders(context, prefs, forceReschedule = true)
        assertNotNull(ReminderScheduler.getNextScheduledReminderMillis(context))
        assertEquals(1, shadowAlarm.scheduledAlarms.size)
        assertNull(alarmManager.nextAlarmClock)
    }

    @Test
    fun `alarm notification configures fullScreenIntent and public alarm metadata to wake screen`() {
        val notification = NotificationHelper.buildAlarmNotification(
            context = context,
            userName = "Alex",
            totalMl = 500,
            goalMl = 2000,
            unit = MeasurementUnit.ML,
            useSilentAudioChannel = true
        )

        assertNotNull(notification.fullScreenIntent)
        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals(NotificationHelper.ALARM_SILENT_RING_CHANNEL_ID, notification.channelId)
    }
}

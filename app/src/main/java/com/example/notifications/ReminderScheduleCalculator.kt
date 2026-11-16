package com.example.notifications

import com.example.utils.DateUtils
import java.util.Calendar
import java.util.TimeZone

object ReminderScheduleCalculator {
    const val MIN_INTERVAL_MINUTES = 15
    private const val MIN_FUTURE_LEAD_MILLIS = 5_000L

    fun computeNextReminderTriggerMillis(
        nowMillis: Long,
        intervalMinutes: Int,
        wakeHour: Int,
        wakeMinute: Int,
        bedHour: Int,
        bedMinute: Int,
        previousTriggerAtMillis: Long? = null,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val safeIntervalMinutes = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        val intervalMillis = safeIntervalMinutes * 60_000L

        var candidate = if (previousTriggerAtMillis != null && previousTriggerAtMillis > 0L) {
            val nextFromPrev = previousTriggerAtMillis + intervalMillis
            if (nowMillis - previousTriggerAtMillis > intervalMillis * 2L) {
                // Large gap (device powered off or long Doze) -> anchor cleanly from now
                nowMillis + intervalMillis
            } else {
                var stepped = nextFromPrev
                while (stepped <= nowMillis + MIN_FUTURE_LEAD_MILLIS) {
                    stepped += intervalMillis
                }
                stepped
            }
        } else {
            nowMillis + intervalMillis
        }

        val wakeTotalMinutes = wakeHour * 60 + wakeMinute
        val bedTotalMinutes = bedHour * 60 + bedMinute
        if (wakeTotalMinutes == bedTotalMinutes) {
            return candidate
        }

        if (isWithinWakingHours(candidate, wakeHour, wakeMinute, bedHour, bedMinute, timeZone)) {
            return candidate
        }

        return nextWakeTimeMillis(nowMillis, wakeHour, wakeMinute, timeZone)
    }

    fun computeSnoozeTriggerMillis(nowMillis: Long, snoozeMinutes: Int): Long {
        return nowMillis + snoozeMinutes.coerceAtLeast(1) * 60_000L
    }

    fun isWithinWakingHours(
        timestampMillis: Long,
        wakeHour: Int,
        wakeMinute: Int,
        bedHour: Int,
        bedMinute: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = timestampMillis }
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val wakeMinutes = wakeHour * 60 + wakeMinute
        val bedMinutes = bedHour * 60 + bedMinute

        return if (wakeMinutes <= bedMinutes) {
            currentMinutes in wakeMinutes..bedMinutes
        } else {
            currentMinutes >= wakeMinutes || currentMinutes <= bedMinutes
        }
    }

    fun nextWakeTimeMillis(
        afterMillis: Long,
        wakeHour: Int,
        wakeMinute: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = afterMillis
            set(Calendar.HOUR_OF_DAY, wakeHour)
            set(Calendar.MINUTE, wakeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= afterMillis + MIN_FUTURE_LEAD_MILLIS) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun formatScheduleSignature(
        intervalMinutes: Int,
        wakeHour: Int,
        wakeMinute: Int,
        bedHour: Int,
        bedMinute: Int
    ): String {
        val safeInterval = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        return "$safeInterval|$wakeHour:$wakeMinute|$bedHour:$bedMinute"
    }

    fun formatTriggerTimeShort(timestampMillis: Long): String {
        return DateUtils.formatTime(timestampMillis)
    }
}

package com.example.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private const val ISO_PATTERN = "yyyy-MM-dd"

    fun todayIso(nowMillis: Long = System.currentTimeMillis()): String {
        return isoFromTimestamp(nowMillis)
    }

    fun isoFromTimestamp(timestampMillis: Long): String {
        val formatter = SimpleDateFormat(ISO_PATTERN, Locale.US)
        return formatter.format(Date(timestampMillis))
    }

    fun dateIsoOffset(baseIso: String, daysDelta: Int): String {
        val cal = parseIsoToCalendar(baseIso)
        cal.add(Calendar.DAY_OF_YEAR, daysDelta)
        return calendarToIso(cal)
    }

    fun parseIsoToCalendar(dateIso: String): Calendar {
        val cal = Calendar.getInstance()
        val parts = dateIso.split("-")
        if (parts.size == 3) {
            val year = parts[0].toIntOrNull() ?: cal.get(Calendar.YEAR)
            val month = (parts[1].toIntOrNull() ?: 1) - 1
            val day = parts[2].toIntOrNull() ?: 1
            cal.set(year, month, day, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        return cal
    }

    fun calendarToIso(calendar: Calendar): String {
        val formatter = SimpleDateFormat(ISO_PATTERN, Locale.US)
        return formatter.format(calendar.time)
    }

    fun formatDisplayDate(dateIso: String, nowMillis: Long = System.currentTimeMillis()): String {
        val today = todayIso(nowMillis)
        val yesterday = dateIsoOffset(today, -1)
        return when (dateIso) {
            today -> {
                val cal = parseIsoToCalendar(dateIso)
                "Today, " + SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
            }
            yesterday -> {
                val cal = parseIsoToCalendar(dateIso)
                "Yesterday, " + SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
            }
            else -> {
                val cal = parseIsoToCalendar(dateIso)
                SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(cal.time)
            }
        }
    }

    fun dayOfWeekShort(dateIso: String): String {
        val cal = parseIsoToCalendar(dateIso)
        return SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
    }

    fun formatTime(timestampMillis: Long): String {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMillis))
    }

    fun formatHourMinute(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
    }

    fun getLastNDaysIso(n: Int, nowMillis: Long = System.currentTimeMillis()): List<String> {
        val today = todayIso(nowMillis)
        return ((n - 1) downTo 0).map { offset -> dateIsoOffset(today, -offset) }
    }

    fun getMonthRangeIso(nowMillis: Long = System.currentTimeMillis()): Pair<String, List<String>> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val monthTitle = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val days = (1..maxDays).map { day ->
            val dayCal = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendarToIso(dayCal)
        }
        return monthTitle to days
    }

    fun greetingForHour(userName: String, nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }
        val trimmed = userName.trim()
        return if (trimmed.isNotEmpty()) "$timeGreeting, $trimmed!" else "$timeGreeting!"
    }

    fun isWithinWakingHours(
        nowMillis: Long = System.currentTimeMillis(),
        wakeHour: Int,
        wakeMinute: Int,
        bedHour: Int,
        bedMinute: Int
    ): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val wakeMinutes = wakeHour * 60 + wakeMinute
        val bedMinutes = bedHour * 60 + bedMinute

        return if (wakeMinutes <= bedMinutes) {
            currentMinutes in wakeMinutes..bedMinutes
        } else {
            // Handles late-night schedules (e.g. wake 9:00 AM, bed 1:00 AM)
            currentMinutes >= wakeMinutes || currentMinutes <= bedMinutes
        }
    }

    fun getWakingFractionElapsed(
        nowMillis: Long = System.currentTimeMillis(),
        wakeHour: Int,
        wakeMinute: Int,
        bedHour: Int,
        bedMinute: Int
    ): Float {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val wakeMinutes = wakeHour * 60 + wakeMinute
        val bedMinutes = bedHour * 60 + bedMinute

        val totalWaking = if (bedMinutes > wakeMinutes) {
            (bedMinutes - wakeMinutes).coerceAtLeast(60)
        } else {
            ((24 * 60 - wakeMinutes) + bedMinutes).coerceAtLeast(60)
        }

        val elapsed = if (bedMinutes >= wakeMinutes) {
            (currentMinutes - wakeMinutes).coerceIn(0, totalWaking)
        } else {
            if (currentMinutes >= wakeMinutes) {
                currentMinutes - wakeMinutes
            } else {
                ((24 * 60 - wakeMinutes) + currentMinutes).coerceIn(0, totalWaking)
            }
        }
        return (elapsed.toFloat() / totalWaking.toFloat()).coerceIn(0f, 1f)
    }

    fun isWithinMinutesAfterWake(
        timestampMillis: Long,
        wakeHour: Int,
        wakeMinute: Int,
        windowMinutes: Int = 60
    ): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val logMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val wakeMinutes = wakeHour * 60 + wakeMinute
        val diff = logMinutes - wakeMinutes
        return diff in 0..windowMinutes
    }
}

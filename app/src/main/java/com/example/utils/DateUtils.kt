package com.example.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private const val ISO_PATTERN = "yyyy-MM-dd"

    private val isoFormat = ThreadLocal.withInitial { SimpleDateFormat(ISO_PATTERN, Locale.US) }
    private val displayMonthDayFormat = ThreadLocal.withInitial { SimpleDateFormat("MMM d", Locale.getDefault()) }
    private val displayFullDateFormat = ThreadLocal.withInitial { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    private val dayOfWeekShortFormat = ThreadLocal.withInitial { SimpleDateFormat("EEE", Locale.getDefault()) }
    private val monthYearFormat = ThreadLocal.withInitial { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    private val sharedCalendar = ThreadLocal.withInitial { Calendar.getInstance() }
    private val sharedDate = ThreadLocal.withInitial { Date() }

    private fun getCal(): Calendar = sharedCalendar.get() ?: Calendar.getInstance()

    private fun getDate(millis: Long): Date = (sharedDate.get() ?: Date()).apply { time = millis }

    fun todayIso(nowMillis: Long = System.currentTimeMillis()): String {
        return isoFromTimestamp(nowMillis)
    }

    fun isoFromTimestamp(timestampMillis: Long): String {
        val fmt = isoFormat.get() ?: SimpleDateFormat(ISO_PATTERN, Locale.US)
        return fmt.format(getDate(timestampMillis))
    }

    fun millisUntilNextMidnight(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (cal.timeInMillis - nowMillis).coerceAtLeast(1L)
    }

    fun millisUntilNextMinute(nowMillis: Long = System.currentTimeMillis()): Long {
        val remainder = nowMillis % 60_000L
        return (60_000L - remainder).coerceAtLeast(1L)
    }

    fun dateIsoOffset(baseIso: String, daysDelta: Int): String {
        val cal = parseIsoToCalendar(baseIso)
        cal.add(Calendar.DAY_OF_YEAR, daysDelta)
        return calendarToIso(cal)
    }

    fun parseIsoToCalendar(dateIso: String): Calendar {
        val cal = Calendar.getInstance()
        setCalendarFromIso(cal, dateIso)
        return cal
    }

    private fun setCalendarFromIso(cal: Calendar, dateIso: String) {
        if (dateIso.length == 10 && dateIso[4] == '-' && dateIso[7] == '-') {
            val year = dateIso.substring(0, 4).toIntOrNull()
            val month = dateIso.substring(5, 7).toIntOrNull()
            val day = dateIso.substring(8, 10).toIntOrNull()
            if (year != null && month != null && day != null) {
                cal.set(year, month - 1, day, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return
            }
        }
        val parts = dateIso.split("-")
        if (parts.size == 3) {
            val year = parts[0].toIntOrNull() ?: cal.get(Calendar.YEAR)
            val month = (parts[1].toIntOrNull() ?: 1) - 1
            val day = parts[2].toIntOrNull() ?: 1
            cal.set(year, month, day, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
    }

    fun calendarToIso(calendar: Calendar): String {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return buildString(10) {
            append(year)
            append('-')
            if (month < 10) append('0')
            append(month)
            append('-')
            if (day < 10) append('0')
            append(day)
        }
    }

    fun formatMonthYear(calendar: Calendar): String {
        val fmt = monthYearFormat.get() ?: SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return fmt.format(calendar.time)
    }

    fun formatDisplayDate(dateIso: String, nowMillis: Long = System.currentTimeMillis()): String {
        val today = todayIso(nowMillis)
        val yesterday = dateIsoOffset(today, -1)
        val cal = getCal()
        setCalendarFromIso(cal, dateIso)
        return when (dateIso) {
            today -> {
                val fmt = displayMonthDayFormat.get() ?: SimpleDateFormat("MMM d", Locale.getDefault())
                "Today, " + fmt.format(cal.time)
            }
            yesterday -> {
                val fmt = displayMonthDayFormat.get() ?: SimpleDateFormat("MMM d", Locale.getDefault())
                "Yesterday, " + fmt.format(cal.time)
            }
            else -> {
                val fmt = displayFullDateFormat.get() ?: SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                fmt.format(cal.time)
            }
        }
    }

    fun dayOfWeekShort(dateIso: String): String {
        val cal = getCal()
        setCalendarFromIso(cal, dateIso)
        val fmt = dayOfWeekShortFormat.get() ?: SimpleDateFormat("EEE", Locale.getDefault())
        return fmt.format(cal.time)
    }

    fun formatTime(timestampMillis: Long): String {
        val fmt = timeFormat.get() ?: SimpleDateFormat("h:mm a", Locale.getDefault())
        return fmt.format(getDate(timestampMillis))
    }

    fun formatHourMinute(hour: Int, minute: Int): String {
        val cal = getCal()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        val fmt = timeFormat.get() ?: SimpleDateFormat("h:mm a", Locale.getDefault())
        return fmt.format(cal.time)
    }

    fun getLastNDaysIso(n: Int, nowMillis: Long = System.currentTimeMillis()): List<String> {
        val today = todayIso(nowMillis)
        return ((n - 1) downTo 0).map { offset -> dateIsoOffset(today, -offset) }
    }

    fun getMonthRangeIso(nowMillis: Long = System.currentTimeMillis()): Pair<String, List<String>> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val monthTitle = formatMonthYear(cal)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val dayCal = Calendar.getInstance()
        val days = (1..maxDays).map { day ->
            dayCal.set(year, month, day, 0, 0, 0)
            dayCal.set(Calendar.MILLISECOND, 0)
            calendarToIso(dayCal)
        }
        return monthTitle to days
    }

    fun greetingForHour(userName: String, nowMillis: Long = System.currentTimeMillis()): String {
        val cal = getCal().apply { timeInMillis = nowMillis }
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
        val cal = getCal().apply { timeInMillis = nowMillis }
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val wakeMinutes = wakeHour * 60 + wakeMinute
        val bedMinutes = bedHour * 60 + bedMinute

        return if (wakeMinutes <= bedMinutes) {
            currentMinutes in wakeMinutes..bedMinutes
        } else {
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
        val cal = getCal().apply { timeInMillis = nowMillis }
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
        val cal = getCal().apply { timeInMillis = timestampMillis }
        val logMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val wakeMinutes = wakeHour * 60 + wakeMinute
        val diff = logMinutes - wakeMinutes
        return diff in 0..windowMinutes
    }
}

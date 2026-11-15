package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.DailyProgressEntity
import com.example.database.WaterEntryEntity
import com.example.model.DrinkType
import com.example.model.HistoryPeriod
import com.example.model.HistoryUiState
import com.example.model.MonthlyHeatmapDay
import com.example.model.UserPreferences
import com.example.model.WeeklyBarPoint
import com.example.repository.HydrationRepository
import com.example.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: HydrationRepository
) : ViewModel() {

    private val selectedPeriodFlow = MutableStateFlow(HistoryPeriod.WEEK)
    private val todayIsoFlow: StateFlow<String> = repository.currentDateIsoFlow
    private val selectedDateIsoFlow = MutableStateFlow(todayIsoFlow.value)
    private var lastObservedTodayIso: String = todayIsoFlow.value

    init {
        viewModelScope.launch {
            repository.currentDateIsoFlow.collect { latestToday ->
                if (latestToday != lastObservedTodayIso) {
                    val wasViewingToday = selectedDateIsoFlow.value == lastObservedTodayIso
                    lastObservedTodayIso = latestToday
                    if (wasViewingToday) {
                        selectedDateIsoFlow.value = latestToday
                    }
                }
            }
        }
    }

    private val selectedDayEntriesFlow = selectedDateIsoFlow.flatMapLatest { dateIso ->
        repository.observeEntriesForDate(dateIso)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        selectedPeriodFlow,
        selectedDateIsoFlow,
        selectedDayEntriesFlow,
        repository.allProgressFlow,
        repository.preferencesFlow,
        todayIsoFlow
    ) { values ->
        val period = values[0] as HistoryPeriod
        val selectedDateIso = values[1] as String
        @Suppress("UNCHECKED_CAST")
        val dayEntries = values[2] as List<WaterEntryEntity>
        @Suppress("UNCHECKED_CAST")
        val allProgress = values[3] as List<DailyProgressEntity>
        val prefs = values[4] as UserPreferences
        val todayIso = values[5] as String
        buildHistoryState(period, selectedDateIso, dayEntries, allProgress, prefs, todayIso)
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState.Loading
        )

    private fun buildHistoryState(
        period: HistoryPeriod,
        selectedDateIso: String,
        dayEntries: List<WaterEntryEntity>,
        allProgress: List<DailyProgressEntity>,
        prefs: UserPreferences,
        todayIso: String = DateUtils.todayIso()
    ): HistoryUiState.Success {
        val progressByDate = allProgress.associateBy { it.dateIso }
        val defaultGoal = prefs.dailyGoalMl.coerceAtLeast(1)

        val selectedDayTotal = dayEntries.sumOf { it.amountMl }
        val selectedDayGoal = progressByDate[selectedDateIso]?.goalMl ?: defaultGoal
        val selectedDayPct = ((selectedDayTotal.toFloat() / selectedDayGoal.coerceAtLeast(1)) * 100f)
            .toInt()
            .coerceIn(0, 999)

        // Anchor 7-day window to the week ending on selectedDateIso (or today if within last 6 days)
        val anchorEndIso = computeWeekAnchorEnd(selectedDateIso, todayIso)
        val weekIsoDates = (6 downTo 0).map { offset -> DateUtils.dateIsoOffset(anchorEndIso, -offset) }
        val weeklyPoints = weekIsoDates.map { iso ->
            val rec = progressByDate[iso]
            val total = if (iso == selectedDateIso) selectedDayTotal else (rec?.totalMl ?: 0)
            val goal = rec?.goalMl ?: defaultGoal
            val pct = ((total.toFloat() / goal.coerceAtLeast(1)) * 100f).toInt()
            WeeklyBarPoint(
                dateIso = iso,
                dayLabel = DateUtils.dayOfWeekShort(iso),
                totalMl = total,
                goalMl = goal,
                completionPercent = pct
            )
        }
        val weeklyTotal = weeklyPoints.sumOf { it.totalMl }
        val weeklyAvg = weeklyTotal / 7

        // Anchor month heatmap to selectedDateIso's month
        val selectedCal = DateUtils.parseIsoToCalendar(selectedDateIso)
        val monthTitle = DateUtils.formatMonthYear(selectedCal)
        val maxDays = selectedCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val year = selectedCal.get(Calendar.YEAR)
        val month = selectedCal.get(Calendar.MONTH)
        val dayCal = Calendar.getInstance()

        val monthlyDays = (1..maxDays).map { day ->
            dayCal.set(year, month, day, 0, 0, 0)
            dayCal.set(Calendar.MILLISECOND, 0)
            val iso = DateUtils.calendarToIso(dayCal)
            val rec = progressByDate[iso]
            val total = if (iso == selectedDateIso) selectedDayTotal else (rec?.totalMl ?: 0)
            val goal = rec?.goalMl ?: defaultGoal
            val isFuture = iso > todayIso
            MonthlyHeatmapDay(
                dateIso = iso,
                dayOfMonth = day,
                totalMl = total,
                goalMl = goal,
                completionFraction = (total.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1.5f),
                isFuture = isFuture
            )
        }

        val elapsedMonthDays = monthlyDays.filter { !it.isFuture }
        val activeMonthDays = elapsedMonthDays.count { it.totalMl > 0 }
        val monthlyTotal = elapsedMonthDays.sumOf { it.totalMl }
        val monthlyAvg = if (activeMonthDays > 0) monthlyTotal / activeMonthDays else 0
        val monthlyGoalsMet = elapsedMonthDays.count { it.totalMl >= it.goalMl && it.totalMl > 0 }

        return HistoryUiState.Success(
            selectedPeriod = period,
            selectedDateIso = selectedDateIso,
            todayIso = todayIso,
            selectedDateLabel = DateUtils.formatDisplayDate(selectedDateIso),
            isToday = selectedDateIso == todayIso,
            unit = prefs.unit,
            dailyGoalMl = defaultGoal,
            selectedDayTotalMl = selectedDayTotal,
            selectedDayGoalMl = selectedDayGoal,
            selectedDayPercent = selectedDayPct,
            selectedDayEntries = dayEntries,
            weeklyPoints = weeklyPoints,
            weeklyTotalMl = weeklyTotal,
            weeklyAverageMl = weeklyAvg,
            monthlyDays = monthlyDays,
            monthTitle = monthTitle,
            monthlyTotalMl = monthlyTotal,
            monthlyAverageMl = monthlyAvg,
            monthlyGoalsMetCount = monthlyGoalsMet,
            allProgressRecords = allProgress
        )
    }

    private fun computeWeekAnchorEnd(selectedDateIso: String, todayIso: String): String {
        val sixDaysAgo = DateUtils.dateIsoOffset(todayIso, -6)
        return if (selectedDateIso >= sixDaysAgo) todayIso else selectedDateIso
    }

    fun refreshTodayIfNeeded(nowMillis: Long = System.currentTimeMillis()) {
        repository.refreshCurrentDate(nowMillis)
    }

    fun selectPeriod(period: HistoryPeriod) {
        refreshTodayIfNeeded()
        selectedPeriodFlow.value = period
    }

    fun selectDate(dateIso: String) {
        val today = repository.refreshCurrentDate()
        selectedDateIsoFlow.value = if (dateIso > today) today else dateIso
    }

    fun stepDay(daysDelta: Int) {
        selectDate(DateUtils.dateIsoOffset(selectedDateIsoFlow.value, daysDelta))
    }

    fun stepWeek(weeksDelta: Int) {
        selectDate(DateUtils.dateIsoOffset(selectedDateIsoFlow.value, weeksDelta * 7))
    }

    fun stepMonth(monthsDelta: Int) {
        val cal = DateUtils.parseIsoToCalendar(selectedDateIsoFlow.value)
        cal.add(Calendar.MONTH, monthsDelta)
        selectDate(DateUtils.calendarToIso(cal))
    }

    fun addEntryForSelectedDate(amountMl: Int, drinkType: DrinkType) {
        val dateIso = selectedDateIsoFlow.value
        viewModelScope.launch {
            repository.addWaterEntryForDate(dateIso = dateIso, amountMl = amountMl, drinkType = drinkType)
        }
    }

    fun updateEntry(entry: WaterEntryEntity) {
        viewModelScope.launch {
            repository.updateWaterEntry(entry)
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            repository.deleteWaterEntry(entryId)
        }
    }

    fun restoreEntry(entry: WaterEntryEntity) {
        viewModelScope.launch {
            repository.restoreWaterEntry(entry)
        }
    }
}

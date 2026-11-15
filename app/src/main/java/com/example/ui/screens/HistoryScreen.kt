package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.database.WaterEntryEntity
import com.example.model.HistoryPeriod
import com.example.model.HistoryUiState
import com.example.model.MeasurementUnit
import com.example.model.MonthlyHeatmapDay
import com.example.model.WeeklyBarPoint
import com.example.ui.components.CustomWaterDialog
import com.example.ui.components.EditWaterEntryDialog
import com.example.ui.theme.LocalHydrateColors
import com.example.utils.DateUtils
import com.example.utils.UnitConverter
import com.example.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.home_undo_action)
    var editingEntry by remember { mutableStateOf<WaterEntryEntity?>(null) }
    var showAddForDateDialog by remember { mutableStateOf(false) }

    when (val state = uiState) {
        is HistoryUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is HistoryUiState.Success -> {
            val currentUnit by rememberUpdatedState(state.unit)
            val deleteWithUndo: (WaterEntryEntity) -> Unit = remember(viewModel, scope, snackbarHostState, undoLabel) {
                { entry ->
                    val unitSnapshot = currentUnit
                    viewModel.deleteEntry(entry.id)
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = snackbarHostState.showSnackbar(
                            message = "Removed ${UnitConverter.formatVolume(entry.amountMl, unitSnapshot)}",
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restoreEntry(entry)
                        }
                    }
                }
            }

            if (showAddForDateDialog) {
                CustomWaterDialog(
                    unit = state.unit,
                    title = "Log for ${state.selectedDateLabel}",
                    onDismiss = { showAddForDateDialog = false },
                    onConfirm = { ml, drinkType ->
                        viewModel.addEntryForSelectedDate(ml, drinkType)
                        showAddForDateDialog = false
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                "Added ${UnitConverter.formatVolume(ml, state.unit)} to ${state.selectedDateLabel}"
                            )
                        }
                    }
                )
            }

            editingEntry?.let { entry ->
                EditWaterEntryDialog(
                    entry = entry,
                    unit = state.unit,
                    onDismiss = { editingEntry = null },
                    onSave = { updated ->
                        viewModel.updateEntry(updated)
                        editingEntry = null
                    },
                    onDelete = { _ ->
                        editingEntry = null
                        deleteWithUndo(entry)
                    }
                )
            }

            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .testTag("history_list"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "history_title", contentType = "header") {
                        Text(
                            text = "Hydration History",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.testTag("history_title")
                        )
                    }

                    // Period Selector
                    item(key = "history_period", contentType = "period_selector") {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            HistoryPeriod.entries.forEachIndexed { index, period ->
                                SegmentedButton(
                                    selected = state.selectedPeriod == period,
                                    onClick = { viewModel.selectPeriod(period) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index,
                                        HistoryPeriod.entries.size
                                    ),
                                    modifier = Modifier.testTag("history_tab_${period.name.lowercase()}")
                                ) {
                                    Text(
                                        text = period.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Date selector bar
                    item(key = "history_date_stepper", contentType = "date_stepper") {
                        val onPrev = remember(viewModel) { { viewModel.stepDate(-1) } }
                        val onNext = remember(viewModel) { { viewModel.stepDate(1) } }
                        val onJumpToday = remember(viewModel) { { viewModel.selectDate(DateUtils.todayIso()) } }
                        DateStepperCard(
                            dateLabel = state.selectedDateLabel,
                            isToday = state.isToday,
                            onPrev = onPrev,
                            onNext = onNext,
                            onJumpToday = onJumpToday
                        )
                    }

                    // Period Visualization
                    when (state.selectedPeriod) {
                        HistoryPeriod.DAY -> {
                            item(key = "history_day_summary", contentType = "day_summary") {
                                DaySummaryCard(
                                    totalMl = state.selectedDayTotalMl,
                                    goalMl = state.selectedDayGoalMl,
                                    percent = state.selectedDayPercent,
                                    unit = state.unit
                                )
                            }
                        }
                        HistoryPeriod.WEEK -> {
                            item(key = "history_weekly_chart", contentType = "weekly_chart") {
                                WeeklyChartCard(
                                    points = state.weeklyPoints,
                                    selectedDateIso = state.selectedDateIso,
                                    weeklyTotalMl = state.weeklyTotalMl,
                                    weeklyAvgMl = state.weeklyAverageMl,
                                    unit = state.unit,
                                    onSelectDate = viewModel::selectDate
                                )
                            }
                        }
                        HistoryPeriod.MONTH -> {
                            item(key = "history_monthly_overview", contentType = "monthly_overview") {
                                MonthlyOverviewCard(
                                    monthTitle = state.monthTitle,
                                    days = state.monthlyDays,
                                    selectedDateIso = state.selectedDateIso,
                                    monthlyTotalMl = state.monthlyTotalMl,
                                    monthlyAverageMl = state.monthlyAverageMl,
                                    goalsMetCount = state.monthlyGoalsMetCount,
                                    unit = state.unit,
                                    onSelectDate = viewModel::selectDate
                                )
                            }
                        }
                    }

                    // Selected day breakdown header + Log for Date action
                    item(key = "history_day_header", contentType = "day_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Logs for ${state.selectedDateLabel}",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "${UnitConverter.formatVolume(state.selectedDayTotalMl, state.unit)} (${state.selectedDayPercent}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = { showAddForDateDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("history_add_for_date_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Sip")
                            }
                        }
                    }

                    if (state.selectedDayEntries.isEmpty()) {
                        item(key = "history_empty_day", contentType = "empty_day") {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WaterDrop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No drinks logged for this date",
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Tap \"Add Sip\" above to log a missed drink, or pick another day.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = state.selectedDayEntries,
                            key = { it.id },
                            contentType = { "water_entry" }
                        ) { entry ->
                            WaterEntryRowCard(
                                entry = entry,
                                unit = state.unit,
                                onEditClick = { editingEntry = entry },
                                onDeleteClick = { deleteWithUndo(entry) }
                            )
                        }
                    }

                    item(key = "history_bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DateStepperCard(
    dateLabel: String,
    isToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpToday: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPrev,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("history_prev_day")
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!isToday) {
                    AssistChip(
                        onClick = onJumpToday,
                        label = { Text("Today") }
                    )
                }
            }

            IconButton(
                onClick = onNext,
                enabled = !isToday,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("history_next_day")
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
        }
    }
}

@Composable
private fun DaySummaryCard(
    totalMl: Int,
    goalMl: Int,
    percent: Int,
    unit: MeasurementUnit
) {
    val extendedColors = LocalHydrateColors.current
    val fraction = (totalMl.toFloat() / goalMl.coerceAtLeast(1)).coerceIn(0f, 1f)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Total",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = UnitConverter.formatVolume(totalMl, unit),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (percent >= 100) extendedColors.successContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$percent% of ${UnitConverter.formatVolume(goalMl, unit)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (percent >= 100) extendedColors.onSuccessContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.small),
                color = if (percent >= 100) extendedColors.success else MaterialTheme.colorScheme.primary,
                trackColor = extendedColors.waterRingTrack
            )
        }
    }
}

@Composable
private fun WeeklyChartCard(
    points: List<WeeklyBarPoint>,
    selectedDateIso: String,
    weeklyTotalMl: Int,
    weeklyAvgMl: Int,
    unit: MeasurementUnit,
    onSelectDate: (String) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val success = LocalHydrateColors.current.success
    val trackColor = LocalHydrateColors.current.waterRingTrack
    val outlineColor = MaterialTheme.colorScheme.outline

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("7-Day Hydration", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "Tap a bar to inspect",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val maxVal = remember(points) {
                maxOf(
                    points.maxOfOrNull { maxOf(it.totalMl, it.goalMl) } ?: 2000,
                    1000
                )
            }
            val goalVal = points.lastOrNull()?.goalMl ?: 2000
            val goalDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f) }
            val trackBgColor = remember(trackColor) { trackColor.copy(alpha = 0.45f) }
            val successBrush = remember(success, secondary) { Brush.verticalGradient(listOf(success, secondary)) }
            val selectedBrush = remember(primary, secondary) { Brush.verticalGradient(listOf(primary, secondary)) }
            val defaultBrush = remember(primary) {
                Brush.verticalGradient(listOf(primary.copy(alpha = 0.78f), primary.copy(alpha = 0.55f)))
            }

            // Overlay clickable columns over the Canvas so vertical scrolling is never intercepted
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val count = points.size.coerceAtLeast(1)
                    val slotWidth = size.width / count
                    val barWidth = slotWidth * 0.52f
                    val topPadPx = 6.dp.toPx()
                    val chartHeight = size.height - 12.dp.toPx()
                    val barRadiusPx = 10.dp.toPx()
                    val barRadius = CornerRadius(barRadiusPx, barRadiusPx)

                    val goalRatio = (goalVal.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                    val goalY = chartHeight * (1f - goalRatio) + topPadPx
                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, goalY),
                        end = Offset(size.width, goalY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = goalDashEffect
                    )

                    points.forEachIndexed { idx, point ->
                        val left = idx * slotWidth + (slotWidth - barWidth) / 2f

                        drawRoundRect(
                            color = trackBgColor,
                            topLeft = Offset(left, topPadPx),
                            size = Size(barWidth, chartHeight),
                            cornerRadius = barRadius
                        )

                        if (point.totalMl > 0) {
                            val hRatio = (point.totalMl.toFloat() / maxVal.toFloat()).coerceIn(0.05f, 1f)
                            val barH = chartHeight * hRatio
                            val top = topPadPx + (chartHeight - barH)
                            val isSelected = point.dateIso == selectedDateIso
                            val barBrush = when {
                                point.completionPercent >= 100 -> successBrush
                                isSelected -> selectedBrush
                                else -> defaultBrush
                            }
                            drawRoundRect(
                                brush = barBrush,
                                topLeft = Offset(left, top),
                                size = Size(barWidth, barH),
                                cornerRadius = barRadius
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    points.forEach { point ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onSelectDate(point.dateIso) }
                        )
                    }
                }
            }

            // Day labels row
            Row(modifier = Modifier.fillMaxWidth()) {
                points.forEach { point ->
                    val isSelected = point.dateIso == selectedDateIso
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelectDate(point.dateIso) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = point.dayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${point.completionPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatPillCard(
                    label = "7-Day Total",
                    value = UnitConverter.formatVolume(weeklyTotalMl, unit),
                    modifier = Modifier.weight(1f)
                )
                StatPillCard(
                    label = "Daily Average",
                    value = UnitConverter.formatVolume(weeklyAvgMl, unit),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MonthlyOverviewCard(
    monthTitle: String,
    days: List<MonthlyHeatmapDay>,
    selectedDateIso: String,
    monthlyTotalMl: Int,
    monthlyAverageMl: Int,
    goalsMetCount: Int,
    unit: MeasurementUnit,
    onSelectDate: (String) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val success = LocalHydrateColors.current.success
    val track = LocalHydrateColors.current.waterRingTrack
    val selectedBorderColor = MaterialTheme.colorScheme.tertiary
    val cellShape = MaterialTheme.shapes.small
    val futureCellColor = remember(track) { track.copy(alpha = 0.28f) }
    val emptyCellColor = remember(track) { track.copy(alpha = 0.55f) }
    val mediumCellColor = remember(primary) { primary.copy(alpha = 0.72f) }
    val lowCellColor = remember(primary) { primary.copy(alpha = 0.34f) }
    val futureTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val rows = remember(days) { days.chunked(7) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(monthTitle, style = MaterialTheme.typography.titleMedium)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 0 until 7) {
                            val day = week.getOrNull(i)
                            if (day == null) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val bgColor = when {
                                    day.isFuture -> futureCellColor
                                    day.completionFraction >= 1f -> success
                                    day.completionFraction >= 0.6f -> mediumCellColor
                                    day.completionFraction > 0f -> lowCellColor
                                    else -> emptyCellColor
                                }
                                val isSelected = day.dateIso == selectedDateIso
                                val textColor = when {
                                    day.isFuture -> futureTextColor
                                    day.completionFraction >= 0.6f -> Color.White
                                    else -> MaterialTheme.colorScheme.onSurface
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(cellShape)
                                        .background(bgColor)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.dp, selectedBorderColor, cellShape)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable(enabled = !day.isFuture) {
                                            onSelectDate(day.dateIso)
                                        }
                                ) {
                                    Text(
                                        text = day.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPillCard(
                    label = "Month Total",
                    value = UnitConverter.formatVolume(monthlyTotalMl, unit),
                    modifier = Modifier.weight(1f)
                )
                StatPillCard(
                    label = "Daily Avg",
                    value = UnitConverter.formatVolume(monthlyAverageMl, unit),
                    modifier = Modifier.weight(1f)
                )
                StatPillCard(
                    label = "Goals Met",
                    value = "$goalsMetCount d",
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatPillCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

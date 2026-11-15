package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.BuddyMood
import com.example.model.MeasurementUnit
import com.example.notifications.NotificationHelper
import com.example.ui.components.HydrateTimePickerDialog
import com.example.ui.components.WaterBuddyCanvas
import com.example.ui.components.pressBounce
import com.example.ui.theme.LocalHydrateColors
import com.example.utils.DateUtils
import com.example.utils.UnitConverter
import com.example.viewmodel.OnboardingViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onCompleted: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extendedColors = LocalHydrateColors.current

    var showWakePicker by remember { mutableStateOf(false) }
    var showBedPicker by remember { mutableStateOf(false) }
    var customGoalInput by remember(state.unit) {
        mutableStateOf(UnitConverter.formatNumericValue(state.dailyGoalMl, state.unit))
    }
    var goalError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.dailyGoalMl, state.unit) {
        val currentInputMl = customGoalInput.toFloatOrNull()?.let {
            UnitConverter.displayInputToMl(it, state.unit)
        }
        if (currentInputMl != state.dailyGoalMl) {
            customGoalInput = UnitConverter.formatNumericValue(state.dailyGoalMl, state.unit)
            goalError = null
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.completeOnboarding(onCompleted)
    }

    val startAction: () -> Unit = {
        val needsPermission = state.remindersEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasNotificationPermission(context)

        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.completeOnboarding(onCompleted)
        }
    }

    val intervals = remember { listOf(30, 60, 90, 120, 180) }
    val suggestedGoals = remember(state.unit) { UnitConverter.suggestedDailyGoalsMl(state.unit) }
    val wakeTimeLabel = remember(state.wakeHour, state.wakeMinute) {
        DateUtils.formatHourMinute(state.wakeHour, state.wakeMinute)
    }
    val bedTimeLabel = remember(state.bedHour, state.bedMinute) {
        DateUtils.formatHourMinute(state.bedHour, state.bedMinute)
    }
    val bgColor = MaterialTheme.colorScheme.background
    val bgBrush = remember(bgColor, extendedColors.cardGradientEnd) {
        Brush.verticalGradient(colors = listOf(bgColor, extendedColors.cardGradientEnd))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .testTag("onboarding_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "onboarding_hero", contentType = "hero") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WaterBuddyCanvas(
                        mood = BuddyMood.EXCITED,
                        size = 144.dp
                    )
                    Text(
                        text = stringResource(R.string.onboarding_headline),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.onboarding_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Name & Unit Card
            item(key = "onboarding_name_unit", contentType = "section_card") {
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
                        OutlinedTextField(
                            value = state.userName,
                            onValueChange = viewModel::setUserName,
                            label = { Text(stringResource(R.string.onboarding_name_label)) },
                            placeholder = { Text(stringResource(R.string.onboarding_name_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_name_input")
                        )

                        Text(
                            text = stringResource(R.string.onboarding_unit_label),
                            style = MaterialTheme.typography.titleSmall
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            MeasurementUnit.entries.forEachIndexed { index, unitOption ->
                                SegmentedButton(
                                    selected = state.unit == unitOption,
                                    onClick = {
                                        viewModel.setUnit(unitOption)
                                        val expectedGoal = if (unitOption == MeasurementUnit.OZ && state.dailyGoalMl == 2000) {
                                            1893
                                        } else if (unitOption == MeasurementUnit.ML && state.dailyGoalMl == 1893) {
                                            2000
                                        } else {
                                            state.dailyGoalMl
                                        }
                                        customGoalInput = UnitConverter.formatNumericValue(expectedGoal, unitOption)
                                        goalError = null
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index, MeasurementUnit.entries.size),
                                    modifier = Modifier.testTag("unit_option_${unitOption.name.lowercase()}")
                                ) {
                                    Text(
                                        text = unitOption.fullName,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Daily Goal Card
            item(key = "onboarding_goal", contentType = "section_card") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.onboarding_goal_label),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestedGoals.forEach { goalMl ->
                                val label = UnitConverter.formatVolume(goalMl, state.unit)
                                FilterChip(
                                    selected = state.dailyGoalMl == goalMl,
                                    onClick = {
                                        viewModel.setDailyGoalMl(goalMl)
                                        customGoalInput = UnitConverter.formatNumericValue(goalMl, state.unit)
                                        goalError = null
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = customGoalInput,
                            onValueChange = { text ->
                                val cleaned = text.filter { it.isDigit() }.take(4)
                                customGoalInput = cleaned
                                val value = cleaned.toFloatOrNull()
                                if (value == null || value <= 0f) {
                                    goalError = "Enter a daily target"
                                } else {
                                    val ml = UnitConverter.displayInputToMl(value, state.unit)
                                    if (ml < 400 || ml > 8000) {
                                        goalError = "Target should be between 400 and 8000 ml"
                                    } else {
                                        goalError = null
                                        viewModel.setDailyGoalMl(ml)
                                    }
                                }
                            },
                            label = { Text("Custom daily target (${state.unit.suffix})") },
                            isError = goalError != null,
                            supportingText = goalError?.let { err -> { Text(err) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_goal_input")
                        )

                        Text(
                            text = stringResource(R.string.onboarding_goal_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Waking hours & Reminders Card
            item(key = "onboarding_schedule", contentType = "section_card") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.onboarding_schedule_label),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Switch(
                                checked = state.remindersEnabled,
                                onCheckedChange = viewModel::setRemindersEnabled
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clickable { showWakePicker = true }
                                    .testTag("onboarding_wake_time_card")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.WbSunny,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.onboarding_wake_time),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = wakeTimeLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clickable { showBedPicker = true }
                                    .testTag("onboarding_bed_time_card")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Bedtime,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.onboarding_bed_time),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = bedTimeLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (state.remindersEnabled) {
                            Text(
                                text = stringResource(R.string.onboarding_interval_label),
                                style = MaterialTheme.typography.titleSmall
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                intervals.forEach { mins ->
                                    val label = if (mins >= 60 && mins % 60 == 0) {
                                        "Every ${mins / 60}h"
                                    } else {
                                        "Every ${mins}m"
                                    }
                                    FilterChip(
                                        selected = state.reminderIntervalMinutes == mins,
                                        onClick = { viewModel.setReminderInterval(mins) },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Ringing Alarm Mode (Optional)",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Ring like an alarm or timer until you log water or stop it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = state.ringingReminderEnabled,
                                    onCheckedChange = viewModel::setRingingReminderEnabled,
                                    modifier = Modifier.testTag("onboarding_ringing_switch")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sticky footer bar so Start Hydrating is always accessible regardless of scroll position
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.completeOnboarding(onCompleted)
                        },
                        enabled = !state.isSaving,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Skip")
                    }

                    val startInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = startAction,
                        enabled = !state.isSaving && goalError == null,
                        interactionSource = startInteraction,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .pressBounce(startInteraction, pressedScale = 0.97f)
                            .testTag("onboarding_start_button")
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_start_button),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }

    if (showWakePicker) {
        HydrateTimePickerDialog(
            title = "Wake-up Time",
            initialHour = state.wakeHour,
            initialMinute = state.wakeMinute,
            onDismiss = { showWakePicker = false },
            onConfirm = { h, m ->
                viewModel.setWakeTime(h, m)
                showWakePicker = false
            }
        )
    }

    if (showBedPicker) {
        HydrateTimePickerDialog(
            title = "Bedtime",
            initialHour = state.bedHour,
            initialMinute = state.bedMinute,
            onDismiss = { showBedPicker = false },
            onConfirm = { h, m ->
                viewModel.setBedTime(h, m)
                showBedPicker = false
            }
        )
    }
}

package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.database.AchievementEntity
import com.example.database.WaterEntryEntity
import com.example.model.BuddyMood
import com.example.model.DrinkType
import com.example.model.MeasurementUnit
import com.example.notifications.AlarmRingState
import com.example.utils.UnitConverter
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomWaterDialog(
    unit: MeasurementUnit,
    title: String = "Add Custom Drink",
    onDismiss: () -> Unit,
    onConfirm: (amountMl: Int, drinkType: DrinkType) -> Unit
) {
    val defaultValue = if (unit == MeasurementUnit.ML) "300" else "10"
    var amountText by remember { mutableStateOf(defaultValue) }
    var selectedType by remember { mutableStateOf(DrinkType.WATER) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val presets = if (unit == MeasurementUnit.ML) {
        listOf("200", "350", "500", "750")
    } else {
        listOf("6", "10", "12", "20")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        amountText = input.filter { it.isDigit() || it == '.' }.take(6)
                        errorText = null
                    },
                    label = { Text("Amount (${unit.suffix})") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_amount_input")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = amountText == preset,
                            onClick = {
                                amountText = preset
                                errorText = null
                            },
                            label = { Text("$preset ${unit.suffix}") }
                        )
                    }
                }

                Text(
                    text = "Drink type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DrinkType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.shortTag) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountText.toFloatOrNull()
                    if (parsed == null || parsed <= 0f) {
                        errorText = "Enter a positive amount"
                        return@Button
                    }
                    val ml = UnitConverter.displayInputToMl(parsed, unit)
                    if (ml < 10 || ml > 5000) {
                        errorText = if (unit == MeasurementUnit.ML) {
                            "Enter between 10 and 5000 ml"
                        } else {
                            "Enter between 1 and 169 oz"
                        }
                        return@Button
                    }
                    onConfirm(ml, selectedType)
                },
                modifier = Modifier.testTag("confirm_custom_water_button")
            ) {
                Text("Add Sip")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditWaterEntryDialog(
    entry: WaterEntryEntity,
    unit: MeasurementUnit,
    onDismiss: () -> Unit,
    onSave: (WaterEntryEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    val initialText = remember(entry, unit) {
        if (unit == MeasurementUnit.ML) {
            entry.amountMl.toString()
        } else {
            UnitConverter.mlToOz(entry.amountMl).roundToInt().toString()
        }
    }
    var amountText by remember { mutableStateOf(initialText) }
    var selectedType by remember { mutableStateOf(DrinkType.fromName(entry.drinkType)) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Water Log", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        amountText = input.filter { it.isDigit() || it == '.' }.take(6)
                        errorText = null
                    },
                    label = { Text("Amount (${unit.suffix})") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Drink type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DrinkType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.shortTag) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountText.toFloatOrNull()
                    if (parsed == null || parsed <= 0f) {
                        errorText = "Enter a valid amount"
                        return@Button
                    }
                    val ml = UnitConverter.displayInputToMl(parsed, unit)
                    if (ml < 10 || ml > 5000) {
                        errorText = "Amount out of valid range"
                        return@Button
                    }
                    onSave(entry.copy(amountMl = ml, drinkType = selectedType.name))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onDelete(entry.id) }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyGoalEditDialog(
    currentGoalMl: Int,
    unit: MeasurementUnit,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var goalInput by remember {
        mutableStateOf(UnitConverter.formatNumericValue(currentGoalMl, unit))
    }
    var goalError by remember { mutableStateOf<String?>(null) }
    val suggested = UnitConverter.suggestedDailyGoalsMl(unit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Hydration Target", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { input ->
                        goalInput = input.filter { it.isDigit() }.take(4)
                        goalError = null
                    },
                    label = { Text("Daily Target (${unit.suffix})") },
                    isError = goalError != null,
                    supportingText = goalError?.let { err -> { Text(err) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_goal_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggested.forEach { ml ->
                        FilterChip(
                            selected = goalInput == UnitConverter.formatNumericValue(ml, unit),
                            onClick = {
                                goalInput = UnitConverter.formatNumericValue(ml, unit)
                                goalError = null
                            },
                            label = { Text(UnitConverter.formatVolume(ml, unit)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = goalInput.toFloatOrNull()
                    if (value == null || value <= 0f) {
                        goalError = "Enter a valid target"
                        return@Button
                    }
                    val ml = UnitConverter.displayInputToMl(value, unit)
                    if (ml < 400 || ml > 8000) {
                        goalError = "Target must be between 400 and 8000 ml"
                        return@Button
                    }
                    onConfirm(ml)
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydrateTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("Set Time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AchievementUnlockDialog(
    achievement: AchievementEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = "Achievement unlocked",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(36.dp)
                )
            }
        },
        title = {
            Text(
                text = "Achievement Unlocked!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "+${achievement.xpReward} XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_achievement_button")
            ) {
                Text("Awesome!")
            }
        }
    )
}

@Composable
fun AlarmRingingDialog(
    ringState: AlarmRingState,
    onLogAndStop: (Int) -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit
) {
    val quickLabel = UnitConverter.quickAddButtonLabel(ringState.quickAmountMl, ringState.unit)
    val remainingMl = (ringState.goalMl - ringState.totalMl).coerceAtLeast(0)
    val greetingTitle = if (ringState.userName.isNotBlank()) {
        "Hey ${ringState.userName.trim()}, time for water!"
    } else {
        "Time for a refreshing sip!"
    }

    Dialog(onDismissRequest = onStop) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("alarm_ringing_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Hydration Alarm Ringing",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                WaterBuddyCanvas(
                    mood = BuddyMood.EXCITED,
                    size = 112.dp
                )

                Text(
                    text = greetingTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (remainingMl > 0) {
                        "Progress: ${UnitConverter.formatVolume(ringState.totalMl, ringState.unit)} / " +
                            "${UnitConverter.formatVolume(ringState.goalMl, ringState.unit)} " +
                            "(${UnitConverter.formatVolume(remainingMl, ringState.unit)} left)"
                    } else {
                        "Progress: ${UnitConverter.formatVolume(ringState.totalMl, ringState.unit)} / " +
                            UnitConverter.formatVolume(ringState.goalMl, ringState.unit)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { onLogAndStop(ringState.quickAmountMl) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("alarm_log_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("+$quickLabel (Log & Stop)")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSnooze,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("alarm_snooze_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Snooze 10m")
                    }

                    TextButton(
                        onClick = onStop,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("alarm_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}


package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.MeasurementUnit
import com.example.model.ThemeMode
import com.example.notifications.NotificationHelper
import com.example.ui.components.DailyGoalEditDialog
import com.example.ui.components.HydrateTimePickerDialog
import com.example.ui.components.pressBounce
import com.example.updater.UpdateCheckUiState
import com.example.utils.DateUtils
import com.example.utils.SoundFeedbackPlayer
import com.example.utils.UnitConverter
import com.example.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onDataCleared: () -> Unit,
    onRevisitOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferencesState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotifPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    var pendingTestReminder by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPermission = NotificationHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted || NotificationHelper.hasNotificationPermission(context)
        if (hasNotifPermission) {
            viewModel.setRemindersEnabled(true)
            if (pendingTestReminder) {
                pendingTestReminder = false
                viewModel.sendTestReminder()
            }
        } else if (pendingTestReminder) {
            pendingTestReminder = false
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("Notification permission is needed for reminders")
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.statusMessages.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditGoalDialog by remember { mutableStateOf(false) }
    var showWakePicker by remember { mutableStateOf(false) }
    var showBedPicker by remember { mutableStateOf(false) }
    var showResetTodayConfirm by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val intervals = remember { listOf(15, 30, 45, 60, 90, 120, 180) }
    val withinWakingNow = remember(prefs.wakeHour, prefs.wakeMinute, prefs.bedHour, prefs.bedMinute) {
        DateUtils.isWithinWakingHours(
            wakeHour = prefs.wakeHour,
            wakeMinute = prefs.wakeMinute,
            bedHour = prefs.bedHour,
            bedMinute = prefs.bedMinute
        )
    }
    val wakeTimeLabel = remember(prefs.wakeHour, prefs.wakeMinute) {
        DateUtils.formatHourMinute(prefs.wakeHour, prefs.wakeMinute)
    }
    val bedTimeLabel = remember(prefs.bedHour, prefs.bedMinute) {
        DateUtils.formatHourMinute(prefs.bedHour, prefs.bedMinute)
    }
    val goalSubtitle = remember(prefs.dailyGoalMl, prefs.unit) {
        UnitConverter.formatVolume(prefs.dailyGoalMl, prefs.unit)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "settings_title", contentType = "header") {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("settings_title")
                )
            }

            // Profile & Hydration Target
            item(key = "settings_profile", contentType = "section_card") {
                SettingsSectionCard(title = "Hydration Profile") {
                    SettingsRowItem(
                        icon = Icons.Default.Person,
                        title = "Your Name",
                        subtitle = prefs.userName.ifBlank { "Not set (tap to add)" },
                        onClick = { showEditNameDialog = true },
                        modifier = Modifier.testTag("settings_edit_name")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingsRowItem(
                        icon = Icons.Default.WaterDrop,
                        title = "Daily Hydration Goal",
                        subtitle = goalSubtitle,
                        onClick = { showEditGoalDialog = true },
                        modifier = Modifier.testTag("settings_edit_goal")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Measurement Unit",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        MeasurementUnit.entries.forEachIndexed { index, unitOption ->
                            SegmentedButton(
                                selected = prefs.unit == unitOption,
                                onClick = { viewModel.updateUnit(unitOption) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index,
                                    MeasurementUnit.entries.size
                                ),
                                modifier = Modifier.testTag("settings_unit_${unitOption.name.lowercase()}")
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

            // Reminder Settings
            item(key = "settings_reminders", contentType = "section_card") {
                SettingsSectionCard(title = "Hydration Reminders") {
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
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Enable Reminders", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = when {
                                        !prefs.remindersEnabled -> "Reminders are off"
                                        withinWakingNow && prefs.ringingReminderEnabled ->
                                            "Active • Alarm Ring • Every ${prefs.reminderIntervalMinutes}m"
                                        withinWakingNow -> "Active • Notification • Every ${prefs.reminderIntervalMinutes}m"
                                        else -> "Paused during sleep hours"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = prefs.remindersEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.setRemindersEnabled(enabled)
                            },
                            modifier = Modifier.testTag("settings_reminders_switch")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

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
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Ringing Alarm Mode",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (prefs.remindersEnabled) {
                                        "Ring like an alarm/timer until you drink, snooze, or stop"
                                    } else {
                                        "Optional continuous alarm ring (enable reminders above)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = prefs.ringingReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                if (enabled && !prefs.remindersEnabled) {
                                    viewModel.setRemindersEnabled(true)
                                }
                                viewModel.setRingingReminderEnabled(enabled)
                            },
                            modifier = Modifier.testTag("settings_ringing_switch")
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Notification permission is off",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                ) {
                                    Text("Allow")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { showWakePicker = true }
                                .testTag("settings_wake_time")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Wake-up", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    text = wakeTimeLabel,
                                    style = MaterialTheme.typography.titleSmall,
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
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { showBedPicker = true }
                                .testTag("settings_bed_time")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bedtime", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    text = bedTimeLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Reminder Interval", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        intervals.forEach { mins ->
                            val label = if (mins >= 60 && mins % 60 == 0) "${mins / 60}h" else "${mins}m"
                            FilterChip(
                                selected = prefs.reminderIntervalMinutes == mins,
                                onClick = { viewModel.updateReminderInterval(mins) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                pendingTestReminder = true
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.sendTestReminder()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("settings_test_reminder_button")
                    ) {
                        Text(
                            if (prefs.ringingReminderEnabled) {
                                "Test Ringing Alarm Reminder"
                            } else {
                                "Send Test Reminder Notification"
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            scope.launch { SoundFeedbackPlayer.playAlarmChimePreview(context) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("settings_preview_water_chime_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Ringtone")
                    }
                }
            }

            // Experience & Theme
            item(key = "settings_experience", contentType = "section_card") {
                SettingsSectionCard(title = "Experience & Appearance") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Water Drop Sound", style = MaterialTheme.typography.titleSmall)
                        }
                        Switch(
                            checked = prefs.soundEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setSoundEnabled(enabled)
                                if (enabled) {
                                    scope.launch { SoundFeedbackPlayer.playWaterDropChime() }
                                }
                            },
                            modifier = Modifier.testTag("settings_sound_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Haptic Vibration", style = MaterialTheme.typography.titleSmall)
                        }
                        Switch(
                            checked = prefs.vibrationEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setVibrationEnabled(enabled)
                                if (enabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            modifier = Modifier.testTag("settings_vibration_switch")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                            ) {
                                Text(
                                    text = mode.label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Data Management
            item(key = "settings_data", contentType = "section_card") {
                SettingsSectionCard(title = "Data & Setup") {
                    SettingsRowItem(
                        icon = Icons.Default.Tune,
                        title = "Revisit Welcome Setup",
                        subtitle = "Walk through onboarding preferences without losing data",
                        onClick = onRevisitOnboarding,
                        modifier = Modifier.testTag("settings_revisit_onboarding")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingsRowItem(
                        icon = Icons.Default.RestartAlt,
                        title = "Reset Today's Progress",
                        subtitle = "Clear all drinks logged today",
                        onClick = { showResetTodayConfirm = true },
                        modifier = Modifier.testTag("settings_reset_today")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingsRowItem(
                        icon = Icons.Default.DeleteForever,
                        title = "Clear All Application Data",
                        subtitle = "Delete all logs, streaks, badges, and preferences",
                        isDestructive = true,
                        onClick = { showClearDataConfirm = true },
                        modifier = Modifier.testTag("settings_clear_all")
                    )
                }
            }

            // App Updates
            item(key = "settings_updates", contentType = "section_card") {
                val isChecking = updateState is UpdateCheckUiState.Checking
                val isDownloading = updateState is UpdateCheckUiState.Downloading

                SettingsSectionCard(title = "App Updates") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
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
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "v${viewModel.currentVersionName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "Build ${viewModel.currentVersionCode}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val statusText = when (val st = updateState) {
                                        is UpdateCheckUiState.Idle ->
                                            "Checks automatically while app is open and online"
                                        is UpdateCheckUiState.Checking ->
                                            "Checking for a newer release..."
                                        is UpdateCheckUiState.UpToDate ->
                                            if (st.noPublishedReleasesYet) {
                                                "No update packages published yet"
                                            } else {
                                                "You're on the latest version"
                                            }
                                        is UpdateCheckUiState.Offline ->
                                            "Offline — turn on Wi-Fi or mobile data to check"
                                        is UpdateCheckUiState.UpdateAvailable ->
                                            "New version available: v${st.info.versionName}"
                                        is UpdateCheckUiState.Downloading ->
                                            "Downloading update v${st.info.versionName}..."
                                        is UpdateCheckUiState.ReadyToInstall ->
                                            if (st.needsInstallPermission) {
                                                "Allow \"Install unknown apps\" to finish installing v${st.info.versionName}"
                                            } else {
                                                "Update v${st.info.versionName} downloaded and ready"
                                            }
                                        is UpdateCheckUiState.Error ->
                                            st.message
                                    }
                                    AnimatedContent(
                                        targetState = statusText,
                                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                                        label = "update_status_text"
                                    ) { text ->
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (updateState) {
                                                is UpdateCheckUiState.UpdateAvailable,
                                                is UpdateCheckUiState.ReadyToInstall -> MaterialTheme.colorScheme.primary
                                                is UpdateCheckUiState.Error -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val actionLabel = when (val st = updateState) {
                            is UpdateCheckUiState.Checking -> "Checking..."
                            is UpdateCheckUiState.Downloading -> "Downloading..."
                            is UpdateCheckUiState.UpdateAvailable -> "Install Update v${st.info.versionName}"
                            is UpdateCheckUiState.ReadyToInstall -> "Install Update v${st.info.versionName}"
                            else -> "Check for Updates"
                        }

                        val checkInteractionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                when (updateState) {
                                    is UpdateCheckUiState.UpdateAvailable,
                                    is UpdateCheckUiState.ReadyToInstall -> viewModel.openOrStartAvailableUpdate()
                                    else -> viewModel.checkForUpdatesManually()
                                }
                            },
                            enabled = !isChecking && !isDownloading,
                            interactionSource = checkInteractionSource,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .pressBounce(checkInteractionSource, pressedScale = 0.98f)
                                .testTag("settings_check_updates_button")
                        ) {
                            if (isChecking || isDownloading) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(actionLabel)
                        }
                    }
                }
            }

            // About Card
            item(key = "settings_about", contentType = "about_card") {
                val aboutInteraction = remember { MutableInteractionSource() }
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressBounce(aboutInteraction, pressedScale = 0.985f)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(
                            interactionSource = aboutInteraction,
                            indication = LocalIndication.current
                        ) { showAboutDialog = true }
                        .testTag("settings_about_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "About Hydrate Buddy",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Hydrate Buddy v${viewModel.currentVersionName} (Tap for details)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.about_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item(key = "settings_bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showEditNameDialog) {
        var nameInput by remember { mutableStateOf(prefs.userName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Name") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(32) },
                    label = { Text("Your name (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_name_input")
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateUserName(nameInput)
                    showEditNameDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditGoalDialog) {
        DailyGoalEditDialog(
            currentGoalMl = prefs.dailyGoalMl,
            unit = prefs.unit,
            onDismiss = { showEditGoalDialog = false },
            onConfirm = { ml ->
                viewModel.updateDailyGoal(ml)
                showEditGoalDialog = false
            }
        )
    }

    if (showWakePicker) {
        HydrateTimePickerDialog(
            title = "Wake-up Time",
            initialHour = prefs.wakeHour,
            initialMinute = prefs.wakeMinute,
            onDismiss = { showWakePicker = false },
            onConfirm = { h, m ->
                viewModel.updateWakeTime(h, m)
                showWakePicker = false
            }
        )
    }

    if (showBedPicker) {
        HydrateTimePickerDialog(
            title = "Bedtime",
            initialHour = prefs.bedHour,
            initialMinute = prefs.bedMinute,
            onDismiss = { showBedPicker = false },
            onConfirm = { h, m ->
                viewModel.updateBedTime(h, m)
                showBedPicker = false
            }
        )
    }

    if (showResetTodayConfirm) {
        AlertDialog(
            onDismissRequest = { showResetTodayConfirm = false },
            title = { Text("Reset Today's Progress?") },
            text = { Text("This will remove all water entries logged today. Past days and unlocked badges will remain.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetTodayProgress()
                        showResetTodayConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Today")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetTodayConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Clear All Data?") },
            text = { Text("This permanently deletes your hydration history, streaks, achievements, and preferences.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataConfirm = false
                        viewModel.clearAllData(onCleared = onDataCleared)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Hydrate Buddy", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Wellness Note: Daily hydration goals in Hydrate Buddy are personal habit targets and are not medical advice. Adjust your target based on your personal needs, climate, and activity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val containerColor = if (isDestructive) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .pressBounce(interactionSource, pressedScale = 0.985f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = containerColor,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

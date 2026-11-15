package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.database.WaterEntryEntity
import com.example.model.BuddyMood
import com.example.model.DrinkType
import com.example.model.HomeUiState
import com.example.model.MeasurementUnit
import com.example.ui.components.AchievementUnlockDialog
import com.example.ui.components.CustomWaterDialog
import com.example.ui.components.DailyGoalEditDialog
import com.example.ui.components.EditWaterEntryDialog
import com.example.ui.components.HydrationProgressRing
import com.example.ui.components.WaterBuddyCanvas
import com.example.ui.components.WaterDropletBurst
import com.example.ui.components.pressBounce
import com.example.ui.theme.LocalHydrateColors
import com.example.utils.DateUtils
import com.example.utils.UnitConverter
import com.example.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    vibrationEnabled: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateToAchievements: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUiState by rememberUpdatedState(uiState)
    val burstTrigger by viewModel.dropletBurstTrigger.collectAsStateWithLifecycle()
    val unlockedQueue by viewModel.unlockedBannerQueue.collectAsStateWithLifecycle()

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.home_undo_action)
    var showCustomDialog by remember { mutableStateOf(false) }
    var showEditGoalDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<WaterEntryEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshDateIfNeeded()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDateIfNeeded()
    }

    LaunchedEffect(viewModel) {
        viewModel.feedbackEvents.collect { event ->
            val state = currentUiState as? HomeUiState.Success
            val unit = state?.unit ?: MeasurementUnit.ML
            val formattedAmount = UnitConverter.formatVolume(event.amountMl, unit)
            val message = when {
                event.goalReachedNow -> "Added $formattedAmount — Daily hydration goal reached!"
                event.milestoneReached != null -> "Added $formattedAmount — ${event.milestoneReached}% milestone!"
                else -> "Added $formattedAmount"
            }

            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoEntry(event.entryId)
            }
        }
    }

    val currentBanner = unlockedQueue.firstOrNull()
    if (currentBanner != null) {
        AchievementUnlockDialog(
            achievement = currentBanner,
            onDismiss = viewModel::dismissUnlockedAchievement
        )
    }

    when (val state = uiState) {
        is HomeUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is HomeUiState.Error -> {
            Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is HomeUiState.Success -> {
            if (showCustomDialog) {
                CustomWaterDialog(
                    unit = state.unit,
                    onDismiss = { showCustomDialog = false },
                    onConfirm = { ml, drinkType ->
                        if (vibrationEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        viewModel.logWater(ml, drinkType)
                        showCustomDialog = false
                    }
                )
            }

            if (showEditGoalDialog) {
                DailyGoalEditDialog(
                    currentGoalMl = state.goalMl,
                    unit = state.unit,
                    onDismiss = { showEditGoalDialog = false },
                    onConfirm = { newGoalMl ->
                        viewModel.updateDailyGoal(newGoalMl)
                        showEditGoalDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Daily target set to ${UnitConverter.formatVolume(newGoalMl, state.unit)}"
                            )
                        }
                    }
                )
            }

            val currentVibrationEnabled by rememberUpdatedState(vibrationEnabled)
            val deleteWithUndo: (WaterEntryEntity) -> Unit = remember(viewModel, scope, snackbarHostState, undoLabel) {
                { entry ->
                    val currentUnit = (currentUiState as? HomeUiState.Success)?.unit ?: MeasurementUnit.ML
                    viewModel.deleteEntry(entry.id)
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val res = snackbarHostState.showSnackbar(
                            message = "Removed ${UnitConverter.formatVolume(entry.amountMl, currentUnit)}",
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short
                        )
                        if (res == SnackbarResult.ActionPerformed) {
                            viewModel.restoreEntry(entry)
                        }
                    }
                }
            }
            val onQuickAddStable: (Int, DrinkType) -> Unit = remember(viewModel, haptic) {
                { amountMl, drinkType ->
                    if (currentVibrationEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    viewModel.logWater(amountMl, drinkType)
                }
            }
            val onBuddyTapStable: () -> Unit = remember(viewModel, haptic) {
                {
                    if (currentVibrationEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    viewModel.triggerBuddyCheer()
                }
            }
            val onOpenCustomStable: () -> Unit = remember { { showCustomDialog = true } }
            val onOpenGoalEditStable: () -> Unit = remember { { showEditGoalDialog = true } }
            val onEditEntryStable: (WaterEntryEntity) -> Unit = remember { { editingEntry = it } }

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

            HomeDashboardContent(
                state = state,
                burstTrigger = burstTrigger,
                onQuickAdd = onQuickAddStable,
                onOpenCustom = onOpenCustomStable,
                onOpenGoalEdit = onOpenGoalEditStable,
                onNavigateToAchievements = onNavigateToAchievements,
                onBuddyTap = onBuddyTapStable,
                onEditEntry = onEditEntryStable,
                onDeleteEntry = deleteWithUndo,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun HomeDashboardContent(
    state: HomeUiState.Success,
    burstTrigger: Int,
    onQuickAdd: (Int, DrinkType) -> Unit,
    onOpenCustom: () -> Unit,
    onOpenGoalEdit: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    onBuddyTap: () -> Unit,
    onEditEntry: (WaterEntryEntity) -> Unit,
    onDeleteEntry: (WaterEntryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalHydrateColors.current
    val animatedTotalMl by animateIntAsState(
        targetValue = state.totalMl,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "home_total_intake_anim"
    )
    val pctDisplay = remember(state.totalMl, state.goalMl) {
        ((state.totalMl.toFloat() / state.goalMl.coerceAtLeast(1)) * 100f).roundToInt()
    }
    val formattedTotal = remember(animatedTotalMl, state.unit) {
        UnitConverter.formatVolume(animatedTotalMl, state.unit)
    }
    val formattedGoal = remember(state.goalMl, state.unit) {
        UnitConverter.formatVolume(state.goalMl, state.unit)
    }
    val formattedRemaining = remember(state.remainingMl, state.unit) {
        UnitConverter.formatVolume(state.remainingMl, state.unit)
    }
    val heroGradient = remember(extendedColors.cardGradientStart, extendedColors.cardGradientEnd) {
        Brush.verticalGradient(
            colors = listOf(
                extendedColors.cardGradientStart,
                extendedColors.cardGradientEnd
            )
        )
    }
    val lastEntry = state.entries.firstOrNull()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .testTag("home_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Greeting, Date, Streak & Level header
            item(key = "home_header", contentType = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.greeting,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag("home_greeting_text")
                        )
                        Text(
                            text = state.displayDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val streakInteraction = remember { MutableInteractionSource() }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                            modifier = Modifier
                                .pressBounce(streakInteraction, pressedScale = 0.94f)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(
                                    interactionSource = streakInteraction,
                                    indication = LocalIndication.current,
                                    onClick = onNavigateToAchievements
                                )
                                .testTag("home_streak_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "View streak rewards",
                                    tint = extendedColors.warmAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${state.currentStreak}d",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        val levelInteraction = remember { MutableInteractionSource() }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                            modifier = Modifier
                                .pressBounce(levelInteraction, pressedScale = 0.94f)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(
                                    interactionSource = levelInteraction,
                                    indication = LocalIndication.current,
                                    onClick = onNavigateToAchievements
                                )
                                .testTag("home_level_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "View XP level rewards",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Lv.${state.levelInfo.level}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // 2. Hero Virtual Buddy + Progress Ring Card
            item(key = "home_hero", contentType = "hero") {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(heroGradient)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val moodAccentColor = when (state.buddyMood) {
                            BuddyMood.CELEBRATING -> extendedColors.success
                            BuddyMood.EXCITED, BuddyMood.HAPPY -> MaterialTheme.colorScheme.primary
                            BuddyMood.NEUTRAL -> MaterialTheme.colorScheme.secondary
                            BuddyMood.THIRSTY -> extendedColors.warmAccent
                            BuddyMood.SAD -> MaterialTheme.colorScheme.error
                        }

                        // Speech bubble + mood status pill with smooth animated transitions
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = moodAccentColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, moodAccentColor.copy(alpha = 0.28f)),
                            modifier = Modifier.testTag("buddy_mood_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(moodAccentColor)
                                )
                                Text(
                                    text = "Feeling ${state.buddyMood.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = moodAccentColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, moodAccentColor.copy(alpha = 0.35f)),
                            tonalElevation = 1.dp,
                            modifier = Modifier.animateContentSize()
                        ) {
                            AnimatedContent(
                                targetState = state.buddyMessage,
                                transitionSpec = {
                                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 5 })
                                        .togetherWith(
                                            fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 5 }
                                        )
                                },
                                label = "buddy_message_transition"
                            ) { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                        .testTag("buddy_status_message")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress Ring + Droplet Buddy + Particle Burst
                        Box(contentAlignment = Alignment.Center) {
                            HydrationProgressRing(
                                progress = state.progressFraction,
                                ringSize = 236.dp,
                                strokeWidth = 18.dp
                            ) {
                                WaterBuddyCanvas(
                                    mood = state.buddyMood,
                                    size = 166.dp,
                                    onBuddyTap = onBuddyTap
                                )
                            }

                            WaterDropletBurst(
                                triggerCount = burstTrigger,
                                modifier = Modifier.size(250.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Total / Tappable Goal display
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = formattedTotal,
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("home_total_intake_text")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val goalInteraction = remember { MutableInteractionSource() }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .padding(bottom = 2.dp)
                                    .pressBounce(goalInteraction, pressedScale = 0.94f)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(
                                        interactionSource = goalInteraction,
                                        indication = LocalIndication.current,
                                        onClick = onOpenGoalEdit
                                    )
                                    .testTag("home_goal_edit_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "/ $formattedGoal",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit daily goal",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Remaining & Percentage pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = state.remainingMl == 0,
                                enter = fadeIn() + scaleIn()
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = extendedColors.successContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = extendedColors.success,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.home_goal_reached_badge),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = extendedColors.onSuccessContainer
                                        )
                                    }
                                }
                            }

                            if (state.remainingMl > 0) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                                ) {
                                    Text(
                                        text = "${stringResource(R.string.home_remaining_label)}: $formattedRemaining",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .testTag("home_remaining_text")
                                    )
                                }
                            }

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "$pctDisplay%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Quick-Add Section
            item(key = "home_quick_add", contentType = "quick_add") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_quick_add_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (lastEntry != null) {
                                val repeatLabel = UnitConverter.formatVolume(lastEntry.amountMl, state.unit)
                                AssistChip(
                                    onClick = {
                                        onQuickAdd(lastEntry.amountMl, DrinkType.fromName(lastEntry.drinkType))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Replay,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(repeatLabel) },
                                    modifier = Modifier.testTag("repeat_last_button")
                                )
                            }
                            OutlinedButton(
                                onClick = onOpenCustom,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("custom_water_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.home_custom_add))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.quickAddAmountsMl.forEach { amountMl ->
                            val label = UnitConverter.quickAddButtonLabel(amountMl, state.unit)
                            val quickInteraction = remember(amountMl) { MutableInteractionSource() }
                            FilledTonalButton(
                                onClick = { onQuickAdd(amountMl, DrinkType.WATER) },
                                interactionSource = quickInteraction,
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .pressBounce(quickInteraction, pressedScale = 0.94f)
                                    .testTag("quick_add_$amountMl")
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.WaterDrop,
                                                contentDescription = "Add $label",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
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

            // 4. Today's Sips Log Header
            item(key = "home_log_header", contentType = "log_header") {
                Text(
                    text = "${stringResource(R.string.home_today_log_title)} (${state.entries.size})",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (state.entries.isEmpty()) {
                item(key = "home_empty_log", contentType = "empty_log") {
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
                                imageVector = Icons.Default.LocalDrink,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.home_empty_log_title),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.home_empty_log_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.entries,
                    key = { it.id },
                    contentType = { "water_entry" }
                ) { entry ->
                    WaterEntryRowCard(
                        entry = entry,
                        unit = state.unit,
                        onEditClick = { onEditEntry(entry) },
                        onDeleteClick = { onDeleteEntry(entry) },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item(key = "home_bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun WaterEntryRowCard(
    entry: WaterEntryEntity,
    unit: MeasurementUnit,
    onEditClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val formattedAmount = remember(entry.amountMl, unit) {
        UnitConverter.formatVolume(entry.amountMl, unit)
    }
    val subtitle = remember(entry.drinkType, entry.timestampMillis) {
        val drinkType = DrinkType.fromName(entry.drinkType)
        "${drinkType.label} • ${DateUtils.formatTime(entry.timestampMillis)}"
    }
    val rowInteraction = remember { MutableInteractionSource() }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .pressBounce(rowInteraction, pressedScale = 0.985f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = rowInteraction,
                indication = LocalIndication.current,
                onClick = onEditClick
            )
            .testTag("water_entry_card_${entry.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onEditClick,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit drink entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onDeleteClick != null) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete drink entry",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

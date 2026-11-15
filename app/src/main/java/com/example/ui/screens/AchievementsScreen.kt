package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.AchievementEntity
import com.example.model.AchievementFilter
import com.example.model.AchievementsUiState
import com.example.model.LevelInfo
import com.example.ui.theme.LocalHydrateColors
import com.example.utils.DateUtils
import com.example.utils.UnitConverter
import com.example.viewmodel.AchievementsViewModel

@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedAchievement by remember { mutableStateOf<AchievementEntity?>(null) }
    var showLevelRoadmap by remember { mutableStateOf(false) }

    when (val state = uiState) {
        is AchievementsUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is AchievementsUiState.Success -> {
            val extendedColors = LocalHydrateColors.current

            selectedAchievement?.let { achievement ->
                AchievementDetailDialog(
                    achievement = achievement,
                    onDismiss = { selectedAchievement = null }
                )
            }

            if (showLevelRoadmap) {
                LevelRoadmapDialog(
                    levelInfo = state.levelInfo,
                    onDismiss = { showLevelRoadmap = false }
                )
            }

            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            text = "Achievements & Rewards",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.testTag("achievements_title")
                        )
                    }

                    // Level & XP Hero Card (tappable for Level Roadmap)
                    item {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable { showLevelRoadmap = true }
                                .testTag("achievements_level_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            )
                                        )
                                    )
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Level ${state.levelInfo.level} • ${state.levelInfo.title}",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${state.levelInfo.totalXp} Total XP • Tap for ranks",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    ) {
                                        Text(
                                            text = "${state.unlockedCount}/${state.totalCount}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { state.levelInfo.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                )

                                Text(
                                    text = "${state.levelInfo.xpInCurrentLevel} / ${state.levelInfo.xpRequiredForNext} XP to Level ${state.levelInfo.level + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Streak & Lifetime Summary Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RewardMetricCard(
                                icon = Icons.Default.LocalFireDepartment,
                                iconTint = extendedColors.warmAccent,
                                label = "Current Streak",
                                value = "${state.currentStreak} days",
                                modifier = Modifier.weight(1f)
                            )
                            RewardMetricCard(
                                icon = Icons.Default.MilitaryTech,
                                iconTint = MaterialTheme.colorScheme.tertiary,
                                label = "Best Streak",
                                value = "${state.longestStreak} days",
                                modifier = Modifier.weight(1f)
                            )
                            RewardMetricCard(
                                icon = Icons.Default.WaterDrop,
                                iconTint = MaterialTheme.colorScheme.primary,
                                label = "Lifetime",
                                value = UnitConverter.formatVolume(state.lifetimeTotalMl, state.unit),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Filter Chips
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AchievementFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = state.filter == filter,
                                    onClick = { viewModel.setFilter(filter) },
                                    label = { Text(filter.label) },
                                    modifier = Modifier.testTag("achievement_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }

                    if (state.achievements.isEmpty()) {
                        item {
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
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Nothing in this filter yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Keep sipping water to unlock new hydration badges!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.achievements, key = { it.id }) { achievement ->
                            AchievementItemCard(
                                achievement = achievement,
                                onClick = { selectedAchievement = achievement }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RewardMetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AchievementItemCard(
    achievement: AchievementEntity,
    onClick: () -> Unit
) {
    val extendedColors = LocalHydrateColors.current
    val progress = (achievement.progressCurrent.toFloat() / achievement.progressTarget.coerceAtLeast(1))
        .coerceIn(0f, 1f)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (achievement.isUnlocked) 1.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("achievement_card_${achievement.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (achievement.isUnlocked) {
                    extendedColors.successContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (achievement.isUnlocked) {
                            Icons.Default.WorkspacePremium
                        } else {
                            Icons.Default.Lock
                        },
                        contentDescription = if (achievement.isUnlocked) "Unlocked badge" else "Locked badge",
                        tint = if (achievement.isUnlocked) {
                            extendedColors.success
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (achievement.isUnlocked) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Text(
                            text = "+${achievement.xpReward} XP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (achievement.isUnlocked) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (achievement.isUnlocked && achievement.unlockedAtMillis != null) {
                    Text(
                        text = "Unlocked • ${DateUtils.formatDisplayDate(DateUtils.isoFromTimestamp(achievement.unlockedAtMillis))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = extendedColors.success,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(MaterialTheme.shapes.small)
                    )
                    Text(
                        text = "${achievement.progressCurrent} / ${achievement.progressTarget}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementDetailDialog(
    achievement: AchievementEntity,
    onDismiss: () -> Unit
) {
    val extendedColors = LocalHydrateColors.current
    val progress = (achievement.progressCurrent.toFloat() / achievement.progressTarget.coerceAtLeast(1))
        .coerceIn(0f, 1f)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (achievement.isUnlocked) Icons.Default.WorkspacePremium else Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = if (achievement.isUnlocked) extendedColors.success else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = { Text(achievement.title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Category: ${achievement.category} • Reward: +${achievement.xpReward} XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.small)
                )
                Text(
                    text = if (achievement.isUnlocked) {
                        "Completed (${achievement.progressTarget}/${achievement.progressTarget})"
                    } else {
                        "Progress: ${achievement.progressCurrent} / ${achievement.progressTarget}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun LevelRoadmapDialog(
    levelInfo: LevelInfo,
    onDismiss: () -> Unit
) {
    val ranks = listOf(
        1 to "Dewdrop (0+ XP)",
        2 to "Splasher (150+ XP)",
        3 to "Brook (400+ XP)",
        4 to "Stream (750+ XP)",
        5 to "Wave Rider (1200+ XP)",
        6 to "Tide Keeper (1750+ XP)",
        7 to "Aqua Hero (2400+ XP)",
        8 to "Ocean Guardian (3150+ XP)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hydration Ranks", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Earn +10 XP per logged drink, +25 XP on days you reach your target, and bonus XP from badges.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ranks.forEach { (lvl, label) ->
                    val reached = levelInfo.level >= lvl
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (levelInfo.level == lvl) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else if (reached) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Lv.$lvl • $label",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (levelInfo.level == lvl) FontWeight.Bold else FontWeight.Normal
                            )
                            if (levelInfo.level == lvl) {
                                Text("Current", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It")
            }
        }
    )
}

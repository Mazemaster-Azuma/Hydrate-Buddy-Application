package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.updater.UpdateCheckUiState
import com.example.updater.UpdateVersionComparator
import kotlin.math.roundToInt

@Composable
fun AppUpdateDialog(
    updateState: UpdateCheckUiState,
    currentVersionName: String,
    currentVersionCode: Int,
    onStartUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit
) {
    when (updateState) {
        is UpdateCheckUiState.UpdateAvailable -> {
            if (!updateState.showDialog) return
            val info = updateState.info
            val sizeLabel = UpdateVersionComparator.formatFileSize(info.sizeBytes)

            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                },
                title = {
                    Text(
                        text = "Update Available",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "A newer version of Hydrate Buddy is ready to install.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Current: v$currentVersionName (Build $currentVersionCode)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val buildPart = if (info.versionCode > 0) " (Build ${info.versionCode})" else ""
                                val sizePart = if (sizeLabel.isNotEmpty()) " • $sizeLabel" else ""
                                Text(
                                    text = "New: v${info.versionName}$buildPart$sizePart",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                text = "What's new:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = info.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onStartUpdate,
                        modifier = Modifier.testTag("update_dialog_install_button")
                    ) {
                        Text("Update Now")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_dialog_later_button")
                    ) {
                        Text("Later")
                    }
                }
            )
        }

        is UpdateCheckUiState.Downloading -> {
            val info = updateState.info
            val pct = if (updateState.progress >= 0f) (updateState.progress * 100f).roundToInt() else null
            val downloadedLabel = UpdateVersionComparator.formatFileSize(updateState.downloadedBytes)
            val totalLabel = UpdateVersionComparator.formatFileSize(updateState.totalBytes)

            AlertDialog(
                onDismissRequest = { /* Prevent accidental dismiss while downloading */ },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                },
                title = {
                    Text(
                        text = "Downloading v${info.versionName}",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (updateState.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { updateState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(MaterialTheme.shapes.small)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(MaterialTheme.shapes.small)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val bytesText = when {
                                downloadedLabel.isNotEmpty() && totalLabel.isNotEmpty() -> "$downloadedLabel / $totalLabel"
                                downloadedLabel.isNotEmpty() -> downloadedLabel
                                else -> "Downloading..."
                            }
                            Text(
                                text = bytesText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (pct != null) {
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.testTag("update_dialog_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        is UpdateCheckUiState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.InstallMobile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                },
                title = {
                    Text(
                        text = if (updateState.needsInstallPermission) {
                            "Allow App Updates"
                        } else {
                            "Ready to Install v${updateState.info.versionName}"
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Text(
                        text = if (updateState.needsInstallPermission) {
                            "To install the downloaded update over your existing app, allow \"Install unknown apps\" for Hydrate Buddy in Android settings. Installation will resume automatically when you return."
                        } else {
                            "The verified update package is ready. Tap Install Update to launch Android's installer and keep all your hydration logs and streaks."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    if (updateState.needsInstallPermission) {
                        Button(onClick = onOpenInstallPermissionSettings) {
                            Text("Open Settings")
                        }
                    } else {
                        Button(onClick = onStartUpdate) {
                            Text("Install Update")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Later")
                    }
                }
            )
        }

        is UpdateCheckUiState.Error -> {
            if (!updateState.isManual) return
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Update Check",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Text(
                        text = updateState.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    if (updateState.info != null) {
                        Button(onClick = onStartUpdate) {
                            Text("Retry")
                        }
                    } else {
                        Button(onClick = onDismiss) {
                            Text("OK")
                        }
                    }
                },
                dismissButton = if (updateState.info != null) {
                    {
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                } else null
            )
        }

        else -> Unit
    }
}

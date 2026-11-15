package com.example.updater

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUpdateMetadata(
    val versionCode: Int = 0,
    val versionName: String = "",
    val tagName: String = "",
    val apkFileName: String = "",
    val apkSha256: String = "",
    val apkSizeBytes: Long = 0L,
    val releaseNotes: String = ""
)

@Serializable
data class GitHubReleaseResponse(
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    val url: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
    val size: Long = 0L,
    @SerialName("content_type")
    val contentType: String = ""
)

@Immutable
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val tagName: String,
    val downloadUrl: String,
    val apkFileName: String,
    val expectedSha256: String,
    val sizeBytes: Long,
    val releaseNotes: String
)

@Immutable
sealed interface UpdateCheckUiState {
    data object Idle : UpdateCheckUiState

    @Immutable
    data class Checking(val isManual: Boolean) : UpdateCheckUiState

    @Immutable
    data class UpToDate(
        val versionName: String,
        val versionCode: Int,
        val checkedAtMillis: Long,
        val noPublishedReleasesYet: Boolean = false
    ) : UpdateCheckUiState

    @Immutable
    data class Offline(val isManual: Boolean) : UpdateCheckUiState

    @Immutable
    data class UpdateAvailable(
        val info: AppUpdateInfo,
        val showDialog: Boolean
    ) : UpdateCheckUiState

    @Immutable
    data class Downloading(
        val info: AppUpdateInfo,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateCheckUiState

    @Immutable
    data class ReadyToInstall(
        val info: AppUpdateInfo,
        val apkFilePath: String,
        val needsInstallPermission: Boolean
    ) : UpdateCheckUiState

    @Immutable
    data class Error(
        val message: String,
        val isManual: Boolean,
        val info: AppUpdateInfo? = null
    ) : UpdateCheckUiState
}

package com.example.updater

import android.content.Context
import com.example.BuildConfig
import com.example.repository.HydrationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class AppUpdateCoordinator(
    private val appContext: Context,
    private val repository: HydrationRepository,
    private val client: GitHubUpdateClient = GitHubUpdateClient()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val checkMutex = Mutex()

    private val _uiState = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Idle)
    val uiState: StateFlow<UpdateCheckUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private var lastAutoCheckAtMillis: Long = 0L
    private var dismissedVersionCodeThisSession: Int? = null
    private var lastInstalledCleanupDone = false

    val currentVersionCode: Int
        get() = BuildConfig.VERSION_CODE

    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    fun resolveConfiguredRepo(prefsRepoOverride: String): String {
        val normalizedOverride = UpdateVersionComparator.normalizeRepoSlug(prefsRepoOverride)
        if (!normalizedOverride.isNullOrBlank()) return normalizedOverride
        return UpdateVersionComparator.normalizeRepoSlug(BuildConfig.GITHUB_REPOSITORY).orEmpty()
    }

    fun isBuiltInRepoPresent(): Boolean {
        return UpdateVersionComparator.normalizeRepoSlug(BuildConfig.GITHUB_REPOSITORY) != null
    }

    fun onForegroundNetworkConnected() {
        val now = System.currentTimeMillis()
        val currentState = _uiState.value
        if (currentState is UpdateCheckUiState.Downloading ||
            currentState is UpdateCheckUiState.ReadyToInstall
        ) {
            return
        }
        if (currentState is UpdateCheckUiState.UpdateAvailable) {
            return
        }
        // Throttle automatic checks to at most once every 10 minutes per foreground lifecycle
        if (lastAutoCheckAtMillis > 0L && (now - lastAutoCheckAtMillis) < AUTO_CHECK_COOLDOWN_MS) {
            return
        }
        scope.launch {
            performCheck(isManual = false)
        }
    }

    fun checkForUpdatesManually() {
        val currentState = _uiState.value
        if (currentState is UpdateCheckUiState.Downloading) return
        scope.launch {
            performCheck(isManual = true)
        }
    }

    fun onForegroundResumed() {
        if (!lastInstalledCleanupDone) {
            lastInstalledCleanupDone = true
            scope.launch(Dispatchers.IO) {
                ApkInstaller.cleanupStaleUpdates(appContext, currentVersionCode.toLong())
            }
        }
        val state = _uiState.value
        if (state is UpdateCheckUiState.ReadyToInstall && state.needsInstallPermission) {
            if (ApkInstaller.canRequestPackageInstalls(appContext)) {
                val file = File(state.apkFilePath)
                if (file.isFile) {
                    val launched = ApkInstaller.launchInstaller(appContext, file)
                    _uiState.value = if (launched) {
                        state.copy(needsInstallPermission = false)
                    } else {
                        UpdateCheckUiState.Error(
                            message = "Could not open the Android package installer.",
                            isManual = true,
                            info = state.info
                        )
                    }
                }
            }
        }
    }

    fun showAvailableUpdateDialog() {
        val current = _uiState.value
        if (current is UpdateCheckUiState.UpdateAvailable) {
            _uiState.value = current.copy(showDialog = true)
        }
    }

    fun dismissUpdateDialog() {
        when (val current = _uiState.value) {
            is UpdateCheckUiState.UpdateAvailable -> {
                dismissedVersionCodeThisSession = current.info.versionCode
                _uiState.value = current.copy(showDialog = false)
            }
            is UpdateCheckUiState.ReadyToInstall -> {
                dismissedVersionCodeThisSession = current.info.versionCode
                _uiState.value = UpdateCheckUiState.UpdateAvailable(
                    info = current.info,
                    showDialog = false
                )
            }
            is UpdateCheckUiState.Error -> {
                val info = current.info
                _uiState.value = if (info != null) {
                    UpdateCheckUiState.UpdateAvailable(info = info, showDialog = false)
                } else {
                    UpdateCheckUiState.Idle
                }
            }
            else -> Unit
        }
    }

    fun startDownloadAndInstall() {
        val info = when (val current = _uiState.value) {
            is UpdateCheckUiState.UpdateAvailable -> current.info
            is UpdateCheckUiState.ReadyToInstall -> {
                val file = File(current.apkFilePath)
                if (file.isFile) {
                    launchOrRequestPermission(current.info, file)
                    return
                }
                current.info
            }
            is UpdateCheckUiState.Error -> current.info ?: return
            else -> return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            if (!NetworkMonitor.isInternetAvailable(appContext)) {
                _uiState.value = UpdateCheckUiState.Error(
                    message = "No internet connection. Turn on Wi-Fi or mobile data and try again.",
                    isManual = true,
                    info = info
                )
                return@launch
            }

            val targetFile = ApkInstaller.getTargetApkFile(appContext, info.versionCode, info.apkFileName)

            // If a previously downloaded, verified APK already exists on disk, reuse it immediately
            val alreadyValid = withContext(Dispatchers.IO) {
                targetFile.isFile &&
                    ApkInstaller.verifyApkCompatibility(
                        appContext,
                        targetFile,
                        currentVersionCode.toLong()
                    ) == ApkCompatibilityResult.Compatible
            }

            if (alreadyValid) {
                launchOrRequestPermission(info, targetFile)
                return@launch
            }

            _uiState.value = UpdateCheckUiState.Downloading(
                info = info,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = info.sizeBytes
            )

            val downloadResult = withContext(Dispatchers.IO) {
                client.downloadApk(
                    downloadUrl = info.downloadUrl,
                    targetFile = targetFile,
                    expectedSha256 = info.expectedSha256,
                    expectedSizeBytes = info.sizeBytes
                ) { downloaded, total ->
                    val fraction = if (total > 0L) {
                        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        -1f
                    }
                    _uiState.value = UpdateCheckUiState.Downloading(
                        info = info,
                        progress = fraction,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                }
            }

            when (downloadResult) {
                is DownloadApkResult.Failure -> {
                    _uiState.value = UpdateCheckUiState.Error(
                        message = downloadResult.message,
                        isManual = true,
                        info = info
                    )
                }
                is DownloadApkResult.Success -> {
                    val compatibility = withContext(Dispatchers.IO) {
                        ApkInstaller.verifyApkCompatibility(
                            context = appContext,
                            apkFile = downloadResult.apkFile,
                            currentVersionCode = currentVersionCode.toLong()
                        )
                    }
                    when (compatibility) {
                        is ApkCompatibilityResult.Compatible -> {
                            launchOrRequestPermission(info, downloadResult.apkFile)
                        }
                        is ApkCompatibilityResult.IncompatibleSignature -> {
                            downloadResult.apkFile.delete()
                            _uiState.value = UpdateCheckUiState.Error(
                                message = compatibility.message,
                                isManual = true,
                                info = info
                            )
                        }
                        is ApkCompatibilityResult.Invalid -> {
                            downloadResult.apkFile.delete()
                            _uiState.value = UpdateCheckUiState.Error(
                                message = compatibility.message,
                                isManual = true,
                                info = info
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val current = _uiState.value
        if (current is UpdateCheckUiState.Downloading) {
            _uiState.value = UpdateCheckUiState.UpdateAvailable(
                info = current.info,
                showDialog = false
            )
        }
    }

    fun requestInstallPermissionFromSettings() {
        ApkInstaller.openInstallPermissionSettings(appContext)
    }

    suspend fun saveRepositoryOverride(rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            repository.updatePreferences { it.copy(updateRepoOverride = "") }
            return true
        }
        val normalized = UpdateVersionComparator.normalizeRepoSlug(trimmed) ?: return false
        repository.updatePreferences { it.copy(updateRepoOverride = normalized) }
        lastAutoCheckAtMillis = 0L
        dismissedVersionCodeThisSession = null
        return true
    }

    private fun launchOrRequestPermission(info: AppUpdateInfo, apkFile: File) {
        if (!ApkInstaller.canRequestPackageInstalls(appContext)) {
            _uiState.value = UpdateCheckUiState.ReadyToInstall(
                info = info,
                apkFilePath = apkFile.absolutePath,
                needsInstallPermission = true
            )
            return
        }

        val launched = ApkInstaller.launchInstaller(appContext, apkFile)
        _uiState.value = if (launched) {
            UpdateCheckUiState.ReadyToInstall(
                info = info,
                apkFilePath = apkFile.absolutePath,
                needsInstallPermission = false
            )
        } else {
            UpdateCheckUiState.Error(
                message = "Unable to launch Android installer for this update.",
                isManual = true,
                info = info
            )
        }
    }

    private suspend fun performCheck(isManual: Boolean) {
        if (checkMutex.isLocked && !isManual) return
        checkMutex.withLock {
            try {
                val prefs = withContext(Dispatchers.IO) { repository.getCurrentPreferences() }
                val repoSlug = resolveConfiguredRepo(prefs.updateRepoOverride)
                if (repoSlug.isBlank()) {
                    if (isManual) {
                        _uiState.value = UpdateCheckUiState.Error(
                            message = "GitHub repository is not configured yet. Tap Repository Source above to set owner/repo.",
                            isManual = true
                        )
                    }
                    return
                }

                if (!NetworkMonitor.isInternetAvailable(appContext)) {
                    _uiState.value = UpdateCheckUiState.Offline(isManual = isManual)
                    return
                }

                _uiState.value = UpdateCheckUiState.Checking(isManual = isManual)
                val result = withContext(Dispatchers.IO) {
                    client.fetchLatestRelease(repoSlug)
                }

                when (result) {
                    is FetchUpdateResult.Success -> {
                        lastAutoCheckAtMillis = System.currentTimeMillis()
                        val info = result.info
                        val hasNewer = UpdateVersionComparator.isRemoteVersionNewer(
                            currentVersionCode = currentVersionCode,
                            currentVersionName = currentVersionName,
                            remoteVersionCode = info.versionCode,
                            remoteVersionName = info.versionName
                        )
                        if (hasNewer) {
                            val showModal = isManual || dismissedVersionCodeThisSession != info.versionCode
                            _uiState.value = UpdateCheckUiState.UpdateAvailable(
                                info = info,
                                showDialog = showModal
                            )
                        } else {
                            _uiState.value = UpdateCheckUiState.UpToDate(
                                versionName = currentVersionName,
                                versionCode = currentVersionCode,
                                checkedAtMillis = System.currentTimeMillis()
                            )
                        }
                    }
                    is FetchUpdateResult.NoReleasesFound -> {
                        lastAutoCheckAtMillis = System.currentTimeMillis()
                        _uiState.value = UpdateCheckUiState.UpToDate(
                            versionName = currentVersionName,
                            versionCode = currentVersionCode,
                            checkedAtMillis = System.currentTimeMillis()
                        )
                    }
                    is FetchUpdateResult.Failure -> {
                        if (isManual) {
                            _uiState.value = UpdateCheckUiState.Error(
                                message = result.message,
                                isManual = true
                            )
                        } else {
                            // Never annoy or crash on background/automatic foreground checks
                            _uiState.value = UpdateCheckUiState.Idle
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                if (isManual) {
                    _uiState.value = UpdateCheckUiState.Error(
                        message = "Unable to check for updates right now.",
                        isManual = true
                    )
                } else {
                    _uiState.value = UpdateCheckUiState.Idle
                }
            }
        }
    }

    private companion object {
        const val AUTO_CHECK_COOLDOWN_MS = 10L * 60L * 1000L
    }
}

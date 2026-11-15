package com.example.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

sealed interface ApkCompatibilityResult {
    data object Compatible : ApkCompatibilityResult
    data class Invalid(val message: String) : ApkCompatibilityResult
    data class IncompatibleSignature(val message: String) : ApkCompatibilityResult
}

object ApkInstaller {

    private const val UPDATES_SUBDIR = "updates"

    fun getUpdatesDir(context: Context): File {
        return File(context.cacheDir, UPDATES_SUBDIR).apply { mkdirs() }
    }

    fun getTargetApkFile(context: Context, versionCode: Int, fileName: String): File {
        val safeName = fileName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            .ifBlank { "hydrate-buddy-update-$versionCode.apk" }
        return File(getUpdatesDir(context), safeName)
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun verifyApkCompatibility(
        context: Context,
        apkFile: File,
        currentVersionCode: Long
    ): ApkCompatibilityResult {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            return ApkCompatibilityResult.Invalid("Downloaded update APK file is missing or empty.")
        }

        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }
        }.getOrNull() ?: return ApkCompatibilityResult.Invalid(
            "Downloaded file is not a valid Android installation package."
        )

        if (archiveInfo.packageName != context.packageName) {
            return ApkCompatibilityResult.Invalid(
                "Package ID mismatch (${archiveInfo.packageName}). Update aborted for safety."
            )
        }

        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
        if (archiveVersionCode in 1..currentVersionCode) {
            return ApkCompatibilityResult.Invalid(
                "Downloaded APK (Build $archiveVersionCode) is not newer than your installed version (Build $currentVersionCode)."
            )
        }

        val archiveDigests = extractSigningDigests(archiveInfo)
        val installedInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flags)
            }
        }.getOrNull()
        val installedDigests = installedInfo?.let(::extractSigningDigests).orEmpty()

        if (archiveDigests.isNotEmpty() && installedDigests.isNotEmpty()) {
            val matches = archiveDigests.any { it in installedDigests }
            if (!matches) {
                return ApkCompatibilityResult.IncompatibleSignature(
                    "The update APK has a different signing certificate than your currently installed app. " +
                        "Android requires uninstalling the old build once before installing a release signed with a different key."
                )
            }
        }

        return ApkCompatibilityResult.Compatible
    }

    fun launchInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.isFile) return false
        return runCatching {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun cleanupStaleUpdates(context: Context, currentVersionCode: Long) {
        runCatching {
            val dir = File(context.cacheDir, UPDATES_SUBDIR)
            if (!dir.isDirectory) return
            val pm = context.packageManager
            val now = System.currentTimeMillis()
            dir.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                if (file.name.endsWith(".part")) {
                    file.delete()
                    return@forEach
                }
                if (file.name.endsWith(".apk", ignoreCase = true)) {
                    val archiveInfo = runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageArchiveInfo(
                                file.absolutePath,
                                PackageManager.PackageInfoFlags.of(0L)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageArchiveInfo(file.absolutePath, 0)
                        }
                    }.getOrNull()
                    val apkVersion = archiveInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: -1L
                    val isTooOld = (now - file.lastModified()) > 7L * 24 * 60 * 60 * 1000
                    if ((apkVersion in 1..currentVersionCode) || isTooOld) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun extractSigningDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageInfo.signingInfo
            info?.apkContentsSigners ?: info?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        } ?: return emptySet()

        val md = MessageDigest.getInstance("SHA-256")
        return signatures.mapNotNull { sig ->
            runCatching {
                md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }.toSet()
    }
}

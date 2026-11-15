package com.example.updater

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed interface FetchUpdateResult {
    data class Success(val info: AppUpdateInfo) : FetchUpdateResult
    data object NoReleasesFound : FetchUpdateResult
    data class Failure(val message: String) : FetchUpdateResult
}

sealed interface DownloadApkResult {
    data class Success(val apkFile: File) : DownloadApkResult
    data class Failure(val message: String) : DownloadApkResult
}

class GitHubUpdateClient(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun fetchLatestRelease(repoSlug: String): FetchUpdateResult {
        val metadataUrl = "https://github.com/$repoSlug/releases/latest/download/update-metadata.json"
        return try {
            val metadataResponse = executeGetString(metadataUrl)
            when (metadataResponse.code) {
                HttpURLConnection.HTTP_OK -> {
                    val meta = json.decodeFromString<GitHubUpdateMetadata>(metadataResponse.body)
                    if (meta.apkFileName.isBlank()) {
                        fetchViaGitHubApiFallback(repoSlug)
                    } else {
                        val tag = meta.tagName.trim()
                        val downloadUrl = if (tag.isNotEmpty()) {
                            "https://github.com/$repoSlug/releases/download/$tag/${meta.apkFileName}"
                        } else {
                            "https://github.com/$repoSlug/releases/latest/download/${meta.apkFileName}"
                        }
                        FetchUpdateResult.Success(
                            AppUpdateInfo(
                                versionCode = meta.versionCode,
                                versionName = meta.versionName.ifBlank { tag.removePrefix("v") },
                                tagName = tag.ifBlank { "v${meta.versionName}" },
                                downloadUrl = downloadUrl,
                                apkFileName = meta.apkFileName,
                                expectedSha256 = meta.apkSha256.trim().lowercase(),
                                sizeBytes = meta.apkSizeBytes,
                                releaseNotes = meta.releaseNotes.trim()
                            )
                        )
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> fetchViaGitHubApiFallback(repoSlug)
                else -> FetchUpdateResult.Failure("Unable to check updates (HTTP ${metadataResponse.code}).")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: IOException) {
            FetchUpdateResult.Failure("Network error while checking for updates.")
        } catch (_: Exception) {
            FetchUpdateResult.Failure("Unable to parse update information.")
        }
    }

    private suspend fun fetchViaGitHubApiFallback(repoSlug: String): FetchUpdateResult {
        val apiUrl = "https://api.github.com/repos/$repoSlug/releases/latest"
        val response = executeGetString(
            urlString = apiUrl,
            acceptHeader = "application/vnd.github+json"
        )
        return when (response.code) {
            HttpURLConnection.HTTP_OK -> {
                val release = json.decodeFromString<GitHubReleaseResponse>(response.body)
                if (release.draft) return FetchUpdateResult.NoReleasesFound

                val apkAssets = release.assets.filter { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true) &&
                        !asset.name.endsWith("-unsigned.apk", ignoreCase = true)
                }
                val chosenAsset = apkAssets.firstOrNull {
                    it.name.contains("release", ignoreCase = true)
                } ?: apkAssets.firstOrNull()
                    ?: return FetchUpdateResult.NoReleasesFound

                val versionName = release.tagName.trim().removePrefix("v").removePrefix("V")
                val versionCode = UpdateVersionComparator.extractVersionCodeFromNotes(release.body)
                FetchUpdateResult.Success(
                    AppUpdateInfo(
                        versionCode = versionCode,
                        versionName = versionName.ifBlank { release.name.ifBlank { "Latest" } },
                        tagName = release.tagName.trim(),
                        downloadUrl = chosenAsset.browserDownloadUrl,
                        apkFileName = chosenAsset.name,
                        expectedSha256 = "",
                        sizeBytes = chosenAsset.size,
                        releaseNotes = release.body.trim()
                    )
                )
            }
            HttpURLConnection.HTTP_NOT_FOUND -> FetchUpdateResult.NoReleasesFound
            403 -> FetchUpdateResult.Failure("Update server rate limit reached. Please try again later.")
            else -> FetchUpdateResult.Failure("Update server returned HTTP ${response.code}.")
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        targetFile: File,
        expectedSha256: String,
        expectedSizeBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): DownloadApkResult {
        targetFile.parentFile?.mkdirs()
        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
        if (partFile.exists()) partFile.delete()

        var connection: HttpURLConnection? = null
        return try {
            connection = openFollowingRedirects(downloadUrl, readTimeoutMs = 30_000)
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return DownloadApkResult.Failure("Download failed (HTTP $code).")
            }

            val headerLength = connection.contentLengthLong
            val totalBytes = when {
                headerLength > 0L -> headerLength
                expectedSizeBytes > 0L -> expectedSizeBytes
                else -> -1L
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastReported = 0L
            val buffer = ByteArray(16 * 1024)

            connection.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= 64 * 1024 || downloaded == totalBytes) {
                            lastReported = downloaded
                            onProgress(downloaded, totalBytes)
                        }
                    }
                    output.flush()
                }
            }
            onProgress(downloaded, totalBytes)

            if (downloaded <= 0L || !partFile.exists()) {
                partFile.delete()
                return DownloadApkResult.Failure("Downloaded update file is empty.")
            }

            if (expectedSha256.isNotBlank()) {
                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                    partFile.delete()
                    return DownloadApkResult.Failure("Update integrity check failed. Please try downloading again.")
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!partFile.renameTo(targetFile)) {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }

            DownloadApkResult.Success(targetFile)
        } catch (ce: CancellationException) {
            partFile.delete()
            throw ce
        } catch (_: IOException) {
            partFile.delete()
            DownloadApkResult.Failure("Connection lost while downloading update.")
        } catch (_: Exception) {
            partFile.delete()
            DownloadApkResult.Failure("Could not download the update package.")
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private data class HttpStringResponse(val code: Int, val body: String)

    private suspend fun executeGetString(
        urlString: String,
        acceptHeader: String = "application/json"
    ): HttpStringResponse {
        var connection: HttpURLConnection? = null
        return try {
            coroutineContext.ensureActive()
            connection = openFollowingRedirects(urlString, acceptHeader = acceptHeader, readTimeoutMs = 12_000)
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpStringResponse(code = code, body = body)
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun openFollowingRedirects(
        urlString: String,
        acceptHeader: String = "*/*",
        readTimeoutMs: Int = 15_000
    ): HttpURLConnection {
        var currentUrl = urlString
        repeat(6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                setRequestProperty("User-Agent", "HydrateBuddy-Android")
                setRequestProperty("Accept", acceptHeader)
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect without Location header")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return conn
            }
        }
        throw IOException("Too many HTTP redirects")
    }
}

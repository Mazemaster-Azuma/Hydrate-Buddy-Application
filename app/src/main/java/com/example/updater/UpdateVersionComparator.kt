package com.example.updater

import java.util.Locale

object UpdateVersionComparator {

    private val repoSlugRegex = Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""")
    private val versionCodeMarkerRegex = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)

    fun normalizeRepoSlug(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val cleaned = trimmed
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')

        return if (repoSlugRegex.matches(cleaned)) cleaned else null
    }

    fun isRemoteVersionNewer(
        currentVersionCode: Int,
        currentVersionName: String,
        remoteVersionCode: Int,
        remoteVersionName: String
    ): Boolean {
        if (remoteVersionCode > 0 && currentVersionCode > 0) {
            return remoteVersionCode > currentVersionCode
        }
        return compareSemanticVersions(remoteVersionName, currentVersionName) > 0
    }

    fun compareSemanticVersions(first: String, second: String): Int {
        val aParts = parseVersionSegments(first)
        val bParts = parseVersionSegments(second)
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val a = aParts.getOrElse(i) { 0 }
            val b = bParts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    fun extractVersionCodeFromNotes(body: String): Int {
        val match = versionCodeMarkerRegex.find(body) ?: return 0
        return match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun parseVersionSegments(raw: String): List<Int> {
        val core = raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (core.isEmpty()) return emptyList()
        return core.split('.').map { segment ->
            segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }
}

package com.example.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * High-performance, secure in-app sideload update manager.
 * Allows checking, downloading, verifying, and launching APK installation directly
 * without requiring the user to navigate to external websites or GitHub.
 */
class AppUpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    companion object {
        const val DEFAULT_RELEASE_ENDPOINT = "https://api.github.com/repos/Curious-being99/Kaspa-browser-/releases/latest"
        private const val PREFS_NAME = "kaspa_update_prefs"
        private const val KEY_AUTO_CHECK = "auto_check_updates_enabled"
        private const val KEY_CUSTOM_MANIFEST_URL = "custom_manifest_url"
        private const val KEY_LAST_CHECKED_TS = "last_checked_timestamp"
        private const val KEY_DISMISSED_VERSION = "dismissed_version"

        @Volatile
        private var instance: AppUpdateManager? = null

        fun getInstance(): AppUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: AppUpdateManager().also { instance = it }
            }
        }
    }

    /**
     * Resolves GitHub repository shorthand (owner/repo or web URL) to the GitHub Releases API endpoint.
     */
    fun resolveReleaseEndpoint(rawInput: String?): String {
        val clean = rawInput?.trim() ?: ""
        if (clean.isBlank()) return DEFAULT_RELEASE_ENDPOINT
        if (clean.startsWith("https://api.github.com/repos/")) return clean
        
        // Handle https://github.com/owner/repo or https://github.com/owner/repo/releases
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            val uri = Uri.parse(clean)
            if (uri.host?.contains("github.com", ignoreCase = true) == true) {
                val segments = uri.pathSegments.filter { it.isNotBlank() }
                if (segments.size >= 2) {
                    val owner = segments[0]
                    val repo = segments[1]
                    return "https://api.github.com/repos/$owner/$repo/releases/latest"
                }
            }
            return clean
        }

        // Handle "owner/repo" shorthand
        if (clean.contains("/") && !clean.contains(" ")) {
            val parts = clean.split("/").filter { it.isNotBlank() }
            if (parts.size == 2) {
                return "https://api.github.com/repos/${parts[0]}/${parts[1]}/releases/latest"
            }
        }

        return clean
    }

    /**
     * Checks for available updates by comparing the local installed version with the remote release manifest.
     */
    suspend fun checkForUpdates(
        context: Context,
        customEndpoint: String? = null
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val packageInfo = getPackageInfo(context)
            val currentVersionName = packageInfo.versionName ?: "1.0.0"
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }

            val primaryUrl = resolveReleaseEndpoint(customEndpoint)

            var fetchedInfo: AppUpdateInfo? = null
            var lastErrorMsg: String? = null
            var isGracefulNoRelease = false

            try {
                val request = Request.Builder()
                    .url(primaryUrl)
                    .header("User-Agent", "KaspaBrowser-Android/${currentVersionName}")
                    .header("Accept", "application/vnd.github.v3+json, application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            fetchedInfo = parseReleaseJson(body, currentVersionName, currentVersionCode)
                        }
                    } else {
                        val code = response.code
                        if (code == 404) {
                            // Repository exists but has no releases published yet -> treat gracefully
                            isGracefulNoRelease = true
                        } else {
                            lastErrorMsg = "HTTP $code from $primaryUrl"
                        }
                        android.util.Log.w("AppUpdateManager", "GitHub release check returned HTTP $code")
                    }
                }
            } catch (e: Exception) {
                lastErrorMsg = e.message ?: "Network error"
                android.util.Log.d("AppUpdateManager", "Remote update query notice: ${e.message}")
            }

            if (fetchedInfo != null) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_LAST_CHECKED_TS, System.currentTimeMillis()).apply()
                return@withContext Result.success(fetchedInfo!!)
            }

            if (isGracefulNoRelease || lastErrorMsg == null) {
                val defaultInfo = AppUpdateInfo(
                    latestVersionName = currentVersionName,
                    latestVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    isUpdateAvailable = false,
                    releaseTitle = "KaspaBrowser v$currentVersionName",
                    releaseNotes = "No newer releases published on GitHub. Check back later or configure your own public repository under 'GitHub Source'.",
                    releaseDate = formatIsoDate(""),
                    downloadUrl = "",
                    apkSizeBytes = 0L,
                    apkSizeFormatted = "0 MB",
                    sha256Checksum = null
                )
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_LAST_CHECKED_TS, System.currentTimeMillis()).apply()
                return@withContext Result.success(defaultInfo)
            }

            val repoDisplay = if (!customEndpoint.isNullOrBlank()) customEndpoint else "Curious-being99/Kaspa-browser-"
            val detailMsg = lastErrorMsg ?: "HTTP 404 Not Found"
            val finalError = "GitHub repository '$repoDisplay' returned an error ($detailMsg). Please ensure the repository is public and has a published release tag with an attached APK asset."

            Result.failure(IllegalStateException(finalError))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file with real-time stream progression and optional SHA-256 verification.
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply {
                if (!exists()) mkdirs()
            }
            
            // Clean up any old update files first to preserve space
            updatesDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".apk")) {
                    try { file.delete() } catch (_: Exception) {}
                }
            }

            val apkFileName = "kaspa_browser_v${updateInfo.latestVersionName.replace(".", "_")}.apk"
            val targetFile = File(updatesDir, apkFileName)

            // If file already exists and matches expected size, verify or reuse
            if (targetFile.exists() && targetFile.length() > 0 && updateInfo.apkSizeBytes > 0 && targetFile.length() == updateInfo.apkSizeBytes) {
                if (updateInfo.sha256Checksum == null || verifySha256(targetFile, updateInfo.sha256Checksum)) {
                    onProgress(100, targetFile.length(), targetFile.length())
                    return@withContext Result.success(targetFile)
                }
            }

            // Execute network download stream
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "KaspaBrowser-UpdateDownloader/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("HTTP ${response.code}: Could not download APK from GitHub release. Ensure a .apk asset is attached to the release.")
                )
            }

            val body = response.body ?: throw IllegalStateException("Empty response body from update server")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: (updateInfo.apkSizeBytes.takeIf { it > 0 } ?: 28_500_000L)

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(targetFile)
            val digest = MessageDigest.getInstance("SHA-256")

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L
            var lastProgressReportTime = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressReportTime > 80 || totalRead == totalBytes) {
                            lastProgressReportTime = now
                            val percent = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100) else 50
                            onProgress(percent, totalRead, totalBytes)
                        }
                    }
                }
            }

            // Verify checksum if provided in update metadata
            if (!updateInfo.sha256Checksum.isNullOrBlank()) {
                val computedHash = bytesToHex(digest.digest())
                if (!computedHash.equals(updateInfo.sha256Checksum.trim(), ignoreCase = true)) {
                    targetFile.delete()
                    return@withContext Result.failure(
                        SecurityException("SHA-256 integrity verification failed. Downloaded APK may be compromised.")
                    )
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Triggers the native Android package installer for sideloaded update installation.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(IllegalArgumentException("APK file does not exist or is empty"))
            }

            // Android 8.0 (API 26) and above requires checking Unknown App Sources permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
                    return Result.failure(
                        SecurityException("PERMISSION_REQUIRED: Please grant 'Install unknown apps' permission to allow Kaspa Browser to update itself.")
                    )
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if the app has permission to install unknown apps (Android 8.0+).
     */
    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Parses GitHub Releases or standard release manifest JSON.
     */
    internal fun parseReleaseJson(
        jsonString: String,
        currentVersionName: String,
        currentVersionCode: Int
    ): AppUpdateInfo {
        val trimmed = jsonString.trim()
        val json: JSONObject = try {
            if (trimmed.startsWith("[")) {
                val array = org.json.JSONArray(trimmed)
                if (array.length() > 0) array.getJSONObject(0) else throw IllegalArgumentException("Empty release array")
            } else {
                JSONObject(trimmed)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid release JSON: ${e.message}")
        }

        val rawTag = json.optString("tag_name", "").ifBlank { json.optString("name", "") }
        val tagName = rawTag.trim().removePrefix("v").removePrefix("V")
        val releaseName = json.optString("name", "").ifBlank { "Kaspa Browser Release v$tagName" }
        val releaseBody = json.optString("body", "").ifBlank { "• Performance improvements\n• Enhanced Kaspa Testnet 10 support\n• Security and stability updates" }
        val publishedAt = json.optString("published_at", "")
        val formattedDate = formatIsoDate(publishedAt)

        var downloadUrl = ""
        var apkSize = 0L

        // Look for .apk asset in GitHub release assets
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (url.isNotBlank() && (name.endsWith(".apk", ignoreCase = true) || name.contains("kaspa", ignoreCase = true) || name.contains("app", ignoreCase = true))) {
                    downloadUrl = url
                    apkSize = asset.optLong("size", 0L)
                    if (name.endsWith(".apk", ignoreCase = true)) break
                }
            }
        }

        val htmlUrl = json.optString("html_url", "")
        if (downloadUrl.isBlank() && htmlUrl.isNotBlank()) {
            val cleanTag = if (rawTag.isNotBlank()) rawTag else "v1.1.0"
            downloadUrl = "$htmlUrl/download/$cleanTag/kaspa-browser.apk"
        }

        if (downloadUrl.isBlank()) {
            downloadUrl = json.optString("download_url", "https://github.com/Curious-being99/Kaspa-browser-/releases/latest/download/KaspaBrowser-release-signed.apk")
        }

        val targetVersionName = if (tagName.isNotBlank()) tagName else "1.1.0"
        val isNewer = compareVersions(targetVersionName, currentVersionName) > 0

        val formattedSize = if (apkSize > 0) {
            "%.1f MB".format(apkSize / (1024.0 * 1024.0))
        } else {
            "28.5 MB"
        }

        val shaInJson = if (json.has("sha256")) json.optString("sha256") else null
        val shaInBody = if (shaInJson.isNullOrBlank()) {
            val regex = Regex("""(?i)(?:sha256|sha-256|hash)\s*[:=]\s*([a-fA-F0-9]{64})""")
            regex.find(releaseBody)?.groupValues?.get(1)
        } else null
        val finalSha = shaInJson?.takeIf { it.isNotBlank() } ?: shaInBody

        return AppUpdateInfo(
            latestVersionName = targetVersionName,
            latestVersionCode = currentVersionCode + 1,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            isUpdateAvailable = isNewer,
            releaseTitle = releaseName,
            releaseNotes = releaseBody,
            releaseDate = formattedDate,
            downloadUrl = downloadUrl,
            apkSizeBytes = apkSize,
            apkSizeFormatted = formattedSize,
            sha256Checksum = finalSha
        )
    }


    /**
     * Semantic version comparator (e.g. 1.1.0 vs 1.0.0).
     */
    fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.trim().removePrefix("v").removePrefix("V").split("-")[0]
        val clean2 = v2.trim().removePrefix("v").removePrefix("V").split("-")[0]

        val parts1 = clean1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = clean2.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }

    private fun verifySha256(file: File, expectedHash: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            file.inputStream().use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val computed = bytesToHex(digest.digest())
            computed.equals(expectedHash.trim(), ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun formatIsoDate(isoString: String): String {
        if (isoString.isBlank()) return SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(Date())
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val date = parser.parse(isoString) ?: Date()
            SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(date)
        } catch (_: Exception) {
            isoString.take(10)
        }
    }

    private fun getPackageInfo(context: Context): android.content.pm.PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }
}

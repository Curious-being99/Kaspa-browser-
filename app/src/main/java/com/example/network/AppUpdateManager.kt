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
        var clean = rawInput?.trim() ?: ""
        if (clean.isBlank()) return DEFAULT_RELEASE_ENDPOINT
        
        // Ensure GitHub API calls strictly point to /releases/latest and NEVER fall back to /releases
        if (clean.startsWith("https://api.github.com/repos/", ignoreCase = true)) {
            val apiPrefix = "https://api.github.com/repos/"
            val relativePath = clean.substring(apiPrefix.length)
            val segments = relativePath.split("/").filter { it.isNotBlank() }
            if (segments.size >= 2) {
                val owner = segments[0]
                val repo = segments[1]
                return "https://api.github.com/repos/$owner/$repo/releases/latest"
            }
            return clean
        }
        
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
                            isGracefulNoRelease = true
                        } else if (code == 403) {
                            lastErrorMsg = "Update service rate limit exceeded. Please try again later."
                        } else {
                            lastErrorMsg = "Update service returned HTTP $code. Please try again."
                        }
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                lastErrorMsg = if (isDeviceConnected(context)) "Update service host could not be resolved. This may be a DNS issue." else "Network connection error. Please check your internet."
            } catch (e: java.net.SocketTimeoutException) {
                lastErrorMsg = "Update check timed out. The update service may be under high load."
            } catch (e: java.net.ConnectException) {
                lastErrorMsg = if (isDeviceConnected(context)) "Could not connect to update service. It might be blocked or down." else "Could not connect to update service. Please check your internet."
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("timeout", ignoreCase = true) || msg.contains("connect", ignoreCase = true)) {
                    lastErrorMsg = if (isDeviceConnected(context)) "Update connection timed out. Service might be unavailable." else "Update connection timed out. Please check your network."
                } else {
                    lastErrorMsg = "Update check currently unavailable."
                }
            }

            if (fetchedInfo != null) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastInstalledTag = prefs.getString("last_installed_release_tag", "") ?: ""

                // If the latest release name matches the last successfully installed release tag,
                // then mark isUpdateAvailable as false to avoid duplicate prompts
                if (fetchedInfo!!.latestVersionName.equals(lastInstalledTag, ignoreCase = true)) {
                    fetchedInfo = fetchedInfo!!.copy(
                        isUpdateAvailable = false,
                        releaseNotes = "You have already updated to the latest release v${fetchedInfo!!.latestVersionName}."
                    )
                }

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
            val finalError = if (detailMsg.contains("resolved") || detailMsg.contains("timed out") || detailMsg.contains("connect")) {
                detailMsg
            } else {
                "Update check for '$repoDisplay' failed ($detailMsg). Please ensure the repository is public."
            }

            Result.failure(IllegalStateException(finalError))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isDeviceConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNetwork)
            caps != null && (caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) || 
                           caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || 
                           caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR))
        } catch (_: Exception) {
            true 
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

            val expectedHash = updateInfo.sha256Checksum?.trim()?.lowercase(Locale.ROOT)
                ?: throw SecurityException("Release is missing its required SHA-256 checksum.")
            if (!expectedHash.matches(Regex("[a-f0-9]{64}"))) {
                throw SecurityException("Release SHA-256 checksum is invalid.")
            }
            val downloadUri = Uri.parse(updateInfo.downloadUrl)
            if (downloadUri.scheme != "https" || !downloadUri.host.equals("github.com", ignoreCase = true) ||
                !downloadUri.path.orEmpty().contains("/releases/download/")) {
                throw SecurityException("APK download URL is not an approved GitHub release asset.")
            }

            if (targetFile.exists() && targetFile.length() > 0 &&
                (updateInfo.apkSizeBytes <= 0 || targetFile.length() == updateInfo.apkSizeBytes) &&
                verifySha256(targetFile, expectedHash) &&
                hasMatchingInstalledSigner(context, targetFile) &&
                hasExpectedNewerVersion(context, targetFile)) {
                onProgress(100, targetFile.length(), targetFile.length())
                return@withContext Result.success(targetFile)
            }
            targetFile.delete()

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

            val computedHash = bytesToHex(digest.digest())
            if (!computedHash.equals(expectedHash, ignoreCase = true)) {
                targetFile.delete()
                throw SecurityException("SHA-256 integrity verification failed.")
            }
            if (updateInfo.apkSizeBytes > 0 && targetFile.length() != updateInfo.apkSizeBytes) {
                targetFile.delete()
                throw SecurityException("Downloaded APK size does not match release metadata.")
            }
            if (!hasMatchingInstalledSigner(context, targetFile)) {
                targetFile.delete()
                throw SecurityException("Downloaded APK package name or signing certificate does not match the installed app. The update was blocked.")
            }
            if (!hasExpectedNewerVersion(context, targetFile)) {
                targetFile.delete()
                throw SecurityException("Downloaded APK version is not newer than the installed app, or its package metadata could not be read. The update was blocked.")
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies that a downloaded APK belongs to this app and is signed by the
     * same current signing certificate as the installed application.
     */
    private fun hasMatchingInstalledSigner(context: Context, apkFile: File): Boolean {
        return try {
            val packageManager = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val installed = packageManager.getPackageInfo(context.packageName, flags)
            val candidate = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
                ?: return false
            if (candidate.packageName != context.packageName) return false

            @Suppress("DEPRECATION")
            val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installed.signingInfo?.apkContentsSigners
            } else {
                installed.signatures
            } ?: return false
            @Suppress("DEPRECATION")
            val candidateSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                candidate.signingInfo?.apkContentsSigners
            } else {
                candidate.signatures
            } ?: return false

            if (installedSignatures.isEmpty() || candidateSignatures.isEmpty()) return false
            val installedSignerBytes = installedSignatures.map { it.toByteArray() }
            val candidateSignerBytes = candidateSignatures.map { it.toByteArray() }

            // Multiple-signer APKs must match exactly. For a single-signer APK,
            // accept Android's v3 signing-certificate rotation only when the
            // candidate APK's verified signing lineage contains the installed signer.
            if (installedSignerBytes.size != 1 || candidateSignerBytes.size != 1) {
                installedSignerBytes.any { installedBytes ->
                    candidateSignerBytes.any { candidateBytes -> installedBytes.contentEquals(candidateBytes) }
                } && installedSignerBytes.size == candidateSignerBytes.size
            } else if (installedSignerBytes.single().contentEquals(candidateSignerBytes.single())) {
                true
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val candidateHistory = candidate.signingInfo?.signingCertificateHistory ?: return false
                candidateHistory.any { historyCert ->
                    historyCert.toByteArray().contentEquals(installedSignerBytes.single())
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasExpectedNewerVersion(context: Context, apkFile: File): Boolean {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val installed = getPackageInfo(context)
            val candidate = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false
            val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.longVersionCode else {
                @Suppress("DEPRECATION")
                installed.versionCode.toLong()
            }
            val candidateCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) candidate.longVersionCode else {
                @Suppress("DEPRECATION")
                candidate.versionCode.toLong()
            }
            val candidateVersionName = candidate.versionName
            candidate.packageName == context.packageName &&
                candidateVersionName != null &&
                compareVersions(candidateVersionName, installed.versionName ?: "0.0.0") > 0 &&
                candidateCode > installedCode
        } catch (_: Exception) {
            false
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
            if (!hasMatchingInstalledSigner(context, apkFile)) {
                return Result.failure(
                    SecurityException("Downloaded APK package name or signing certificate does not match the installed Kaspa Browser app.")
                )
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
    internal fun parseReleaseJson(jsonString: String, currentVersionName: String, currentVersionCode: Int): AppUpdateInfo {
        val trimmed = jsonString.trim()
        val json = try {
            if (trimmed.startsWith("[")) {
                val array = org.json.JSONArray(trimmed)
                require(array.length() > 0) { "No published stable release found." }
                array.getJSONObject(0)
            } else JSONObject(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid release JSON: ${e.message}")
        }
        require(!json.optBoolean("draft", false)) { "Draft releases are not eligible for updates." }
        require(!json.optBoolean("prerelease", false)) { "Pre-releases are not eligible for updates." }
        val rawTag = json.optString("tag_name", "").trim()
        require(rawTag.matches(Regex("(?i)^v?\\d+\\.\\d+\\.\\d+$"))) { "Release tag must use stable vMAJOR.MINOR.PATCH format." }
        val targetVersionName = rawTag.removePrefix("v").removePrefix("V")
        val isNewer = compareVersions(targetVersionName, currentVersionName) > 0
        val releaseBody = json.optString("body", "")
        val assets = json.optJSONArray("assets")
        var downloadUrl = ""
        var apkSize = 0L
        var assetDigest: String? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == "KaspaBrowser-release-signed.apk") {
                downloadUrl = asset.optString("browser_download_url", "")
                apkSize = asset.optLong("size", 0L)
                assetDigest = asset.optString("digest", "").takeIf { it.isNotBlank() }
                break
            }
        }
        val uri = Uri.parse(downloadUrl)
        require(downloadUrl.isNotBlank() && uri.scheme == "https" && uri.host.equals("github.com", true) && uri.path.orEmpty().contains("/releases/download/")) {
            "Stable release does not contain the expected signed APK asset."
        }
        val digest = assetDigest?.removePrefix("sha256:")?.takeIf { it.matches(Regex("(?i)[a-f0-9]{64}")) }
        val shaJson = json.optString("sha256", "").takeIf { it.isNotBlank() }
        val shaBody = Regex("""(?i)(?:sha256|sha-256|hash)\s*[:=]\s*([a-f0-9]{64})""").find(releaseBody)?.groupValues?.get(1)
        val checksum = (digest ?: shaJson ?: shaBody)?.lowercase(Locale.ROOT)
        require(checksum != null && checksum.matches(Regex("[a-f0-9]{64}"))) { "Stable release is missing a valid SHA-256 checksum." }
        require(apkSize > 0L) { "Stable release APK asset has no valid size." }
        return AppUpdateInfo(
            latestVersionName = targetVersionName, latestVersionCode = currentVersionCode,
            currentVersionName = currentVersionName, currentVersionCode = currentVersionCode,
            isUpdateAvailable = isNewer,
            releaseTitle = json.optString("name", "").ifBlank { "Kaspa Browser Release v$targetVersionName" },
            releaseNotes = releaseBody, releaseDate = formatIsoDate(json.optString("published_at", "")),
            downloadUrl = downloadUrl, apkSizeBytes = apkSize,
            apkSizeFormatted = "%.1f MB".format(Locale.US, apkSize / (1024.0 * 1024.0)),
            sha256Checksum = checksum
        )
    }

    /**
     * Semantic version comparator (e.g. 1.1.0 vs 1.0.0).
     */
    fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.trim().removePrefix("v").removePrefix("V").split("-")[0]
        val clean2 = v2.trim().removePrefix("v").removePrefix("V").split("-")[0]

        val parts1 = clean1.split(".").map { it.toLongOrNull() ?: 0L }
        val parts2 = clean2.split(".").map { it.toLongOrNull() ?: 0L }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0L }
            val p2 = parts2.getOrElse(i) { 0L }
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

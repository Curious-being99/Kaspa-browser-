package com.example.network

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class InstalledPwa(
    val id: String,
    val name: String,
    val url: String,
    val iconUrl: String? = null,
    val manifestUrl: String? = null,
    val hasManifest: Boolean = false,
    val installedAt: Long = System.currentTimeMillis()
)

object PwaShortcutHelper {

    /**
     * Installs an APK file from downloads using Android PackageInstaller / ACTION_VIEW.
     */
    fun installApk(context: Context, downloadId: Long, fileName: String): Pair<Boolean, String> {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            var uri: Uri? = null

            // 1. Try dm.getUriForDownloadedFile
            if (dm != null && downloadId > 0) {
                try {
                    uri = dm.getUriForDownloadedFile(downloadId)
                } catch (_: Exception) {}
            }

            // 2. Fallback to physical file in public Downloads directory
            if (uri == null && fileName.isNotBlank()) {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val directFile = File(publicDownloads, fileName)
                if (directFile.exists()) {
                    val authority = "${context.packageName}.fileprovider"
                    uri = FileProvider.getUriForFile(context, authority, directFile)
                }
            }

            // 3. Fallback to app's external files directory
            if (uri == null && fileName.isNotBlank()) {
                val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (appDownloads != null) {
                    val appFile = File(appDownloads, fileName)
                    if (appFile.exists()) {
                        val authority = "${context.packageName}.fileprovider"
                        uri = FileProvider.getUriForFile(context, authority, appFile)
                    }
                }
            }

            if (uri == null) {
                return Pair(false, "APK file not found on disk yet. Please wait for download to finish.")
            }

            // Android O+ Unknown Sources Permission Check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    Toast.makeText(
                        context,
                        "Please allow Kaspa Browser to install unknown apps, then tap Install again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return Pair(false, "Permission required: Allow installing unknown apps.")
                }
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            return Pair(true, "Launching Package Installer for $fileName...")
        } catch (e: Exception) {
            return Pair(false, "Install failed: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Installs a PWA to the Android device.
     * PWA ONLY works for websites that support PWA (manifest / web app metadata).
     * Downloads the actual website webpage PWA logo from the network.
     * NO custom logo is used.
     */
    suspend fun installWebsitePwa(
        context: Context,
        url: String,
        fallbackTitle: String = "",
        manifestUrl: String? = null,
        iconUrl: String? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("kas://")) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Cannot install PWA: Invalid web URL", Toast.LENGTH_SHORT).show()
            }
            return@withContext Pair(false, "Cannot install PWA: Invalid web URL")
        }

        // Fetch manifest and download the official website webpage PWA logo
        val pwaInfo = fetchAndDownloadWebsitePwa(url, manifestUrl, iconUrl)
        val logoBitmap = pwaInfo.first
        if (logoBitmap == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Website does not support PWA (no PWA manifest or webpage logo found)",
                    Toast.LENGTH_LONG
                ).show()
            }
            return@withContext Pair(false, "Website does not support PWA (no PWA manifest or webpage logo found)")
        }

        val appTitle = pwaInfo.second?.ifBlank { null } 
            ?: fallbackTitle.ifBlank { null } 
            ?: Uri.parse(url).host?.removePrefix("www.")?.replaceFirstChar { it.uppercase() } 
            ?: "Web App"

        // Pin to device using the downloaded webpage PWA logo (strictly NO custom logo)
        withContext(Dispatchers.Main) {
            installPwaToDeviceWithWebLogo(context, appTitle, url, logoBitmap)
        }
    }

    /**
     * Pins the PWA shortcut to the device home screen using the downloaded webpage PWA logo.
     */
    fun installPwaToDeviceWithWebLogo(
        context: Context,
        title: String,
        url: String,
        webpageLogoBitmap: Bitmap
    ): Pair<Boolean, String> {
        try {
            val cleanTitle = title.trim().ifBlank { "Web App" }
            val pwaId = "pwa_${url.hashCode()}"
            val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(url)
                putExtra("PWA_URL", url)
                putExtra("IS_PWA_MODE", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val iconCompat = IconCompat.createWithBitmap(webpageLogoBitmap)
            val shortLabel = if (cleanTitle.length > 18) cleanTitle.take(17) + "…" else cleanTitle

            val shortcutInfo = ShortcutInfoCompat.Builder(context, pwaId)
                .setShortLabel(shortLabel)
                .setLongLabel(cleanTitle)
                .setIcon(iconCompat)
                .setIntent(shortcutIntent)
                .build()

            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                val pinned = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
                if (pinned) {
                    Toast.makeText(context, "PWA \"$cleanTitle\" installed with website logo!", Toast.LENGTH_SHORT).show()
                    return Pair(true, "PWA \"$cleanTitle\" installed with website logo!")
                }
            }

            // Fallback for older launcher broadcast
            @Suppress("DEPRECATION")
            val legacyIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, shortLabel)
                putExtra(Intent.EXTRA_SHORTCUT_ICON, webpageLogoBitmap)
                putExtra("duplicate", false)
            }
            context.sendBroadcast(legacyIntent)
            Toast.makeText(context, "PWA shortcut requested with website logo", Toast.LENGTH_SHORT).show()
            return Pair(true, "PWA shortcut created with website logo")
        } catch (e: Exception) {
            return Pair(false, "Failed to install PWA: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Inspects webpage PWA support and downloads the actual webpage PWA logo.
     * Returns Pair(Bitmap?, PwaTitle?) if website supports PWA; otherwise returns Pair(null, null).
     */
    suspend fun fetchAndDownloadWebsitePwa(
        pageUrl: String,
        manifestUrl: String?,
        iconUrl: String?
    ): Pair<Bitmap?, String?> = withContext(Dispatchers.IO) {
        var foundIconUrl: String? = null
        var pwaTitle: String? = null
        var isPwaSupported = false

        // 1. Discover or resolve manifest URL
        val targetManifestUrl = manifestUrl?.ifBlank { null } ?: discoverManifestUrl(pageUrl)

        if (!targetManifestUrl.isNullOrBlank()) {
            try {
                val resolvedManifestUrl = resolveAbsoluteUrl(pageUrl, targetManifestUrl)
                val conn = (URL(resolvedManifestUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    pwaTitle = json.optString("short_name", "").ifBlank { json.optString("name", "") }
                    val iconsArray = json.optJSONArray("icons")
                    if (iconsArray != null && iconsArray.length() > 0) {
                        isPwaSupported = true
                        var maxDim = -1
                        var bestSrc = ""
                        for (i in 0 until iconsArray.length()) {
                            val iconObj = iconsArray.optJSONObject(i) ?: continue
                            val src = iconObj.optString("src", "")
                            val sizes = iconObj.optString("sizes", "")
                            val dim = parseMaxDimension(sizes)
                            if (dim > maxDim && src.isNotBlank()) {
                                maxDim = dim
                                bestSrc = src
                            }
                        }
                        if (bestSrc.isNotBlank()) {
                            foundIconUrl = resolveAbsoluteUrl(resolvedManifestUrl, bestSrc)
                        }
                    } else if (pwaTitle?.isNotBlank() == true) {
                        isPwaSupported = true
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback to iconUrl if manifest did not specify an icon or as secondary source
        if (foundIconUrl.isNullOrBlank() && !iconUrl.isNullOrBlank()) {
            foundIconUrl = resolveAbsoluteUrl(pageUrl, iconUrl)
        }

        // If website has no manifest and no PWA support indication, reject
        if (targetManifestUrl.isNullOrBlank() && !isPwaSupported && iconUrl.isNullOrBlank()) {
            return@withContext Pair(null, null)
        }

        // 3. Download the actual webpage PWA logo from foundIconUrl
        if (!foundIconUrl.isNullOrBlank()) {
            try {
                val conn = (URL(foundIconUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                }
                if (conn.responseCode in 200..299) {
                    val rawBitmap = BitmapFactory.decodeStream(conn.inputStream)
                    if (rawBitmap != null) {
                        // Standardize to 192x192 preserving exact downloaded website logo
                        val targetSize = 192
                        val scaledBitmap = if (rawBitmap.width != targetSize || rawBitmap.height != targetSize) {
                            Bitmap.createScaledBitmap(rawBitmap, targetSize, targetSize, true)
                        } else {
                            rawBitmap
                        }
                        return@withContext Pair(scaledBitmap, pwaTitle)
                    }
                }
            } catch (_: Exception) {}
        }

        Pair(null, null)
    }

    private fun discoverManifestUrl(pageUrl: String): String? {
        if (!pageUrl.startsWith("http://") && !pageUrl.startsWith("https://")) return null
        return try {
            val conn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
            }
            if (conn.responseCode in 200..299) {
                val html = conn.inputStream.bufferedReader().use { it.readLines().take(80).joinToString("\n") }
                val manifestRegex = Regex("""<link[^>]+rel=["']manifest["'][^>]+href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val match = manifestRegex.find(html) 
                    ?: Regex("""<link[^>]+href=["']([^"']+)["'][^>]+rel=["']manifest["']""", RegexOption.IGNORE_CASE).find(html)
                match?.groupValues?.get(1)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun resolveAbsoluteUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(relativeOrAbsolute).toString()
        } catch (_: Exception) {
            if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
                relativeOrAbsolute
            } else {
                val cleanBase = baseUrl.trimEnd('/')
                val cleanRel = relativeOrAbsolute.trimStart('/')
                "$cleanBase/$cleanRel"
            }
        }
    }

    private fun parseMaxDimension(sizes: String): Int {
        return try {
            val parts = sizes.split("x", "X")
            if (parts.size == 2) parts[0].trim().toIntOrNull() ?: 0 else 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Creates a home screen shortcut for a downloaded file or origin URL.
     */
    fun createDownloadShortcut(
        context: Context,
        fileName: String,
        url: String,
        downloadId: Long
    ): Pair<Boolean, String> {
        try {
            val title = if (fileName.isNotBlank()) fileName else "Download"
            val shortLabel = if (title.length > 18) title.take(17) + "…" else title
            val shortcutId = "dl_${downloadId}_${fileName.hashCode()}"

            val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                if (url.isNotBlank()) {
                    data = Uri.parse(url)
                    putExtra("PWA_URL", url)
                }
                putExtra("DOWNLOAD_FILENAME", fileName)
                putExtra("DOWNLOAD_ID", downloadId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val isApk = fileName.endsWith(".apk", ignoreCase = true)
            val badgeColor = if (isApk) Color.parseColor("#00E5FF") else Color.parseColor("#10B981")
            val iconBitmap = generateCustomBadgeBitmap(shortLabel, badgeColor)
            val iconCompat = IconCompat.createWithBitmap(iconBitmap)

            val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(shortLabel)
                .setLongLabel(title)
                .setIcon(iconCompat)
                .setIntent(shortcutIntent)
                .build()

            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
                Toast.makeText(context, "Shortcut created for \"$shortLabel\"", Toast.LENGTH_SHORT).show()
                return Pair(true, "Shortcut added to home screen!")
            } else {
                @Suppress("DEPRECATION")
                val legacyIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                    putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                    putExtra(Intent.EXTRA_SHORTCUT_NAME, shortLabel)
                    putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap)
                    putExtra("duplicate", false)
                }
                context.sendBroadcast(legacyIntent)
                Toast.makeText(context, "Shortcut sent to launcher", Toast.LENGTH_SHORT).show()
                return Pair(true, "Shortcut added to home screen!")
            }
        } catch (e: Exception) {
            return Pair(false, "Failed to create shortcut: ${e.localizedMessage ?: e.message}")
        }
    }

    fun generateCustomBadgeBitmap(label: String, accentColor: Int): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0D121D")
            style = Paint.Style.FILL
        }
        val rect = RectF(6f, 6f, size - 6f, size - 6f)
        canvas.drawRoundRect(rect, 36f, 36f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRoundRect(rect, 36f, 36f, borderPaint)

        val letter = label.trim().firstOrNull()?.uppercase() ?: "D"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 80f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(letter, canvas.width / 2f, yPos, textPaint)

        return bitmap
    }
}

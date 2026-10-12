package com.example.ui

import android.content.Context
import android.webkit.WebStorage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Reports storage categories separately. Cache cleanup intentionally never touches
 * Room databases, history, bookmarks, tabs, cookies, or Web Storage/site data.
 */
@Composable
fun BrowserStorageDiagnosticsCard(context: Context) {
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var webViewCacheBytes by remember { mutableLongStateOf(0L) }
    var databaseBytes by remember { mutableLongStateOf(0L) }
    var siteStorageBytes by remember { mutableLongStateOf(0L) }
    var siteOriginCount by remember { mutableLongStateOf(0L) }
    var measuring by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun measure() {
        if (measuring) return
        measuring = true
        scope.launch {
            try {
                val sizes = withContext(Dispatchers.IO) {
                    listOf(
                        directorySize(context.cacheDir) + (context.externalCacheDir?.let(::directorySize) ?: 0L),
                        webViewCacheSize(context),
                        directorySize(context.getDatabasePath("placeholder").parentFile),
                        webViewSiteStorageSize(context)
                    )
                }
                cacheBytes = sizes[0]
                webViewCacheBytes = sizes[1]
                databaseBytes = sizes[2]
                siteStorageBytes = sizes[3]
            } catch (_: Throwable) {
            } finally {
                measuring = false
            }

            // Asynchronously query WebStorage origins to enrich storage diagnostics
            try {
                WebStorage.getInstance().getOrigins { origins ->
                    if (origins != null) {
                        siteOriginCount = origins.size.toLong()
                        var totalWebUsage = 0L
                        origins.values.forEach { originObj ->
                            (originObj as? WebStorage.Origin)?.let { orig ->
                                totalWebUsage += orig.usage
                            }
                        }
                        if (totalWebUsage > 0L) {
                            siteStorageBytes = maxOf(siteStorageBytes, totalWebUsage)
                        }
                    } else {
                        siteOriginCount = 0L
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(context) { measure() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("browser_storage_diagnostics_card"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(ElectricCyan.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Storage",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Storage Diagnostics",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Estimated app & disk cache usage",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                if (measuring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ElectricCyan
                    )
                }
            }

            Text(
                text = "Measurements are calculated directly from on-disk app and WebView storage directories. Cache cleanup clears temporary files while preserving website logins, tabs, and wallet keys.",
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = TextSecondary
            )

            HorizontalDivider(
                color = SurfaceCardBorder.copy(alpha = 0.6f),
                thickness = 1.dp
            )

            // Storage Metrics List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StorageLine("App & external cache", cacheBytes)
                StorageLine("WebView disk cache", webViewCacheBytes)
                StorageLine("Database & user records", databaseBytes)
                StorageLine(
                    if (siteOriginCount > 0) "Website storage ($siteOriginCount origins)" else "Website storage (HTML5 / PWA / IndexedDB)",
                    siteStorageBytes,
                    statusHint = if (siteStorageBytes == 0L && siteOriginCount == 0L) "Available · Clean" else "Available · Preserved"
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Action Buttons moved upward closer to metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { measure(); status = null },
                    enabled = !measuring,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (!measuring) ElectricCyan else TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Refresh",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (!measuring) TextPrimary else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KaspaTea,
                        contentColor = SurfaceDark
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = SurfaceDark
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clear Cache",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            status?.let { infoText ->
                val isSuccess = infoText.contains("freed", ignoreCase = true)
                Surface(
                    color = if (isSuccess) EmeraldMesh.copy(alpha = 0.12f) else SurfaceCard,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (isSuccess) EmeraldMesh.copy(alpha = 0.4f) else SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = infoText,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = if (isSuccess) EmeraldMesh else TextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "Clear cached files?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "This removes app cache and known WebView disk-cache folders only. It does not delete history, bookmarks, tabs, wallet records, cookies, or website storage. Some pages may reload or download resources again.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { clearCacheOnly(context) }
                            status = "Cache cleanup freed ${formatBytes(result)}. Website storage and user records were preserved."
                            measure()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KaspaTea,
                        contentColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Clear Cache", fontWeight = FontWeight.Bold, color = SurfaceDark)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun StorageLine(label: String, bytes: Long, statusHint: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatBytes(bytes),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary
            )
            if (statusHint != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusHint,
                    fontSize = 10.5.sp,
                    color = EmeraldMesh
                )
            }
        }
    }
}

private fun getWebViewDataDirs(context: Context): List<File> {
    val dirs = mutableListOf<File>()
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.dataDir?.let { dirs.add(File(it, "app_webview")) }
        }
    } catch (_: Throwable) {}
    try {
        context.applicationInfo?.dataDir?.let { dirs.add(File(it, "app_webview")) }
    } catch (_: Throwable) {}
    try {
        context.filesDir?.parentFile?.let { dirs.add(File(it, "app_webview")) }
    } catch (_: Throwable) {}
    return dirs.distinctBy { it.absolutePath }
}

private fun webViewCacheSize(context: Context): Long {
    val roots = getWebViewDataDirs(context)
    val subPaths = listOf(
        "Default/Cache",
        "Default/Code Cache",
        "Default/GPUCache",
        "Cache",
        "Code Cache",
        "GPUCache"
    )
    var totalSize = 0L
    for (root in roots) {
        if (!root.exists()) continue
        for (sub in subPaths) {
            val f = File(root, sub)
            if (f.exists()) {
                totalSize += directorySize(f)
            }
        }
    }
    return totalSize
}

private fun webViewSiteStorageSize(context: Context): Long {
    val roots = getWebViewDataDirs(context)
    val subPaths = listOf(
        "Default/Local Storage",
        "Default/IndexedDB",
        "Default/Service Worker",
        "Default/databases",
        "Default/File System",
        "Default/Cookies",
        "Default/blob_storage",
        "Default/WebStorage",
        "Default/Storage",
        "Local Storage",
        "IndexedDB",
        "Service Worker",
        "databases",
        "File System",
        "Cookies",
        "blob_storage",
        "WebStorage",
        "Storage"
    )
    var totalSize = 0L
    for (root in roots) {
        if (!root.exists()) continue
        for (sub in subPaths) {
            val f = File(root, sub)
            if (f.exists()) {
                totalSize += directorySize(f)
            }
        }
    }
    return totalSize
}

private fun clearCacheOnly(context: Context): Long {
    var before = directorySize(context.cacheDir) + (context.externalCacheDir?.let(::directorySize) ?: 0L)
    val roots = getWebViewDataDirs(context)
    val cacheSubPaths = listOf(
        "Default/Cache",
        "Default/Code Cache",
        "Default/GPUCache",
        "Cache",
        "Code Cache",
        "GPUCache"
    )
    val cacheFolders = mutableListOf<File>()
    for (root in roots) {
        if (!root.exists()) continue
        for (sub in cacheSubPaths) {
            val f = File(root, sub)
            if (f.exists()) {
                cacheFolders.add(f)
            }
        }
    }
    cacheFolders.forEach { before += directorySize(it) }
    clearDirectoryContents(context.cacheDir)
    context.externalCacheDir?.let(::clearDirectoryContents)
    cacheFolders.forEach(::clearDirectoryContents)
    val after = directorySize(context.cacheDir) +
        (context.externalCacheDir?.let(::directorySize) ?: 0L) +
        cacheFolders.sumOf(::directorySize)
    return (before - after).coerceAtLeast(0L)
}

private fun clearDirectoryContents(directory: File?) {
    if (directory == null || !directory.exists() || !directory.isDirectory) return
    directory.listFiles()?.forEach { child ->
        try {
            if (child.isDirectory) child.deleteRecursively() else child.delete()
        } catch (_: SecurityException) {
            // Best effort: retain inaccessible cache entries and continue.
        }
    }
}

private fun directorySize(file: File?): Long {
    if (file == null || !file.exists()) return 0L
    if (file.isFile) return file.length()
    return try {
        file.listFiles()?.sumOf(::directorySize) ?: 0L
    } catch (_: SecurityException) {
        0L
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024.0
        unit++
    } while (value >= 1024.0 && unit < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

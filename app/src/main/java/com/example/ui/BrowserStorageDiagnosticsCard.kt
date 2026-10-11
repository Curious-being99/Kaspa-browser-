package com.example.ui

import android.content.Context
import android.webkit.WebStorage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            val sizes = withContext(Dispatchers.IO) {
                Triple(
                    directorySize(context.cacheDir) + (context.externalCacheDir?.let(::directorySize) ?: 0L),
                    webViewCacheSize(context),
                    directorySize(context.getDatabasePath("placeholder").parentFile)
                )
            }
            cacheBytes = sizes.first
            webViewCacheBytes = sizes.second
            databaseBytes = sizes.third
            try {
                WebStorage.getInstance().getOrigins { origins ->
                    siteStorageBytes = origins.values.sumOf { origin -> origin.getUsage().coerceAtLeast(0L) }
                    siteOriginCount = origins.size.toLong()
                    measuring = false
                }
            } catch (_: Exception) {
                status = "Web Storage usage is unavailable on this WebView version."
                measuring = false
            }
        }
    }

    LaunchedEffect(context) { measure() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Storage usage", style = MaterialTheme.typography.titleMedium)
            Text(
                "Measurements are estimates from accessible app directories and WebView origin usage; Android's overall “User data” figure may include additional system-managed data.",
                style = MaterialTheme.typography.bodySmall
            )
            StorageLine("App cache + external cache", cacheBytes)
            StorageLine("WebView disk cache", webViewCacheBytes)
            StorageLine("App database files (includes user records)", databaseBytes)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Website storage (${siteOriginCount} origins)", style = MaterialTheme.typography.bodyMedium)
                Text(formatBytes(siteStorageBytes), style = MaterialTheme.typography.bodyMedium)
            }
            if (measuring) Text("Measuring site storage…", style = MaterialTheme.typography.bodySmall)
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { measure(); status = null }, enabled = !measuring) {
                    Text("Refresh measurements")
                }
                Button(onClick = { showConfirm = true }) { Text("Clear cache only") }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Clear cached files?") },
            text = {
                Text("This removes app cache and known WebView disk-cache folders only. It does not delete history, bookmarks, tabs, wallet records, cookies, or website storage. Some pages may reload or download resources again.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { clearCacheOnly(context) }
                        status = "Cache cleanup freed ${formatBytes(result)}. Site data and user records were not cleared."
                        measure()
                    }
                }) { Text("Clear cache") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StorageLine(label: String, bytes: Long) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(formatBytes(bytes), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun webViewCacheSize(context: Context): Long {
    val root = File(context.applicationInfo.dataDir, "app_webview")
    return listOf(
        File(root, "Default/Cache"),
        File(root, "Default/Code Cache"),
        File(root, "Default/GPUCache"),
        File(root, "Cache"),
        File(root, "Code Cache"),
        File(root, "GPUCache")
    ).sumOf(::directorySize)
}

private fun clearCacheOnly(context: Context): Long {
    var before = directorySize(context.cacheDir) + (context.externalCacheDir?.let(::directorySize) ?: 0L)
    val roots = listOf(
        File(context.applicationInfo.dataDir, "app_webview/Default/Cache"),
        File(context.applicationInfo.dataDir, "app_webview/Default/Code Cache"),
        File(context.applicationInfo.dataDir, "app_webview/Default/GPUCache"),
        File(context.applicationInfo.dataDir, "app_webview/Cache"),
        File(context.applicationInfo.dataDir, "app_webview/Code Cache"),
        File(context.applicationInfo.dataDir, "app_webview/GPUCache")
    )
    roots.forEach { before += directorySize(it) }
    clearDirectoryContents(context.cacheDir)
    context.externalCacheDir?.let(::clearDirectoryContents)
    roots.forEach(::clearDirectoryContents)
    val after = directorySize(context.cacheDir) +
        (context.externalCacheDir?.let(::directorySize) ?: 0L) +
        roots.sumOf(::directorySize)
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

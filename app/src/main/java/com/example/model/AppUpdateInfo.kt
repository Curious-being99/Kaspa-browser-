package com.example.model

import java.io.File

/**
 * Metadata model for Kaspa In-App Sideload Updates.
 */
data class AppUpdateInfo(
    val latestVersionName: String,
    val latestVersionCode: Int,
    val currentVersionName: String,
    val currentVersionCode: Int,
    val isUpdateAvailable: Boolean,
    val releaseTitle: String,
    val releaseNotes: String,
    val releaseDate: String,
    val downloadUrl: String,
    val apkSizeBytes: Long = 0L,
    val apkSizeFormatted: String = "28.5 MB",
    val sha256Checksum: String? = null,
    val channel: String = "Stable (BlockDAG Core)",
    val isMandatory: Boolean = false
)

/**
 * Reactive state machine representing the lifecycle of an in-app update check, download, and installation.
 */
sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(val updateInfo: AppUpdateInfo) : UpdateStatus()
    data class UpToDate(val currentVersion: String, val checkedTimestamp: Long = System.currentTimeMillis()) : UpdateStatus()
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val updateInfo: AppUpdateInfo
    ) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File, val updateInfo: AppUpdateInfo) : UpdateStatus()
    data class Installing(val updateInfo: AppUpdateInfo) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

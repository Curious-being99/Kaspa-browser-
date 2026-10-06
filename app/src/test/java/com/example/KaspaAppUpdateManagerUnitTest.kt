package com.example

import com.example.model.AppUpdateInfo
import com.example.model.UpdateStatus
import com.example.network.AppUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KaspaAppUpdateManagerUnitTest {

    private val updateManager = AppUpdateManager.getInstance()

    @Test
    fun testVersionComparisonLogic() {
        // Newer version comparisons
        assertTrue(updateManager.compareVersions("1.1.0", "1.0.0") > 0)
        assertTrue(updateManager.compareVersions("1.0.1", "1.0.0") > 0)
        assertTrue(updateManager.compareVersions("2.0.0", "1.9.9") > 0)
        assertTrue(updateManager.compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue(updateManager.compareVersions("v1.2.0", "1.1.0") > 0)

        // Same version comparisons
        assertEquals(0, updateManager.compareVersions("1.0.0", "1.0.0"))
        assertEquals(0, updateManager.compareVersions("v1.0.0", "1.0.0"))
        assertEquals(0, updateManager.compareVersions("1.1.0", "v1.1.0"))

        // Older version comparisons
        assertTrue(updateManager.compareVersions("1.0.0", "1.1.0") < 0)
        assertTrue(updateManager.compareVersions("0.9.0", "1.0.0") < 0)
    }

    @Test
    fun testAppUpdateInfoStructure() {
        val info = AppUpdateInfo(
            latestVersionName = "1.1.0",
            latestVersionCode = 2,
            currentVersionName = "1.0.0",
            currentVersionCode = 1,
            isUpdateAvailable = true,
            releaseTitle = "Kaspa BlockDAG v1.1.0",
            releaseNotes = "• Testnet 10 improvements\n• In-App Sideload Update Zone",
            releaseDate = "October 2026",
            downloadUrl = "https://github.com/kaspa-browser/kaspa-browser/releases/download/v1.1.0/kaspa-browser.apk",
            apkSizeBytes = 29_884_416L,
            apkSizeFormatted = "28.5 MB"
        )

        assertTrue(info.isUpdateAvailable)
        assertEquals("1.1.0", info.latestVersionName)
        assertEquals("1.0.0", info.currentVersionName)
        assertTrue(info.releaseNotes.contains("In-App Sideload"))
        assertNotNull(info.downloadUrl)
    }

    @Test
    fun testUpdateStatusStateTransitions() {
        val info = AppUpdateInfo(
            latestVersionName = "1.1.0",
            latestVersionCode = 2,
            currentVersionName = "1.0.0",
            currentVersionCode = 1,
            isUpdateAvailable = true,
            releaseTitle = "Kaspa Release",
            releaseNotes = "Test notes",
            releaseDate = "Today",
            downloadUrl = "https://example.com/kaspa.apk",
            apkSizeBytes = 1000L
        )

        val idle: UpdateStatus = UpdateStatus.Idle
        val checking: UpdateStatus = UpdateStatus.Checking
        val available: UpdateStatus = UpdateStatus.Available(info)
        val downloading: UpdateStatus = UpdateStatus.Downloading(50, 500L, 1000L, info)
        val dummyFile = File("/tmp/kaspa_test.apk")
        val ready: UpdateStatus = UpdateStatus.ReadyToInstall(dummyFile, info)

        assertTrue(idle is UpdateStatus.Idle)
        assertTrue(checking is UpdateStatus.Checking)
        assertTrue(available is UpdateStatus.Available)
        assertEquals(50, (downloading as UpdateStatus.Downloading).progressPercent)
        assertEquals(dummyFile, (ready as UpdateStatus.ReadyToInstall).apkFile)
    }
}

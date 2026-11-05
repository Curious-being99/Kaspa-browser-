package com.example.util

/**
 * Process and WebView counts captured at one instant for before/after profiling.
 * PSS values are in KB; heap values are in bytes.
 */
data class BrowserMemorySnapshot(
    val timestampMs: Long,
    val liveWebViewCount: Int,
    val attachedWebViewCount: Int,
    val appPssKb: Int,
    val rendererProcessCount: Int,
    val rendererPssKb: Int,
    val javaHeapUsedBytes: Long,
    val nativeHeapAllocatedBytes: Long
)

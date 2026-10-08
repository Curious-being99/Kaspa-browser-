package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class MimeResolution(
    val primaryMime: String,
    val extraMimes: List<String>?
)

/**
 * Thread-safe, lifecycle-resilient helper for WebView file uploads (WebChromeClient.onShowFileChooser).
 *
 * Prevents browser crashes by:
 * 1. Always returning true when the callback is accepted/handled, preventing Chromium IllegalStateException.
 * 2. Guaranteeing the ValueCallback<Array<Uri>> is invoked exactly once and never leaked or double-called.
 * 3. Safely copying external content stream URIs to app-private cache files served via FileProvider,
 *    preventing SecurityException / Permission Denial when Chromium background worker threads read the file.
 * 4. Supporting camera capture (<input type="file" capture>) with safe FileProvider output URIs.
 * 5. Providing robust fallbacks across Android versions (API 24 - 35+).
 */
object WebChromeUploadHelper {

    private const val TAG = "WebChromeUploadHelper"
    private const val WILDCARD_MIME = "*/*"

    @Volatile
    var activeLauncher: ((Intent) -> Boolean)? = null

    private val callbackLock = Any()
    private var pendingCallback: ValueCallback<Array<Uri>>? = null
    private var callbackDelivered: Boolean = false

    @Volatile
    var pendingCameraCaptureUri: Uri? = null

    /**
     * Handles an onShowFileChooser request from WebChromeClient.
     * Always returns true if handled or safely cancelled, preventing Chromium crash contracts.
     */
    fun handleFileChooser(
        callback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
        context: Context,
        onStatusMessage: ((String) -> Unit)? = null
    ): Boolean {
        if (callback == null) return false

        // Register new callback, safely completing any previously pending one
        synchronized(callbackLock) {
            safeCancelPendingLocked()
            pendingCallback = callback
            callbackDelivered = false
        }

        val launcher = activeLauncher
        if (launcher == null) {
            Log.w(TAG, "No active ActivityResultLauncher available to handle file chooser")
            completeUpload(null)
            onStatusMessage?.invoke("Unable to open file picker: UI not ready")
            return true
        }

        return try {
            val chooserIntent = buildSafeChooserIntent(context, params)
            val launched = launcher(chooserIntent)
            if (!launched) {
                Log.w(TAG, "Launcher failed to start file chooser intent")
                completeUpload(null)
                onStatusMessage?.invoke("Could not open file picker on this device")
                true
            } else {
                true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception launching file chooser intent: ${e.message}", e)
            completeUpload(null)
            onStatusMessage?.invoke("File picker failed: ${e.localizedMessage ?: "Unknown error"}")
            true
        }
    }

    /**
     * Builds a safe Chooser Intent with normalized MIME types, camera support if requested, and guaranteed flags.
     */
    fun buildSafeChooserIntent(context: Context, params: WebChromeClient.FileChooserParams?): Intent {
        val resolution = resolveMimeTypes(params?.acceptTypes)
        val isMultiple = params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val isCapture = params?.isCaptureEnabled == true

        // Universal Android file picker intent
        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = resolution.primaryMime
            if (!resolution.extraMimes.isNullOrEmpty()) {
                putExtra(Intent.EXTRA_MIME_TYPES, resolution.extraMimes.toTypedArray())
            }
            if (isMultiple) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Prepare optional camera capture intent if capture is requested or image type
        var cameraIntent: Intent? = null
        if (isCapture || resolution.primaryMime.startsWith("image/", ignoreCase = true) || resolution.primaryMime == WILDCARD_MIME) {
            try {
                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                val photoFile = createTempUploadFile(context, "camera_capture", ".jpg")
                if (photoFile != null) {
                    val photoUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    pendingCameraCaptureUri = photoUri
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    cameraIntent = takePictureIntent
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Camera capture intent preparation notice: ${e.message}")
            }
        }

        val title = params?.title?.takeIf { it.isNotBlank() } ?: "Choose file to upload"

        // If website specifically requested camera capture and camera is available, use camera with file picker fallback
        if (isCapture && cameraIntent != null) {
            return Intent.createChooser(cameraIntent, title).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(getContentIntent))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooserIntent = Intent.createChooser(getContentIntent, title).apply {
            if (cameraIntent != null) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return chooserIntent
    }

    /**
     * Sanitizes and normalizes HTML accept types into Android-compatible MIME types.
     */
    fun resolveMimeTypes(acceptTypes: Array<String>?): MimeResolution {
        if (acceptTypes == null || acceptTypes.isEmpty()) {
            return MimeResolution(WILDCARD_MIME, null)
        }

        val mimeTypeMap = MimeTypeMap.getSingleton()
        val normalizedMimes = mutableListOf<String>()

        for (raw in acceptTypes) {
            if (raw.isNullOrBlank()) continue
            val tokens = raw.split(",")
            for (token in tokens) {
                val clean = token.trim().lowercase()
                if (clean.isEmpty()) continue

                if (clean.startsWith(".")) {
                    val ext = clean.removePrefix(".")
                    val mime = mimeTypeMap.getMimeTypeFromExtension(ext)
                    if (!mime.isNullOrBlank() && !normalizedMimes.contains(mime)) {
                        normalizedMimes.add(mime)
                    }
                } else if (clean.contains("/")) {
                    if (!normalizedMimes.contains(clean)) {
                        normalizedMimes.add(clean)
                    }
                } else {
                    val mime = mimeTypeMap.getMimeTypeFromExtension(clean)
                    if (!mime.isNullOrBlank() && !normalizedMimes.contains(mime)) {
                        normalizedMimes.add(mime)
                    }
                }
            }
        }

        return when {
            normalizedMimes.isEmpty() -> MimeResolution(WILDCARD_MIME, null)
            normalizedMimes.size == 1 -> MimeResolution(normalizedMimes.first(), null)
            else -> MimeResolution(WILDCARD_MIME, normalizedMimes)
        }
    }

    /**
     * Safely parses the resulting Uri array from ActivityResult data.
     * Ensures all URIs are readable by Chromium without SecurityException or Lifecycle revocation.
     */
    fun parseResultUris(resultCode: Int, data: Intent?, context: Context?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK) {
            pendingCameraCaptureUri = null
            return null
        }

        val rawUriList = mutableListOf<Uri>()

        // 1. Check if camera capture was used
        val cameraUri = pendingCameraCaptureUri
        pendingCameraCaptureUri = null
        if (cameraUri != null && context != null) {
            try {
                val hasContent = context.contentResolver.openInputStream(cameraUri)?.use { it.available() > 0 } ?: false
                if (hasContent) {
                    rawUriList.add(cameraUri)
                }
            } catch (_: Throwable) {}
        }

        // 2. Standard WebChromeClient FileChooserParams parseResult
        if (rawUriList.isEmpty() && data != null) {
            try {
                val parsed = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                if (parsed != null && parsed.isNotEmpty()) {
                    for (u in parsed) {
                        if (u != null) rawUriList.add(u)
                    }
                }
            } catch (_: Throwable) {}
        }

        // 3. ClipData for multiple items
        if (rawUriList.isEmpty() && data != null) {
            val clipData = data.clipData
            if (clipData != null && clipData.itemCount > 0) {
                for (i in 0 until clipData.itemCount) {
                    val u = clipData.getItemAt(i).uri
                    if (u != null) rawUriList.add(u)
                }
            }
        }

        // 4. Single data Uri
        if (rawUriList.isEmpty() && data != null) {
            data.data?.let { rawUriList.add(it) }
        }

        if (rawUriList.isEmpty()) return null

        // 5. Ensure all URIs can be read by Chromium without permission revocation
        if (context != null) {
            val safeList = mutableListOf<Uri>()
            for (u in rawUriList) {
                val safeUri = ensureSafeReadableUri(context, u)
                safeList.add(safeUri)
            }
            return safeList.toTypedArray()
        }

        return rawUriList.toTypedArray()
    }

    /**
     * Ensures a selected URI is safe and readable across process boundaries.
     * Takes persistable permissions and bridges external streams into app-private cache files
     * served via FileProvider to guarantee Chromium does not crash on SecurityException.
     */
    private fun ensureSafeReadableUri(context: Context, uri: Uri): Uri {
        val myAuthority = "${context.packageName}.fileprovider"

        // If it's already from our FileProvider, it is 100% safe
        if (uri.authority == myAuthority) {
            return uri
        }

        // Try persistable permission grant if available
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {}

        // For file:// scheme (disallowed on modern Android) or external content:// URIs,
        // copy stream to a temporary upload cache file served via our FileProvider
        return try {
            val displayName = queryFileName(context, uri) ?: "upload_${System.currentTimeMillis()}"
            val ext = displayName.substringAfterLast('.', "tmp")
            val cachedFile = createTempUploadFile(context, "upload_${System.currentTimeMillis()}", ".$ext")

            if (cachedFile != null) {
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cachedFile).use { output ->
                        input.copyTo(output)
                    }
                    true
                } ?: false

                if (copied && cachedFile.length() > 0) {
                    val fpUri = FileProvider.getUriForFile(context, myAuthority, cachedFile)
                    try {
                        context.grantUriPermission(context.packageName, fpUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Throwable) {}
                    return fpUri
                }
            }
            uri
        } catch (e: Throwable) {
            Log.w(TAG, "Notice creating safe upload uri for $uri: ${e.message}")
            uri
        }
    }

    /**
     * Resolves the user-facing display name of a content URI.
     */
    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            }
            uri.lastPathSegment
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Creates a unique temporary file inside app-private cacheDir for upload streaming.
     * Cleans up expired cache files older than 6 hours.
     */
    private fun createTempUploadFile(context: Context, prefix: String, suffix: String): File? {
        return try {
            val uploadDir = File(context.cacheDir, "upload_cache")
            if (!uploadDir.exists()) {
                uploadDir.mkdirs()
            }
            cleanExpiredUploadFiles(uploadDir)
            File.createTempFile(prefix + "_", suffix, uploadDir)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to create temp upload file: ${e.message}")
            null
        }
    }

    /**
     * Cleans up old upload cache files to conserve device storage.
     */
    private fun cleanExpiredUploadFiles(uploadDir: File) {
        try {
            val now = System.currentTimeMillis()
            val maxAge = 6 * 3600 * 1000L // 6 hours
            uploadDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAge) {
                    file.delete()
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Completes the pending file upload with the selected URIs (invoked at most once).
     */
    fun completeUpload(uris: Array<Uri>?) {
        synchronized(callbackLock) {
            if (!callbackDelivered) {
                callbackDelivered = true
                val cb = pendingCallback
                pendingCallback = null
                try {
                    cb?.onReceiveValue(uris)
                } catch (e: Throwable) {
                    Log.w(TAG, "Error delivering URIs to WebView ValueCallback: ${e.message}")
                }
            }
        }
    }

    /**
     * Cancels the pending file upload by notifying the WebView with null.
     */
    fun cancelUpload() {
        completeUpload(null)
    }

    private fun safeCancelPendingLocked() {
        if (!callbackDelivered && pendingCallback != null) {
            val cb = pendingCallback
            pendingCallback = null
            callbackDelivered = true
            try {
                cb?.onReceiveValue(null)
            } catch (_: Throwable) {}
        }
        pendingCallback = null
        callbackDelivered = true
    }
}

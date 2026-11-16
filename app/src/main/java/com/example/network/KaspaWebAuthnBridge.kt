package com.example.network

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.engine.BrowserEngineManager
import com.example.security.CredentialSettingsStore
import com.example.security.PasswordVaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.WeakHashMap

enum class PasskeyPromptKind {
    SIGN_IN,
    CREATE_OR_UPGRADE
}

data class PasskeyConfirmationRequest(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val host: String,
    val rpId: String,
    val username: String,
    val kind: PasskeyPromptKind,
    val isUpgrade: Boolean = false,
    val onDecision: (Boolean) -> Unit
)

data class PasskeyRecoveryEvent(
    val url: String,
    val host: String,
    val operation: String,
    val reason: String
)

data class RecordedPasskeyInfo(
    val url: String,
    val rpId: String,
    val username: String,
    val siteName: String,
    val isUpgrade: Boolean
)

/**
 * Origin-checked, main-frame-only WebAuthn / FIDO2 Passkey coordinator built on
 * Chromium WebView (`WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER`), Jetpack WebKit
 * `WebMessageListener` + `DOCUMENT_START_SCRIPT`, and Android `CredentialManager`.
 *
 * Key reliability guarantees:
 * - Preserves browser user activation by invoking Chromium's real native WebAuthn
 *   (`originalCreate` / `originalGet`) immediately without preemptive dialog pauses.
 * - Strips null/undefined fields cleanly from WebAuthn JSON before `CredentialManager`
 *   so Google Play Services FIDO2 does not reject options with "internal error".
 * - Preserves `PublicKeyCredential` native static methods & `Symbol.hasInstance`.
 * - Only supplies `origin` to `CredentialManager` when `CREDENTIAL_MANAGER_SET_ORIGIN`
 *   is granted by the OS; otherwise lets WebView / Custom Tab handle origin trust safely.
 */
object KaspaWebAuthnBridge {
    private const val TAG = "KaspaWebAuthnBridge"
    private const val CHANNEL_NAME = "kaspaWebAuthnChannel"
    private const val PERM_SET_ORIGIN = "android.permission.CREDENTIAL_MANAGER_SET_ORIGIN"
    private val validCallbackIdRegex = Regex("^[A-Za-z0-9_]{1,64}$")
    private val mainHandler = Handler(Looper.getMainLooper())

    private class BridgeCallbacks(
        @Volatile var scope: CoroutineScope,
        @Volatile var onPasskeyRecorded: (RecordedPasskeyInfo) -> Unit,
        @Volatile var onStatusMessage: (String) -> Unit,
        @Volatile var onConfirmationRequired: (PasskeyConfirmationRequest) -> Unit,
        @Volatile var onRecoveryNeeded: (PasskeyRecoveryEvent) -> Unit
    )

    private val callbackMap = Collections.synchronizedMap(WeakHashMap<WebView, BridgeCallbacks>())
    private val docStartInjectedWebViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

    private fun findActivity(ctx: Context): Activity? {
        var current: Context? = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun hasPrivilegedOriginPermission(context: Context): Boolean {
        return try {
            context.checkSelfPermission(PERM_SET_ORIGIN) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun install(
        webView: WebView,
        scope: CoroutineScope,
        onPasskeyRecorded: (RecordedPasskeyInfo) -> Unit,
        onStatusMessage: (String) -> Unit,
        onConfirmationRequired: (PasskeyConfirmationRequest) -> Unit,
        onRecoveryNeeded: (PasskeyRecoveryEvent) -> Unit
    ) {
        CredentialSettingsStore.init(webView.context)

        val alreadyInstalled = synchronized(callbackMap) {
            val prev = callbackMap[webView]
            if (prev != null) {
                prev.scope = scope
                prev.onPasskeyRecorded = onPasskeyRecorded
                prev.onStatusMessage = onStatusMessage
                prev.onConfirmationRequired = onConfirmationRequired
                prev.onRecoveryNeeded = onRecoveryNeeded
                true
            } else {
                callbackMap[webView] = BridgeCallbacks(
                    scope = scope,
                    onPasskeyRecorded = onPasskeyRecorded,
                    onStatusMessage = onStatusMessage,
                    onConfirmationRequired = onConfirmationRequired,
                    onRecoveryNeeded = onRecoveryNeeded
                )
                false
            }
        }

        if (!alreadyInstalled && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            try {
                WebViewCompat.addWebMessageListener(
                    webView,
                    CHANNEL_NAME,
                    setOf("*")
                ) { view, message, sourceOrigin, isMainFrame, replyProxy ->
                    if (!isMainFrame) return@addWebMessageListener
                    val currentUrl = view.url.orEmpty()
                    if (!currentUrl.startsWith("https://", ignoreCase = true)) return@addWebMessageListener
                    val currentOrigin = PasswordVaultRepository.normalizeOrigin(currentUrl, "")
                    val sourceStr = sourceOrigin.toString().lowercase(Locale.ROOT).trimEnd('/')
                    if (sourceStr != currentOrigin.lowercase(Locale.ROOT).trimEnd('/')) {
                        return@addWebMessageListener
                    }

                    val payload = try {
                        JSONObject(message.data ?: return@addWebMessageListener)
                    } catch (_: Throwable) {
                        return@addWebMessageListener
                    }
                    val callbacks = callbackMap[view] ?: return@addWebMessageListener
                    handleMessage(view, currentUrl, currentOrigin, payload, replyProxy, callbacks)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to install WebAuthn WebMessageListener: ${t.message}")
                callbackMap.remove(webView)
            }
        }

        val shouldAddDocStart = synchronized(docStartInjectedWebViews) {
            docStartInjectedWebViews.add(webView)
        }
        if (shouldAddDocStart && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    buildInjectionScript(buildPolicyJson()),
                    setOf("*")
                )
            } catch (t: Throwable) {
                Log.w(TAG, "DocumentStartJavaScript passkey notice: ${t.message}")
            }
        }
    }

    /**
     * Updates runtime WebAuthn / Passkey preferences inside the currently loaded page and
     * installs the bridge shim when `DOCUMENT_START_SCRIPT` is unavailable on older WebViews.
     */
    fun syncPageScriptAndPolicy(webView: WebView?, url: String?) {
        if (webView == null || url.isNullOrBlank()) return
        if (!url.startsWith("https://", ignoreCase = true)) return
        CredentialSettingsStore.init(webView.context)
        val policyJson = buildPolicyJson()
        val script = buildInjectionScript(policyJson)
        webView.post {
            try {
                webView.evaluateJavascript("window.__kaspaPasskeyPolicy = $policyJson;", null)
                webView.evaluateJavascript(script, null)
            } catch (_: Throwable) {}
        }
    }

    fun buildPolicyJson(): String {
        val status = BrowserEngineManager.engineStatus.value
        val obj = JSONObject().apply {
            put("webAuthEnabled", CredentialSettingsStore.webAuthEnabled.value)
            put("autoCreatePasskey", CredentialSettingsStore.autoCreatePasskeyEnabled.value)
            put("allowPasskeyUpgrade", CredentialSettingsStore.allowPasskeyUpgradeEnabled.value)
            put("autoSignIn", CredentialSettingsStore.autoSignInEnabled.value)
            put("skipNativeWebView", status.passkeyDowngradedBySystem)
        }
        return obj.toString()
    }

    /**
     * Recursively strips JSON null entries and ensures mandatory WebAuthn FIDO2 fields
     * (`rp.id`, `rpId`, `pubKeyCredParams`) are well-formed for Android `CredentialManager`.
     */
    internal fun sanitizeCredentialJsonForManager(
        source: JSONObject,
        fallbackRpId: String,
        isCreate: Boolean
    ): JSONObject {
        fun cleanObject(obj: JSONObject): JSONObject {
            val cleaned = JSONObject()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val rawVal = obj.opt(key)
                if (rawVal == null || rawVal == JSONObject.NULL) continue
                when (rawVal) {
                    is JSONObject -> {
                        val sub = cleanObject(rawVal)
                        if (sub.length() > 0 || key == "extensions") {
                            if (!(key == "extensions" && sub.length() == 0)) {
                                cleaned.put(key, sub)
                            }
                        }
                    }
                    is JSONArray -> {
                        val arr = JSONArray()
                        for (i in 0 until rawVal.length()) {
                            val elem = rawVal.opt(i)
                            if (elem == null || elem == JSONObject.NULL) continue
                            if (elem is JSONObject) {
                                val sub = cleanObject(elem)
                                if (sub.length() > 0) arr.put(sub)
                            } else {
                                arr.put(elem)
                            }
                        }
                        if (arr.length() > 0 || key == "allowCredentials" || key == "excludeCredentials") {
                            cleaned.put(key, arr)
                        }
                    }
                    else -> cleaned.put(key, rawVal)
                }
            }
            return cleaned
        }

        val out = cleanObject(source)
        val cleanHost = fallbackRpId.trim()
        if (isCreate) {
            val rp = out.optJSONObject("rp") ?: JSONObject().also { out.put("rp", it) }
            if (rp.optString("id").isBlank() && cleanHost.isNotBlank()) {
                rp.put("id", cleanHost)
            }
            if (rp.optString("name").isBlank()) {
                rp.put("name", rp.optString("id").ifBlank { cleanHost.ifBlank { "Passkey" } })
            }
            val params = out.optJSONArray("pubKeyCredParams")
            if (params == null || params.length() == 0) {
                out.put(
                    "pubKeyCredParams",
                    JSONArray()
                        .put(JSONObject().put("type", "public-key").put("alg", -7))
                        .put(JSONObject().put("type", "public-key").put("alg", -257))
                )
            }
            if (out.optString("attestation").isBlank()) {
                out.put("attestation", "none")
            }
        } else {
            if (out.optString("rpId").isBlank() && cleanHost.isNotBlank()) {
                out.put("rpId", cleanHost)
            }
        }
        return out
    }

    private fun handleMessage(
        webView: WebView,
        pageUrl: String,
        expectedOrigin: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        callbacks: BridgeCallbacks
    ) {
        val host = PasswordVaultRepository.normalizeHost(pageUrl) ?: return
        when (payload.optString("type")) {
            "CONFIRM_SIGN_IN" -> {
                val callId = payload.optString("callId", "")
                if (!validCallbackIdRegex.matches(callId)) return
                val rpId = payload.optString("rpId", host).ifBlank { host }
                mainHandler.post {
                    callbacks.onConfirmationRequired(
                        PasskeyConfirmationRequest(
                            url = pageUrl,
                            host = host,
                            rpId = rpId,
                            username = "",
                            kind = PasskeyPromptKind.SIGN_IN,
                            isUpgrade = false,
                            onDecision = { approved ->
                                val reply = JSONObject().apply {
                                    put("type", "CONFIRM_RESULT")
                                    put("callId", callId)
                                    put("approved", approved)
                                }
                                postReply(webView, expectedOrigin, replyProxy, reply.toString())
                            }
                        )
                    )
                }
            }

            "CONFIRM_CREATE_OR_UPGRADE" -> {
                val callId = payload.optString("callId", "")
                if (!validCallbackIdRegex.matches(callId)) return
                val rpId = payload.optString("rpId", host).ifBlank { host }
                val username = payload.optString("username", "")
                val isUpgrade = payload.optBoolean("isUpgrade", false)
                mainHandler.post {
                    callbacks.onConfirmationRequired(
                        PasskeyConfirmationRequest(
                            url = pageUrl,
                            host = host,
                            rpId = rpId,
                            username = username,
                            kind = PasskeyPromptKind.CREATE_OR_UPGRADE,
                            isUpgrade = isUpgrade,
                            onDecision = { approved ->
                                val reply = JSONObject().apply {
                                    put("type", "CONFIRM_RESULT")
                                    put("callId", callId)
                                    put("approved", approved)
                                }
                                postReply(webView, expectedOrigin, replyProxy, reply.toString())
                            }
                        )
                    )
                }
            }

            "PASSKEY_NATIVE_CREATED" -> {
                val rpId = payload.optString("rpId", host).ifBlank { host }
                val siteName = payload.optString("siteName", host).ifBlank { host }
                val username = payload.optString("username", "")
                val isUpgrade = payload.optBoolean("isUpgrade", false)
                mainHandler.post {
                    callbacks.onPasskeyRecorded(
                        RecordedPasskeyInfo(
                            url = pageUrl,
                            rpId = rpId,
                            username = username,
                            siteName = siteName,
                            isUpgrade = isUpgrade
                        )
                    )
                    callbacks.onStatusMessage(
                        if (isUpgrade) "Account upgraded with passkey for $rpId"
                        else "Passkey saved for $rpId"
                    )
                }
            }

            "PASSKEY_NATIVE_ASSERTED" -> {
                val rpId = payload.optString("rpId", host).ifBlank { host }
                mainHandler.post {
                    callbacks.onStatusMessage("Signed in with passkey ($rpId)")
                }
            }

            "CREATE_CREDENTIAL" -> {
                val callId = payload.optString("callId", "")
                if (!validCallbackIdRegex.matches(callId)) return
                val rawRequestObj = payload.optJSONObject("request")
                if (rawRequestObj == null) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "Invalid WebAuthn registration request", "TypeError")
                    return
                }
                val isUpgrade = payload.optBoolean("isUpgrade", false)
                val nativeElapsedMs = payload.optLong("nativeElapsedMs", -1L)
                val nativeError = payload.optString("nativeError", "")

                if (!CredentialSettingsStore.webAuthEnabled.value) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "WebAuthn is disabled in Kazium settings", "NotAllowedError")
                    return
                }
                if (isUpgrade && !CredentialSettingsStore.allowPasskeyUpgradeEnabled.value) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "Passkey account upgrades are disabled in settings", "NotAllowedError")
                    return
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "Passkeys require Android 9 (API 28) or higher", "NotSupportedError")
                    mainHandler.post {
                        callbacks.onRecoveryNeeded(
                            PasskeyRecoveryEvent(
                                url = pageUrl,
                                host = host,
                                operation = "create",
                                reason = "Android 9+ is required for passkeys."
                            )
                        )
                    }
                    return
                }

                val requestObj = sanitizeCredentialJsonForManager(rawRequestObj, host, isCreate = true)

                callbacks.scope.launch(Dispatchers.Main) {
                    val startMs = System.currentTimeMillis()
                    try {
                        callbacks.onStatusMessage(
                            if (isUpgrade) "Upgrading account to passkey for $host..."
                            else "Creating passkey for $host..."
                        )
                        val activity = findActivity(webView.context) ?: webView.context
                        val credentialManager = CredentialManager.create(activity)
                        val validRequestJson = requestObj.toString()
                        val originArg = if (hasPrivilegedOriginPermission(activity)) expectedOrigin else null
                        val request = CreatePublicKeyCredentialRequest(
                            requestJson = validRequestJson,
                            origin = originArg
                        )
                        val response = credentialManager.createCredential(activity, request)
                        if (response is CreatePublicKeyCredentialResponse) {
                            sendCredSuccess(webView, expectedOrigin, replyProxy, callId, response.registrationResponseJson)
                            val rpId = requestObj.optJSONObject("rp")?.optString("id").orEmpty().ifBlank { host }
                            val rpName = requestObj.optJSONObject("rp")?.optString("name").orEmpty().ifBlank { host }
                            val userObj = requestObj.optJSONObject("user")
                            val userName = userObj?.optString("name").orEmpty()
                                .ifBlank { userObj?.optString("displayName").orEmpty() }
                            callbacks.onPasskeyRecorded(
                                RecordedPasskeyInfo(
                                    url = pageUrl,
                                    rpId = rpId,
                                    username = userName,
                                    siteName = rpName,
                                    isUpgrade = isUpgrade
                                )
                            )
                            callbacks.onStatusMessage(
                                if (isUpgrade) "Account upgraded to passkey for $rpId"
                                else "Passkey created for $rpId"
                            )
                        } else {
                            sendCredError(webView, expectedOrigin, replyProxy, callId, "Unsupported passkey registration response", "NotSupportedError")
                        }
                    } catch (e: CreateCredentialException) {
                        val elapsed = System.currentTimeMillis() - startMs
                        val domName = mapCreateDomErrorName(e)
                        Log.w(TAG, "CredentialManager createCredential failed (${e.javaClass.simpleName}, ${elapsed}ms): ${e.message}")
                        sendCredError(webView, expectedOrigin, replyProxy, callId, e.message ?: "Passkey creation failed", domName)
                        val shouldOfferRecovery = when (e) {
                            is CreateCredentialCancellationException -> elapsed < 1500L || (nativeElapsedMs in 0..1500L)
                            is CreateCredentialNoCreateOptionException,
                            is CreateCredentialProviderConfigurationException,
                            is CreateCredentialInterruptedException -> true
                            else -> true
                        }
                        if (shouldOfferRecovery) {
                            callbacks.onRecoveryNeeded(
                                PasskeyRecoveryEvent(
                                    url = pageUrl,
                                    host = host,
                                    operation = "create",
                                    reason = buildRecoveryReason(e.message ?: nativeError, host)
                                )
                            )
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Unexpected createCredential error: ${t.message}")
                        sendCredError(webView, expectedOrigin, replyProxy, callId, t.message ?: "Passkey creation failed", "UnknownError")
                        callbacks.onRecoveryNeeded(
                            PasskeyRecoveryEvent(
                                url = pageUrl,
                                host = host,
                                operation = "create",
                                reason = buildRecoveryReason(t.message ?: nativeError, host)
                            )
                        )
                    }
                }
            }

            "GET_CREDENTIAL" -> {
                val callId = payload.optString("callId", "")
                if (!validCallbackIdRegex.matches(callId)) return
                val rawRequestObj = payload.optJSONObject("request")
                if (rawRequestObj == null) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "Invalid WebAuthn assertion request", "TypeError")
                    return
                }
                val nativeElapsedMs = payload.optLong("nativeElapsedMs", -1L)
                val nativeError = payload.optString("nativeError", "")

                if (!CredentialSettingsStore.webAuthEnabled.value) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "WebAuthn is disabled in Kazium settings", "NotAllowedError")
                    return
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "Passkeys require Android 9 (API 28) or higher", "NotSupportedError")
                    mainHandler.post {
                        callbacks.onRecoveryNeeded(
                            PasskeyRecoveryEvent(
                                url = pageUrl,
                                host = host,
                                operation = "get",
                                reason = "Android 9+ is required for passkeys."
                            )
                        )
                    }
                    return
                }

                val requestObj = sanitizeCredentialJsonForManager(rawRequestObj, host, isCreate = false)

                callbacks.scope.launch(Dispatchers.Main) {
                    val startMs = System.currentTimeMillis()
                    try {
                        callbacks.onStatusMessage("Requesting passkey verification for $host...")
                        val activity = findActivity(webView.context) ?: webView.context
                        val credentialManager = CredentialManager.create(activity)
                        val validRequestJson = requestObj.toString()
                        val option = GetPublicKeyCredentialOption(validRequestJson)
                        val reqBuilder = GetCredentialRequest.Builder()
                            .addCredentialOption(option)
                        if (hasPrivilegedOriginPermission(activity)) {
                            reqBuilder.setOrigin(expectedOrigin)
                        }
                        val response = credentialManager.getCredential(activity, reqBuilder.build())
                        val cred = response.credential
                        if (cred is PublicKeyCredential) {
                            sendCredSuccess(webView, expectedOrigin, replyProxy, callId, cred.authenticationResponseJson)
                            val rpId = requestObj.optString("rpId", host).ifBlank { host }
                            callbacks.onStatusMessage("Passkey verified successfully ($rpId)")
                        } else {
                            sendCredError(webView, expectedOrigin, replyProxy, callId, "No matching passkey credential returned", "NotAllowedError")
                            callbacks.onRecoveryNeeded(
                                PasskeyRecoveryEvent(
                                    url = pageUrl,
                                    host = host,
                                    operation = "get",
                                    reason = buildRecoveryReason(nativeError, host)
                                )
                            )
                        }
                    } catch (e: NoCredentialException) {
                        Log.w(TAG, "No credential in embedded WebView CredentialManager: ${e.message}")
                        sendCredError(webView, expectedOrigin, replyProxy, callId, "No passkey accessible to embedded browser for $host", "NotAllowedError")
                        callbacks.onRecoveryNeeded(
                            PasskeyRecoveryEvent(
                                url = pageUrl,
                                host = host,
                                operation = "get",
                                reason = buildRecoveryReason(e.message ?: nativeError, host)
                            )
                        )
                        callbacks.onStatusMessage("Passkey unavailable in WebView — System Browser Tab handoff ready")
                    } catch (e: GetCredentialException) {
                        val elapsed = System.currentTimeMillis() - startMs
                        val domName = mapGetDomErrorName(e)
                        Log.w(TAG, "CredentialManager getCredential failed (${e.javaClass.simpleName}, ${elapsed}ms): ${e.message}")
                        sendCredError(webView, expectedOrigin, replyProxy, callId, e.message ?: "Passkey verification failed", domName)
                        val isSlowUserCancel = e is GetCredentialCancellationException &&
                            elapsed >= 1800L &&
                            (nativeElapsedMs < 0L || nativeElapsedMs >= 1800L)
                        if (!isSlowUserCancel || e is GetCredentialProviderConfigurationException) {
                            callbacks.onRecoveryNeeded(
                                PasskeyRecoveryEvent(
                                    url = pageUrl,
                                    host = host,
                                    operation = "get",
                                    reason = buildRecoveryReason(e.message ?: nativeError, host)
                                )
                            )
                        }
                        callbacks.onStatusMessage("Passkey could not complete in embedded WebView")
                    } catch (t: Throwable) {
                        Log.w(TAG, "Unexpected getCredential error: ${t.message}")
                        sendCredError(webView, expectedOrigin, replyProxy, callId, t.message ?: "Passkey verification failed", "UnknownError")
                        callbacks.onRecoveryNeeded(
                            PasskeyRecoveryEvent(
                                url = pageUrl,
                                host = host,
                                operation = "get",
                                reason = buildRecoveryReason(t.message ?: nativeError, host)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun mapCreateDomErrorName(e: CreateCredentialException): String {
        val type = e.type.lowercase(Locale.ROOT)
        return when {
            e is CreateCredentialCancellationException -> "NotAllowedError"
            e is CreateCredentialInterruptedException -> "AbortError"
            type.contains("invalid_state") -> "InvalidStateError"
            type.contains("not_supported") -> "NotSupportedError"
            type.contains("security") -> "SecurityError"
            type.contains("constraint") -> "ConstraintError"
            type.contains("not_allowed") -> "NotAllowedError"
            type.contains("unknown") -> "UnknownError"
            else -> "NotAllowedError"
        }
    }

    private fun mapGetDomErrorName(e: GetCredentialException): String {
        val type = e.type.lowercase(Locale.ROOT)
        return when {
            e is GetCredentialCancellationException -> "NotAllowedError"
            e is GetCredentialInterruptedException -> "AbortError"
            type.contains("not_supported") -> "NotSupportedError"
            type.contains("security") -> "SecurityError"
            type.contains("unknown") -> "UnknownError"
            else -> "NotAllowedError"
        }
    }

    private fun buildRecoveryReason(rawError: String?, host: String): String {
        val msg = rawError.orEmpty().lowercase(Locale.ROOT)
        return when {
            msg.contains("privileged") || msg.contains("assetlink") || msg.contains("origin") || msg.contains("role_browser") ->
                "Android requires a system-privileged browser to unlock passkeys for $host."
            msg.contains("no credential") || msg.contains("no passkey") ->
                "Passkey sheet closed because stored passkeys for $host are scoped to your system browser or credential manager."
            else ->
                "Passkey authentication closed in embedded WebView. Continue in a System Browser Tab or use a saved password."
        }
    }

    private fun sendCredSuccess(
        webView: WebView,
        expectedOrigin: String,
        replyProxy: JavaScriptReplyProxy,
        callId: String,
        resultJson: String
    ) {
        val reply = JSONObject().apply {
            put("type", "CRED_RESULT")
            put("callId", callId)
            put("ok", true)
            put("resultJson", resultJson)
        }
        postReply(webView, expectedOrigin, replyProxy, reply.toString())
    }

    private fun sendCredError(
        webView: WebView,
        expectedOrigin: String,
        replyProxy: JavaScriptReplyProxy,
        callId: String,
        errorMessage: String,
        errorName: String = "NotAllowedError"
    ) {
        val reply = JSONObject().apply {
            put("type", "CRED_RESULT")
            put("callId", callId)
            put("ok", false)
            put("error", errorMessage)
            put("errorName", errorName)
        }
        postReply(webView, expectedOrigin, replyProxy, reply.toString())
    }

    private fun postReply(
        webView: WebView,
        expectedOrigin: String,
        replyProxy: JavaScriptReplyProxy,
        replyJson: String
    ) {
        webView.post {
            val currentOrigin = PasswordVaultRepository.normalizeOrigin(webView.url.orEmpty(), "")
            if (!currentOrigin.equals(expectedOrigin, ignoreCase = true)) return@post
            try {
                replyProxy.postMessage(replyJson)
            } catch (_: Throwable) {
                try {
                    val quoted = JSONObject.quote(replyJson)
                    webView.evaluateJavascript("window.__kaspaWebAuthnReply && window.__kaspaWebAuthnReply($quoted);", null)
                } catch (_: Throwable) {}
            }
        }
    }

    fun getInjectionScript(): String = buildInjectionScript(buildPolicyJson())

    fun buildInjectionScript(initialPolicyJson: String): String {
        return """
            (function() {
                try {
                    var defaultPolicy = $initialPolicyJson;
                    if (!window.__kaspaPasskeyPolicy) {
                        window.__kaspaPasskeyPolicy = defaultPolicy;
                    }
                    if (window.__kaspaWebAuthnInjected) return;
                    window.__kaspaWebAuthnInjected = true;
                    if (window.top !== window.self) return;

                    function getPolicy() {
                        return window.__kaspaPasskeyPolicy || defaultPolicy;
                    }

                    function b64ToBuf(b64url) {
                        if (!b64url) return new ArrayBuffer(0);
                        var b64 = String(b64url).replace(/-/g, '+').replace(/_/g, '/');
                        var pad = b64.length % 4;
                        if (pad) b64 += '='.repeat(4 - pad);
                        var bin = atob(b64);
                        var bytes = new Uint8Array(bin.length);
                        for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
                        return bytes.buffer;
                    }

                    function isBinaryLike(val) {
                        if (!val || typeof val !== 'object') return false;
                        if (val instanceof ArrayBuffer || ArrayBuffer.isView(val)) return true;
                        var tag = Object.prototype.toString.call(val);
                        return tag === '[object ArrayBuffer]' || tag === '[object Uint8Array]' || tag === '[object DataView]';
                    }

                    function bufToB64(buf) {
                        if (!buf) return '';
                        var bytes = ArrayBuffer.isView(buf)
                            ? new Uint8Array(buf.buffer, buf.byteOffset, buf.byteLength)
                            : new Uint8Array(buf);
                        var bin = '';
                        for (var i = 0; i < bytes.byteLength; i++) bin += String.fromCharCode(bytes[i]);
                        return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
                    }

                    function sanitizeForCredentialManager(options, isCreate) {
                        if (!options) return options;
                        function convert(val) {
                            if (val === null || val === undefined || typeof val === 'function') return undefined;
                            if (isBinaryLike(val)) {
                                return bufToB64(val);
                            }
                            if (Array.isArray(val)) {
                                var arr = [];
                                for (var i = 0; i < val.length; i++) {
                                    var item = convert(val[i]);
                                    if (item !== undefined) arr.push(item);
                                }
                                return arr;
                            }
                            if (typeof val === 'object') {
                                var obj = {};
                                for (var k in val) {
                                    if (Object.prototype.hasOwnProperty.call(val, k)) {
                                        var v = convert(val[k]);
                                        if (v !== undefined) obj[k] = v;
                                    }
                                }
                                return obj;
                            }
                            return val;
                        }
                        var sanitized = convert(options) || {};
                        var host = (window.location && window.location.hostname) || '';
                        if (isCreate) {
                            sanitized.rp = sanitized.rp || {};
                            if (!sanitized.rp.id && host) sanitized.rp.id = host;
                            if (!sanitized.rp.name) sanitized.rp.name = sanitized.rp.id || host || 'Passkey';
                            if (!Array.isArray(sanitized.pubKeyCredParams) || sanitized.pubKeyCredParams.length === 0) {
                                sanitized.pubKeyCredParams = [
                                    { type: 'public-key', alg: -7 },
                                    { type: 'public-key', alg: -257 }
                                ];
                            }
                            if (!sanitized.attestation) sanitized.attestation = 'none';
                        } else {
                            if (!sanitized.rpId && host) sanitized.rpId = host;
                        }
                        if (sanitized.extensions && Object.keys(sanitized.extensions).length === 0) {
                            delete sanitized.extensions;
                        }
                        return sanitized;
                    }

                    function makeDomError(message, name) {
                        try {
                            return new DOMException(message || 'WebAuthn failed', name || 'NotAllowedError');
                        } catch (_) {
                            var error = new Error(message || 'WebAuthn failed');
                            error.name = name || 'NotAllowedError';
                            return error;
                        }
                    }

                    function buildPublicKeyCredential(rawJson) {
                        var data = typeof rawJson === 'string' ? JSON.parse(rawJson) : rawJson;
                        var resp = data.response || {};
                        var responseObj = {
                            clientDataJSON: resp.clientDataJSON ? b64ToBuf(resp.clientDataJSON) : new ArrayBuffer(0)
                        };
                        if (resp.attestationObject) {
                            responseObj.attestationObject = b64ToBuf(resp.attestationObject);
                            responseObj.getTransports = function() { return resp.transports || ['internal']; };
                            responseObj.getAuthenticatorData = function() {
                                return resp.authenticatorData ? b64ToBuf(resp.authenticatorData) : new ArrayBuffer(0);
                            };
                            responseObj.getPublicKey = function() {
                                return resp.publicKey ? b64ToBuf(resp.publicKey) : null;
                            };
                            responseObj.getPublicKeyAlgorithm = function() {
                                return typeof resp.publicKeyAlgorithm === 'number' ? resp.publicKeyAlgorithm : -7;
                            };
                        }
                        if (resp.authenticatorData) {
                            responseObj.authenticatorData = b64ToBuf(resp.authenticatorData);
                        }
                        if (resp.signature) {
                            responseObj.signature = b64ToBuf(resp.signature);
                        }
                        if (resp.userHandle !== undefined) {
                            responseObj.userHandle = resp.userHandle ? b64ToBuf(resp.userHandle) : null;
                        }
                        var extResults = data.clientExtensionResults || {};
                        return {
                            __kaspaCred: true,
                            id: data.id,
                            rawId: b64ToBuf(data.rawId || data.id),
                            type: data.type || 'public-key',
                            authenticatorAttachment: data.authenticatorAttachment || 'platform',
                            response: responseObj,
                            getClientExtensionResults: function() { return extResults; },
                            toJSON: function() { return data; }
                        };
                    }

                    window.__kaspaWebAuthnPending = window.__kaspaWebAuthnPending || {};

                    window.__kaspaWebAuthnReply = function(msgData) {
                        var activeCb = null;
                        try {
                            var msg = typeof msgData === 'string' ? JSON.parse(msgData) : msgData;
                            if (!msg || !msg.callId) return;
                            activeCb = window.__kaspaWebAuthnPending[msg.callId];
                            if (!activeCb) return;
                            delete window.__kaspaWebAuthnPending[msg.callId];
                            if (msg.type === 'CONFIRM_RESULT') {
                                activeCb.resolve(msg.approved === true);
                            } else if (msg.type === 'CRED_RESULT') {
                                if (msg.ok) {
                                    activeCb.resolve(buildPublicKeyCredential(msg.resultJson));
                                } else {
                                    activeCb.reject(makeDomError(msg.error || 'Passkey operation failed', msg.errorName || 'NotAllowedError'));
                                }
                            }
                        } catch (e) {
                            if (activeCb) {
                                activeCb.reject(e);
                            }
                        }
                    };

                    if (window.kaspaWebAuthnChannel) {
                        window.kaspaWebAuthnChannel.onmessage = function(ev) {
                            if (ev && ev.data) {
                                window.__kaspaWebAuthnReply(ev.data);
                            }
                        };
                    }

                    function nextCallId() {
                        return 'wa_' + Math.random().toString(36).slice(2, 10) + '_' + Date.now();
                    }

                    function postBridgeMessage(payload) {
                        if (!window.kaspaWebAuthnChannel || typeof window.kaspaWebAuthnChannel.postMessage !== 'function') {
                            return false;
                        }
                        window.kaspaWebAuthnChannel.postMessage(JSON.stringify(payload));
                        return true;
                    }

                    function requestUserConfirmation(kindPayload) {
                        return new Promise(function(resolve) {
                            if (!window.kaspaWebAuthnChannel) {
                                resolve(false);
                                return;
                            }
                            var callId = nextCallId();
                            window.__kaspaWebAuthnPending[callId] = {
                                resolve: resolve,
                                reject: function() { resolve(false); }
                            };
                            kindPayload.callId = callId;
                            try {
                                postBridgeMessage(kindPayload);
                            } catch (_) {
                                delete window.__kaspaWebAuthnPending[callId];
                                resolve(false);
                            }
                        });
                    }

                    function executeViaCredentialManager(msgType, pubKeyOptions, extra) {
                        return new Promise(function(resolve, reject) {
                            if (!window.kaspaWebAuthnChannel) {
                                reject(makeDomError('Passkey channel unavailable', 'NotSupportedError'));
                                return;
                            }
                            var callId = nextCallId();
                            window.__kaspaWebAuthnPending[callId] = { resolve: resolve, reject: reject };
                            var payload = {
                                type: msgType,
                                callId: callId,
                                request: sanitizeForCredentialManager(pubKeyOptions, msgType === 'CREATE_CREDENTIAL')
                            };
                            if (extra) {
                                for (var k in extra) {
                                    if (Object.prototype.hasOwnProperty.call(extra, k)) payload[k] = extra[k];
                                }
                            }
                            try {
                                if (!postBridgeMessage(payload)) {
                                    delete window.__kaspaWebAuthnPending[callId];
                                    reject(makeDomError('Passkey channel unavailable', 'NotSupportedError'));
                                }
                            } catch (e) {
                                delete window.__kaspaWebAuthnPending[callId];
                                reject(e);
                            }
                        });
                    }

                    var hadNativePkc = (typeof window.PublicKeyCredential !== 'undefined');
                    if (!hadNativePkc) {
                        window.PublicKeyCredential = function() {};
                    }

                    var origIsUvpaa = hadNativePkc && window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable
                        ? window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable.bind(window.PublicKeyCredential)
                        : null;
                    var origIsConditional = hadNativePkc && window.PublicKeyCredential.isConditionalMediationAvailable
                        ? window.PublicKeyCredential.isConditionalMediationAvailable.bind(window.PublicKeyCredential)
                        : null;
                    var origGetCaps = hadNativePkc && window.PublicKeyCredential.getClientCapabilities
                        ? window.PublicKeyCredential.getClientCapabilities.bind(window.PublicKeyCredential)
                        : null;

                    if (hadNativePkc && typeof Symbol !== 'undefined' && Symbol.hasInstance) {
                        try {
                            var origHasInstance = Object.getOwnPropertyDescriptor(window.PublicKeyCredential, Symbol.hasInstance);
                            Object.defineProperty(window.PublicKeyCredential, Symbol.hasInstance, {
                                configurable: true,
                                value: function(inst) {
                                    if (inst && inst.__kaspaCred) return true;
                                    if (origHasInstance && typeof origHasInstance.value === 'function') {
                                        return origHasInstance.value.call(window.PublicKeyCredential, inst);
                                    }
                                    return Object.prototype.isPrototypeOf.call(window.PublicKeyCredential.prototype, inst);
                                }
                            });
                        } catch (_) {}
                    }

                    window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable = function() {
                        if (!getPolicy().webAuthEnabled) return Promise.resolve(false);
                        if (origIsUvpaa) {
                            return origIsUvpaa().then(function(ok) { return Boolean(ok || window.kaspaWebAuthnChannel); })
                                .catch(function() { return true; });
                        }
                        return Promise.resolve(true);
                    };

                    window.PublicKeyCredential.isConditionalMediationAvailable = function() {
                        if (!getPolicy().webAuthEnabled) return Promise.resolve(false);
                        if (origIsConditional) {
                            return origIsConditional().catch(function() { return false; });
                        }
                        return Promise.resolve(false);
                    };

                    window.PublicKeyCredential.getClientCapabilities = function() {
                        var p = getPolicy();
                        var enabled = Boolean(p.webAuthEnabled);
                        var base = {
                            conditionalCreate: enabled && Boolean(p.allowPasskeyUpgrade),
                            conditionalGet: false,
                            hybridTransport: enabled,
                            passkeyPlatformAuthenticator: enabled,
                            relatedOrigins: true,
                            signalAllAcceptedCredentials: enabled,
                            signalCurrentUserDetails: enabled,
                            signalUnknownCredential: enabled,
                            userVerifyingPlatformAuthenticator: enabled
                        };
                        if (origGetCaps && enabled) {
                            return origGetCaps().then(function(nativeCaps) {
                                var merged = {};
                                for (var k in base) merged[k] = base[k];
                                if (nativeCaps && typeof nativeCaps === 'object') {
                                    for (var nk in nativeCaps) merged[nk] = nativeCaps[nk];
                                }
                                merged.conditionalCreate = enabled && Boolean(p.allowPasskeyUpgrade);
                                return merged;
                            }).catch(function() { return base; });
                        }
                        return Promise.resolve(base);
                    };

                    if (!window.PublicKeyCredential.signalAllAcceptedCredentials) {
                        window.PublicKeyCredential.signalAllAcceptedCredentials = function() { return Promise.resolve(); };
                    }
                    if (!window.PublicKeyCredential.signalCurrentUserDetails) {
                        window.PublicKeyCredential.signalCurrentUserDetails = function() { return Promise.resolve(); };
                    }
                    if (!window.PublicKeyCredential.signalUnknownCredential) {
                        window.PublicKeyCredential.signalUnknownCredential = function() { return Promise.resolve(); };
                    }

                    if (!navigator.credentials) {
                        navigator.credentials = {};
                    }

                    var originalCreate = navigator.credentials.create
                        ? navigator.credentials.create.bind(navigator.credentials)
                        : null;
                    var originalGet = navigator.credentials.get
                        ? navigator.credentials.get.bind(navigator.credentials)
                        : null;

                    function shouldFallbackAfterNativeError(err, elapsedMs, confirmedByDialog) {
                        if (!window.kaspaWebAuthnChannel) return false;
                        var name = (err && err.name) || '';
                        var msg = ((err && err.message) || '').toLowerCase();
                        if (name === 'InvalidStateError' || name === 'AbortError') return false;
                        if (confirmedByDialog) return true;
                        if (name !== 'NotAllowedError') return true;
                        var looksUserCancel = msg.indexOf('cancel') !== -1 || msg.indexOf('declined') !== -1 || msg.indexOf('aborted by user') !== -1;
                        return !looksUserCancel && elapsedMs < 1200;
                    }

                    navigator.credentials.create = function(options) {
                        if (!options || !options.publicKey) {
                            if (originalCreate) return originalCreate(options);
                            return Promise.reject(makeDomError('Invalid options for credentials.create', 'TypeError'));
                        }
                        if (options.signal && options.signal.aborted) {
                            return Promise.reject(makeDomError('Aborted', 'AbortError'));
                        }

                        var policy = getPolicy();
                        if (!policy.webAuthEnabled) {
                            return Promise.reject(makeDomError('Passkeys are disabled in Kazium settings', 'NotAllowedError'));
                        }

                        var isConditionalUpgrade = (options.mediation === 'conditional');
                        if (isConditionalUpgrade && !policy.allowPasskeyUpgrade) {
                            return Promise.reject(makeDomError('Passkey account upgrade disabled by user setting', 'NotAllowedError'));
                        }

                        var pubKey = options.publicKey;
                        var rpId = (pubKey.rp && pubKey.rp.id) || (window.location && window.location.hostname) || '';
                        var siteName = (pubKey.rp && pubKey.rp.name) || document.title || rpId;
                        var userName = (pubKey.user && (pubKey.user.name || pubKey.user.displayName)) || '';

                        var proceedCreate = function(confirmedByDialog) {
                            if (!isConditionalUpgrade && originalCreate && !policy.skipNativeWebView) {
                                var t0 = Date.now();
                                return originalCreate(options).then(function(cred) {
                                    postBridgeMessage({
                                        type: 'PASSKEY_NATIVE_CREATED',
                                        rpId: rpId,
                                        siteName: siteName,
                                        username: userName,
                                        isUpgrade: isConditionalUpgrade
                                    });
                                    return cred;
                                }).catch(function(err) {
                                    var elapsed = Date.now() - t0;
                                    if (shouldFallbackAfterNativeError(err, elapsed, confirmedByDialog)) {
                                        return executeViaCredentialManager('CREATE_CREDENTIAL', pubKey, {
                                            isUpgrade: isConditionalUpgrade,
                                            nativeElapsedMs: elapsed,
                                            nativeError: (err && (err.name + ': ' + err.message)) || ''
                                        });
                                    }
                                    throw err;
                                });
                            }
                            return executeViaCredentialManager('CREATE_CREDENTIAL', pubKey, {
                                isUpgrade: isConditionalUpgrade,
                                nativeElapsedMs: -1,
                                nativeError: policy.skipNativeWebView ? 'WebView WebAuthn restricted by OS' : ''
                            });
                        };

                        if (!policy.autoCreatePasskey) {
                            return requestUserConfirmation({
                                type: 'CONFIRM_CREATE_OR_UPGRADE',
                                rpId: rpId,
                                username: userName,
                                isUpgrade: isConditionalUpgrade
                            }).then(function(approved) {
                                if (!approved) {
                                    throw makeDomError('Passkey creation declined by user', 'NotAllowedError');
                                }
                                return proceedCreate(true);
                            });
                        }

                        return proceedCreate(false);
                    };

                    navigator.credentials.get = function(options) {
                        if (!options || !options.publicKey) {
                            if (originalGet) return originalGet(options);
                            return Promise.reject(makeDomError('Invalid options for credentials.get', 'TypeError'));
                        }
                        if (options.signal && options.signal.aborted) {
                            return Promise.reject(makeDomError('Aborted', 'AbortError'));
                        }

                        var policy = getPolicy();
                        if (!policy.webAuthEnabled) {
                            return Promise.reject(makeDomError('Passkeys are disabled in Kazium settings', 'NotAllowedError'));
                        }

                        if (options.mediation === 'conditional') {
                            if (originalGet && !policy.skipNativeWebView) {
                                return originalGet(options);
                            }
                            return Promise.reject(makeDomError('Conditional mediation not active in WebView', 'NotAllowedError'));
                        }

                        var pubKey = options.publicKey;
                        var rpId = pubKey.rpId || (window.location && window.location.hostname) || '';

                        var proceedGet = function(confirmedByDialog) {
                            if (originalGet && !policy.skipNativeWebView) {
                                var t0 = Date.now();
                                return originalGet(options).then(function(cred) {
                                    postBridgeMessage({
                                        type: 'PASSKEY_NATIVE_ASSERTED',
                                        rpId: rpId
                                    });
                                    return cred;
                                }).catch(function(err) {
                                    var elapsed = Date.now() - t0;
                                    if (shouldFallbackAfterNativeError(err, elapsed, confirmedByDialog)) {
                                        return executeViaCredentialManager('GET_CREDENTIAL', pubKey, {
                                            nativeElapsedMs: elapsed,
                                            nativeError: (err && (err.name + ': ' + err.message)) || ''
                                        });
                                    }
                                    throw err;
                                });
                            }
                            return executeViaCredentialManager('GET_CREDENTIAL', pubKey, {
                                nativeElapsedMs: -1,
                                nativeError: policy.skipNativeWebView ? 'WebView WebAuthn restricted by OS' : ''
                            });
                        };

                        if (!policy.autoSignIn) {
                            return requestUserConfirmation({
                                type: 'CONFIRM_SIGN_IN',
                                rpId: rpId
                            }).then(function(approved) {
                                if (!approved) {
                                    throw makeDomError('Sign-in cancelled by user confirmation prompt', 'NotAllowedError');
                                }
                                return proceedGet(true);
                            });
                        }

                        return proceedGet(false);
                    };
                } catch (e) {
                    console.warn('Kaspa WebAuthn bridge setup notice', e);
                }
            })();
        """.trimIndent()
    }
}

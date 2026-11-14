package com.example.network

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
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
 * Jetpack WebKit `WebMessageListener` + `DOCUMENT_START_SCRIPT` + Android `CredentialManager`.
 *
 * Fixes embedded WebView passkey sheet flash-closing on sites using "Login with passkey",
 * adds W3C `getClientCapabilities()` & conditional-create upgrade support, enforces
 * auto-sign-in / auto-create / passkey-upgrade preferences, and triggers in-tab recovery
 * when OS policy blocks WebView FIDO2 origin delegation.
 */
object KaspaWebAuthnBridge {
    private const val TAG = "KaspaWebAuthnBridge"
    private const val CHANNEL_NAME = "kaspaWebAuthnChannel"
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
                val requestObj = payload.optJSONObject("request")
                if (requestObj == null) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "Invalid WebAuthn registration request")
                    return
                }
                val isUpgrade = payload.optBoolean("isUpgrade", false)
                val nativeElapsedMs = payload.optLong("nativeElapsedMs", -1L)
                val nativeError = payload.optString("nativeError", "")

                if (!CredentialSettingsStore.webAuthEnabled.value) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "WebAuthn is disabled in browser privacy settings")
                    return
                }
                if (isUpgrade && !CredentialSettingsStore.allowPasskeyUpgradeEnabled.value) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "Passkey account upgrades are disabled in settings")
                    return
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "Passkeys require Android 9 (API 28) or higher")
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

                callbacks.scope.launch(Dispatchers.Main) {
                    val startMs = System.currentTimeMillis()
                    try {
                        callbacks.onStatusMessage(
                            if (isUpgrade) "Upgrading account to passkey for $host..."
                            else "Creating passkey for $host..."
                        )
                        val activity = findActivity(webView.context) ?: webView.context
                        val credentialManager = CredentialManager.create(webView.context)
                        val validRequestJson = requestObj.toString()
                        val request = CreatePublicKeyCredentialRequest(validRequestJson)
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
                            sendCredError(webView, expectedOrigin, replyProxy, callId, "Unsupported passkey registration response")
                        }
                    } catch (e: CreateCredentialException) {
                        val elapsed = System.currentTimeMillis() - startMs
                        Log.w(TAG, "CredentialManager createCredential failed (${e.javaClass.simpleName}, ${elapsed}ms): ${e.message}")
                        sendCredError(webView, expectedOrigin, replyProxy, callId, e.message ?: "Passkey creation failed")
                        val shouldOfferRecovery = when (e) {
                            is CreateCredentialCancellationException -> elapsed < 1500L || (nativeElapsedMs in 0..1500L)
                            is CreateCredentialNoCreateOptionException -> true
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
                        sendCredError(webView, expectedOrigin, replyProxy, callId, t.message ?: "Passkey creation failed")
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
                val requestObj = payload.optJSONObject("request")
                if (requestObj == null) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "Invalid WebAuthn assertion request")
                    return
                }
                val nativeElapsedMs = payload.optLong("nativeElapsedMs", -1L)
                val nativeError = payload.optString("nativeError", "")

                if (!CredentialSettingsStore.webAuthEnabled.value) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "WebAuthn is disabled in browser privacy settings")
                    return
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    sendCredError(webView, expectedOrigin, replyProxy, callId, "Passkeys require Android 9 (API 28) or higher")
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

                callbacks.scope.launch(Dispatchers.Main) {
                    val startMs = System.currentTimeMillis()
                    try {
                        callbacks.onStatusMessage("Requesting passkey verification for $host...")
                        val activity = findActivity(webView.context) ?: webView.context
                        val credentialManager = CredentialManager.create(webView.context)
                        val validRequestJson = requestObj.toString()
                        val option = GetPublicKeyCredentialOption(validRequestJson)
                        val request = GetCredentialRequest(listOf(option))
                        val response = credentialManager.getCredential(activity, request)
                        val cred = response.credential
                        if (cred is PublicKeyCredential) {
                            sendCredSuccess(webView, expectedOrigin, replyProxy, callId, cred.authenticationResponseJson)
                            val rpId = requestObj.optString("rpId", host).ifBlank { host }
                            callbacks.onStatusMessage("Passkey verified successfully ($rpId)")
                        } else {
                            sendCredError(webView, expectedOrigin, replyProxy, callId, "No matching passkey credential returned")
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
                        sendCredError(webView, expectedOrigin, replyProxy, callId, "No passkey accessible to embedded browser for $host")
                        callbacks.onRecoveryNeeded(
                            PasskeyRecoveryEvent(
                                url = pageUrl,
                                host = host,
                                operation = "get",
                                reason = buildRecoveryReason(e.message ?: nativeError, host)
                            )
                        )
                        callbacks.onStatusMessage("Passkey unavailable in WebView — Custom Tab handoff ready")
                    } catch (e: GetCredentialException) {
                        val elapsed = System.currentTimeMillis() - startMs
                        Log.w(TAG, "CredentialManager getCredential failed (${e.javaClass.simpleName}, ${elapsed}ms): ${e.message}")
                        sendCredError(webView, expectedOrigin, replyProxy, callId, e.message ?: "Passkey verification failed")
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
                        sendCredError(webView, expectedOrigin, replyProxy, callId, t.message ?: "Passkey verification failed")
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
        errorMessage: String
    ) {
        val reply = JSONObject().apply {
            put("type", "CRED_RESULT")
            put("callId", callId)
            put("ok", false)
            put("error", errorMessage)
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

                    function bufToB64(buf) {
                        if (!buf) return '';
                        var bytes = ArrayBuffer.isView(buf)
                            ? new Uint8Array(buf.buffer, buf.byteOffset, buf.byteLength)
                            : new Uint8Array(buf);
                        var bin = '';
                        for (var i = 0; i < bytes.byteLength; i++) bin += String.fromCharCode(bytes[i]);
                        return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
                    }

                    function sanitizeForCredentialManager(options) {
                        if (!options) return options;
                        function convert(val) {
                            if (val === null || val === undefined) return null;
                            if (val instanceof ArrayBuffer || ArrayBuffer.isView(val)) {
                                return bufToB64(val);
                            }
                            if (Array.isArray(val)) {
                                return val.map(convert);
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
                            if (typeof val === 'function') return undefined;
                            return val;
                        }
                        return convert(options);
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
                                    activeCb.reject(makeDomError(msg.error || 'Passkey operation failed', 'NotAllowedError'));
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
                                request: sanitizeForCredentialManager(pubKeyOptions)
                            };
                            if (extra) {
                                for (var k in extra) {
                                    if (Object.prototype.hasOwnProperty.call(extra, k)) payload[k] = extra[k];
                                }
                            }
                            try {
                                postBridgeMessage(payload);
                            } catch (e) {
                                delete window.__kaspaWebAuthnPending[callId];
                                reject(e);
                            }
                        });
                    }

                    if (typeof window.PublicKeyCredential === 'undefined') {
                        window.PublicKeyCredential = function() {};
                    }

                    window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable = function() {
                        return Promise.resolve(Boolean(getPolicy().webAuthEnabled));
                    };

                    window.PublicKeyCredential.isConditionalMediationAvailable = function() {
                        return Promise.resolve(false);
                    };

                    window.PublicKeyCredential.getClientCapabilities = function() {
                        var p = getPolicy();
                        var enabled = Boolean(p.webAuthEnabled);
                        return Promise.resolve({
                            conditionalCreate: enabled && Boolean(p.allowPasskeyUpgrade),
                            conditionalGet: false,
                            hybridTransport: enabled,
                            passkeyPlatformAuthenticator: enabled,
                            relatedOrigins: true,
                            signalAllAcceptedCredentials: enabled,
                            signalCurrentUserDetails: enabled,
                            signalUnknownCredential: enabled,
                            userVerifyingPlatformAuthenticator: enabled
                        });
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
                            return Promise.reject(makeDomError('Passkeys are disabled in Kaspa Browser settings', 'NotAllowedError'));
                        }

                        var isConditionalUpgrade = (options.mediation === 'conditional');
                        if (isConditionalUpgrade && !policy.allowPasskeyUpgrade) {
                            return Promise.reject(makeDomError('Passkey account upgrade disabled by user setting', 'NotAllowedError'));
                        }

                        var pubKey = options.publicKey;
                        var rpId = (pubKey.rp && pubKey.rp.id) || (window.location && window.location.hostname) || '';
                        var siteName = (pubKey.rp && pubKey.rp.name) || document.title || rpId;
                        var userName = (pubKey.user && (pubKey.user.name || pubKey.user.displayName)) || '';

                        var proceedCreate = function() {
                            // WebKit WebView does not natively support mediation: 'conditional' passkey upgrades;
                            // route conditional create directly through CredentialManager so upgrades succeed.
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
                                    if (window.kaspaWebAuthnChannel) {
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
                                nativeError: ''
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
                                return proceedCreate();
                            });
                        }

                        return proceedCreate();
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
                            return Promise.reject(makeDomError('Passkeys are disabled in Kaspa Browser settings', 'NotAllowedError'));
                        }

                        // Conditional mediation background listeners must not fail/close noisy recovery dialogs
                        if (options.mediation === 'conditional') {
                            return Promise.reject(makeDomError('Conditional mediation not active in WebView', 'NotAllowedError'));
                        }

                        var pubKey = options.publicKey;
                        var rpId = pubKey.rpId || (window.location && window.location.hostname) || '';

                        var proceedGet = function() {
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
                                    if (window.kaspaWebAuthnChannel) {
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
                                return proceedGet();
                            });
                        }

                        return proceedGet();
                    };
                } catch (e) {
                    console.warn('Kaspa WebAuthn bridge setup notice', e);
                }
            })();
        """.trimIndent()
    }
}

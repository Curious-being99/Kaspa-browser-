package com.example.network

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.credentials.CredentialManager
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.example.viewmodel.DecentralViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class KaspaWebAuthnBridge(
    private val context: Context,
    private val webViewProvider: () -> WebView?,
    private val viewModel: DecentralViewModel,
    private val scope: CoroutineScope
) {
    private val tag = "KaspaWebAuthnBridge"

    private fun findActivity(ctx: Context): Activity? {
        var current = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    @JavascriptInterface
    fun isAvailable(): Boolean {
        return viewModel.webAuthEnabled.value
    }

    @JavascriptInterface
    fun createCredential(requestJson: String, callbackId: String) {
        if (!viewModel.webAuthEnabled.value) {
            rejectCallback(callbackId, "WebAuthn is disabled in browser privacy settings")
            return
        }

        scope.launch(Dispatchers.Main) {
            try {
                viewModel.setStatusMessage("FIDO2 / WebAuthn passkey registration requested...")
                val activity = findActivity(context) ?: context
                val credentialManager = CredentialManager.create(context)
                val request = CreatePublicKeyCredentialRequest(requestJson)
                val response = credentialManager.createCredential(activity, request)

                if (response is CreatePublicKeyCredentialResponse) {
                    val resultJson = response.registrationResponseJson
                    resolveCallback(callbackId, resultJson)
                    viewModel.setStatusMessage("Passkey registered successfully via FIDO2")
                } else {
                    rejectCallback(callbackId, "Unsupported credential response type")
                }
            } catch (e: Exception) {
                Log.w(tag, "WebAuthn createCredential failed: ${e.message}", e)
                val userMsg = e.message ?: "Authentication error or user cancelled"
                rejectCallback(callbackId, userMsg)
                if (userMsg.contains("RP ID", ignoreCase = true) || userMsg.contains("relying party", ignoreCase = true)) {
                    viewModel.setShowWebAuthnRpIdDialog(true)
                    viewModel.setStatusMessage("Passkey RP ID restricted: Tap 'More options' on GitHub for Authenticator or 2FA.")
                } else {
                    viewModel.setStatusMessage("Passkey registration: $userMsg")
                }
            }
        }
    }

    @JavascriptInterface
    fun getCredential(requestJson: String, callbackId: String) {
        if (!viewModel.webAuthEnabled.value) {
            rejectCallback(callbackId, "WebAuthn is disabled in browser privacy settings")
            return
        }

        scope.launch(Dispatchers.Main) {
            try {
                viewModel.setStatusMessage("FIDO2 / WebAuthn passkey assertion requested...")
                val activity = findActivity(context) ?: context
                val credentialManager = CredentialManager.create(context)
                val option = GetPublicKeyCredentialOption(requestJson)
                val request = GetCredentialRequest(listOf(option))
                val response = credentialManager.getCredential(activity, request)

                val cred = response.credential
                if (cred is PublicKeyCredential) {
                    val resultJson = cred.authenticationResponseJson
                    resolveCallback(callbackId, resultJson)
                    viewModel.setStatusMessage("Passkey verified successfully via FIDO2")
                } else {
                    rejectCallback(callbackId, "No matching public key credential found")
                }
            } catch (e: Exception) {
                Log.w(tag, "WebAuthn getCredential failed: ${e.message}", e)
                val userMsg = e.message ?: "Verification error or user cancelled"
                rejectCallback(callbackId, userMsg)
                if (userMsg.contains("RP ID", ignoreCase = true) || userMsg.contains("relying party", ignoreCase = true)) {
                    viewModel.setShowWebAuthnRpIdDialog(true)
                    viewModel.setStatusMessage("Passkey RP ID restricted: Tap 'More options' on GitHub for Authenticator or 2FA.")
                } else {
                    viewModel.setStatusMessage("Passkey verification: $userMsg")
                }
            }
        }
    }

    private fun resolveCallback(callbackId: String, resultJson: String) {
        val wv = webViewProvider() ?: return
        val escaped = JSONObject.quote(resultJson)
        val script = "window.__kaspaResolve && window.__kaspaResolve('$callbackId', $escaped);"
        wv.post { wv.evaluateJavascript(script, null) }
    }

    private fun rejectCallback(callbackId: String, errorMessage: String) {
        val wv = webViewProvider() ?: return
        val escaped = JSONObject.quote(errorMessage)
        val script = "window.__kaspaReject && window.__kaspaReject('$callbackId', $escaped);"
        wv.post { wv.evaluateJavascript(script, null) }
    }

    companion object {
        fun getInjectionScript(): String {
            return """
                (function() {
                    try {
                        if (window.__kaspaWebAuthnInjected) return;
                        window.__kaspaWebAuthnInjected = true;

                    function b64ToBuf(b64url) {
                        if (!b64url) return new Uint8Array(0).buffer;
                        var b64 = b64url.replace(/-/g, '+').replace(/_/g, '/');
                        var pad = b64.length % 4;
                        if (pad) b64 += '='.repeat(4 - pad);
                        var bin = atob(b64);
                        var bytes = new Uint8Array(bin.length);
                        for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
                        return bytes.buffer;
                    }

                    function bufToB64(buf) {
                        if (!buf) return '';
                        var bytes = new Uint8Array(buf.buffer || buf);
                        var bin = '';
                        for (var i = 0; i < bytes.byteLength; i++) bin += String.fromCharCode(bytes[i]);
                        return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
                    }

                    function sanitizeForCredentialManager(options) {
                        if (!options) return options;
                        function convert(val) {
                            if (val === null || val === undefined) return val;
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
                                        obj[k] = convert(val[k]);
                                    }
                                }
                                return obj;
                            }
                            return val;
                        }
                        return convert(options);
                    }

                    window.__kaspaCallbacks = window.__kaspaCallbacks || {};
                    window.__kaspaResolve = function(id, resJson) {
                        var cb = window.__kaspaCallbacks[id];
                        if (cb) {
                            delete window.__kaspaCallbacks[id];
                            try {
                                var data = typeof resJson === 'string' ? JSON.parse(resJson) : resJson;
                                var pubKeyCred = {
                                    id: data.id,
                                    rawId: b64ToBuf(data.rawId || data.id),
                                    type: data.type || 'public-key',
                                    authenticatorAttachment: data.authenticatorAttachment || 'platform',
                                    response: {},
                                    getClientExtensionResults: function() { return {}; },
                                    toJSON: function() { return data; }
                                };
                                if (data.response) {
                                    if (data.response.clientDataJSON) {
                                        pubKeyCred.response.clientDataJSON = b64ToBuf(data.response.clientDataJSON);
                                    }
                                    if (data.response.attestationObject) {
                                        pubKeyCred.response.attestationObject = b64ToBuf(data.response.attestationObject);
                                        pubKeyCred.response.getTransports = function() { return data.response.transports || ['internal']; };
                                        pubKeyCred.response.getAuthenticatorData = function() { return b64ToBuf(data.response.authenticatorData || ''); };
                                        pubKeyCred.response.getPublicKey = function() { return data.response.publicKey ? b64ToBuf(data.response.publicKey) : null; };
                                        pubKeyCred.response.getPublicKeyAlgorithm = function() { return data.response.publicKeyAlgorithm || -7; };
                                    }
                                    if (data.response.authenticatorData) {
                                        pubKeyCred.response.authenticatorData = b64ToBuf(data.response.authenticatorData);
                                    }
                                    if (data.response.signature) {
                                        pubKeyCred.response.signature = b64ToBuf(data.response.signature);
                                    }
                                    if (data.response.userHandle) {
                                        pubKeyCred.response.userHandle = b64ToBuf(data.response.userHandle);
                                    }
                                }
                                cb.resolve(pubKeyCred);
                            } catch(e) {
                                cb.reject(e);
                            }
                        }
                    };

                    window.__kaspaReject = function(id, err) {
                        var cb = window.__kaspaCallbacks[id];
                        if (cb) {
                            delete window.__kaspaCallbacks[id];
                            var error = new Error(err || 'WebAuthn authentication failed');
                            error.name = 'NotAllowedError';
                            cb.reject(error);
                        }
                    };

                    if (typeof window.PublicKeyCredential === 'undefined') {
                        window.PublicKeyCredential = function() {};
                    }
                    window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable = function() {
                        if (window.KaspaWebAuthnBridge) {
                            return Promise.resolve(window.KaspaWebAuthnBridge.isAvailable());
                        }
                        return Promise.resolve(true);
                    };
                    window.PublicKeyCredential.isConditionalMediationAvailable = function() {
                        return Promise.resolve(false);
                    };

                    if (!navigator.credentials) {
                        navigator.credentials = {};
                    }

                    var originalCreate = navigator.credentials.create ? navigator.credentials.create.bind(navigator.credentials) : null;
                    var originalGet = navigator.credentials.get ? navigator.credentials.get.bind(navigator.credentials) : null;

                    function executeViaBridge(type, pubKeyOptions) {
                        return new Promise(function(resolve, reject) {
                            var callId = 'cb_' + Math.random().toString(36).substr(2, 9) + '_' + Date.now();
                            window.__kaspaCallbacks[callId] = { resolve: resolve, reject: reject };
                            var jsonStr = JSON.stringify(sanitizeForCredentialManager(pubKeyOptions));
                            try {
                                if (type === 'create') {
                                    window.KaspaWebAuthnBridge.createCredential(jsonStr, callId);
                                } else {
                                    window.KaspaWebAuthnBridge.getCredential(jsonStr, callId);
                                }
                            } catch(e) {
                                delete window.__kaspaCallbacks[callId];
                                reject(e);
                            }
                        });
                    }

                    navigator.credentials.create = function(options) {
                        if (!options || !options.publicKey) {
                            if (originalCreate) return originalCreate(options);
                            return Promise.reject(new Error('Invalid options for credentials.create'));
                        }
                        if (originalCreate) {
                            return originalCreate(options).catch(function(err) {
                                if (window.KaspaWebAuthnBridge && window.KaspaWebAuthnBridge.isAvailable()) {
                                    return executeViaBridge('create', options.publicKey);
                                }
                                throw err;
                            });
                        }
                        return executeViaBridge('create', options.publicKey);
                    };

                    navigator.credentials.get = function(options) {
                        if (!options || !options.publicKey) {
                            if (originalGet) return originalGet(options);
                            return Promise.reject(new Error('Invalid options for credentials.get'));
                        }
                        if (originalGet) {
                            return originalGet(options).catch(function(err) {
                                if (window.KaspaWebAuthnBridge && window.KaspaWebAuthnBridge.isAvailable()) {
                                    return executeViaBridge('get', options.publicKey);
                                }
                                throw err;
                            });
                        }
                        return executeViaBridge('get', options.publicKey);
                    };
                    } catch(e) {
                        console.warn('WebAuthn bridge setup failed', e);
                    }
                })();
            """.trimIndent()
        }
    }
}

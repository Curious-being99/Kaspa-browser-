package com.example.security

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.network.KaspaPrivacyEngine
import org.json.JSONObject
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

data class PendingCredentialSave(
    val url: String,
    val host: String,
    val siteName: String,
    val username: String,
    val password: String
)

data class AutofillFormEvent(
    val url: String,
    val host: String,
    val isNewPasswordField: Boolean
)

/**
 * Origin-checked, main-frame-only WebMessageListener bridge for in-browser password autofill
 * and form capture. Never exposes @JavascriptInterface objects to web content and strictly
 * skips subframes, Cloudflare Turnstile / CAPTCHA origins, and payment widgets.
 */
object PasswordAutofillBridge {
    private const val BRIDGE_NAME = "kaspaVaultBridge"
    private val mainHandler = Handler(Looper.getMainLooper())

    private class BridgeCallbacks(
        @Volatile var onDetected: (AutofillFormEvent) -> Unit,
        @Volatile var onSubmitted: (PendingCredentialSave) -> Unit
    )

    private val callbackMap = Collections.synchronizedMap(WeakHashMap<WebView, BridgeCallbacks>())

    private val EXCLUDED_HOSTS = setOf(
        "challenges.cloudflare.com",
        "cloudflare.com",
        "hcaptcha.com",
        "recaptcha.net",
        "arkoselabs.com",
        "octocaptcha.com",
        "geetest.com",
        "friendlycaptcha.com",
        "captcha-delivery.com",
        "datadome.co",
        "stripe.com",
        "paypal.com"
    )

    fun isUrlEligibleForAutofill(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "https") return false
        val host = parsed.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return false
        if (host.isBlank()) return false
        if (EXCLUDED_HOSTS.any { host == it || host.endsWith(".$it") }) return false
        val path = parsed.path?.lowercase(Locale.ROOT) ?: ""
        val query = parsed.query?.lowercase(Locale.ROOT) ?: ""
        if (path.contains("/cdn-cgi/") || path.contains("/turnstile") || query.contains("__cf_chl_")) return false
        if (KaspaPrivacyEngine.isEssentialAuthOrCaptchaDomain(host) &&
            (host.contains("cloudflare") || host.contains("captcha") || host.contains("arkose") ||
                host.contains("geetest") || host.contains("datadome"))
        ) {
            return false
        }
        return true
    }

    fun install(
        webView: WebView,
        onPasswordFormDetected: (AutofillFormEvent) -> Unit,
        onCredentialSubmitted: (PendingCredentialSave) -> Unit
    ) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        val existing = synchronized(callbackMap) {
            val prev = callbackMap[webView]
            if (prev != null) {
                prev.onDetected = onPasswordFormDetected
                prev.onSubmitted = onCredentialSubmitted
                true
            } else {
                callbackMap[webView] = BridgeCallbacks(onPasswordFormDetected, onCredentialSubmitted)
                false
            }
        }
        if (existing) return

        try {
            WebViewCompat.addWebMessageListener(
                webView,
                BRIDGE_NAME,
                setOf("*")
            ) { view, message, sourceOrigin, isMainFrame, _ ->
                if (!isMainFrame) return@addWebMessageListener
                val currentUrl = view.url.orEmpty()
                if (!isUrlEligibleForAutofill(currentUrl)) return@addWebMessageListener
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
                val host = PasswordVaultRepository.normalizeHost(currentUrl) ?: return@addWebMessageListener
                val callbacks = callbackMap[view] ?: return@addWebMessageListener
                when (payload.optString("type")) {
                    "PASSWORD_FORM_FOUND" -> {
                        val isNew = payload.optBoolean("isNewPassword", false)
                        mainHandler.post {
                            callbacks.onDetected(
                                AutofillFormEvent(
                                    url = currentUrl,
                                    host = host,
                                    isNewPasswordField = isNew
                                )
                            )
                        }
                    }
                    "CREDENTIAL_SUBMITTED" -> {
                        val user = payload.optString("username", "").trim()
                        val pass = payload.optString("password", "")
                        val siteName = payload.optString("siteName", host).trim().ifBlank { host }
                        if (pass.isNotBlank()) {
                            mainHandler.post {
                                callbacks.onSubmitted(
                                    PendingCredentialSave(
                                        url = currentUrl,
                                        host = host,
                                        siteName = siteName,
                                        username = user,
                                        password = pass
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            callbackMap.remove(webView)
        }
    }

    fun scanPageForPasswordForms(webView: WebView?, url: String?) {
        if (webView == null || !isUrlEligibleForAutofill(url)) return
        webView.post {
            try {
                webView.evaluateJavascript(JS_FORM_SCANNER, null)
            } catch (_: Throwable) {}
        }
    }

    fun fillCredentials(
        webView: WebView?,
        username: String,
        password: String,
        expectedHost: String? = null
    ) {
        if (webView == null || !isUrlEligibleForAutofill(webView.url)) return
        val u = JSONObject.quote(username)
        val p = JSONObject.quote(password)
        val js = """
            (function() {
                try {
                    if (window.top !== window.self) return false;
                    var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    function applyVal(el, val) {
                        if (!el) return;
                        el.focus();
                        if (setter) setter.call(el, val); else el.value = val;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    var pwds = Array.prototype.slice.call(document.querySelectorAll('input[type="password"]'))
                        .filter(function(el) { return !el.disabled && el.offsetParent !== null; });
                    if (pwds.length === 0) {
                        pwds = Array.prototype.slice.call(document.querySelectorAll('input[type="password"]'));
                    }
                    var pwdEl = pwds[0] || null;
                    var form = pwdEl ? pwdEl.form : null;
                    var root = form || document;
                    var userCandidates = Array.prototype.slice.call(
                        root.querySelectorAll('input[type="email"], input[autocomplete*="username"], input[autocomplete="email"], input[name*="user" i], input[name*="mail" i], input[name*="login" i], input[type="text"]')
                    ).filter(function(el) {
                        return el !== pwdEl && !el.disabled && el.type !== 'hidden' && el.type !== 'search';
                    });
                    if ($u && userCandidates.length > 0) {
                        applyVal(userCandidates[0], $u);
                    }
                    if ($p && pwdEl) {
                        applyVal(pwdEl, $p);
                    }
                    return true;
                } catch(e) { return false; }
            })();
        """.trimIndent()
        webView.post {
            try {
                val currentUrl = webView.url
                if (!isUrlEligibleForAutofill(currentUrl)) return@post
                if (!expectedHost.isNullOrBlank()) {
                    val currentHost = PasswordVaultRepository.normalizeHost(currentUrl) ?: return@post
                    if (!PasswordVaultRepository.isHostMatch(expectedHost, currentHost)) return@post
                }
                webView.evaluateJavascript(js, null)
            } catch (_: Throwable) {}
        }
    }

    fun fillGeneratedPassword(webView: WebView?, generatedPassword: String) {
        if (webView == null || !isUrlEligibleForAutofill(webView.url)) return
        val p = JSONObject.quote(generatedPassword)
        val js = """
            (function() {
                try {
                    if (window.top !== window.self) return false;
                    var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    function applyVal(el, val) {
                        if (!el) return;
                        el.focus();
                        if (setter) setter.call(el, val); else el.value = val;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    var newPwds = Array.prototype.slice.call(
                        document.querySelectorAll('input[type="password"][autocomplete="new-password"]')
                    ).filter(function(el) { return !el.disabled; });
                    var targets = newPwds.length > 0 ? newPwds : Array.prototype.slice.call(
                        document.querySelectorAll('input[type="password"]')
                    ).filter(function(el) { return !el.disabled; });
                    targets.forEach(function(el) { applyVal(el, $p); });
                    return targets.length > 0;
                } catch(e) { return false; }
            })();
        """.trimIndent()
        webView.post {
            try {
                if (!isUrlEligibleForAutofill(webView.url)) return@post
                webView.evaluateJavascript(js, null)
            } catch (_: Throwable) {}
        }
    }

    val JS_FORM_SCANNER = """
        (function() {
            try {
                if (window.top !== window.self) return;
                if (!window.kaspaVaultBridge) return;
                var href = (window.location && window.location.href || '').toLowerCase();
                var host = (window.location && window.location.hostname || '').toLowerCase();
                if (href.indexOf('https://') !== 0) return;
                if (host.indexOf('cloudflare.com') !== -1 || host.indexOf('hcaptcha.com') !== -1 ||
                    host.indexOf('recaptcha.net') !== -1 || host.indexOf('arkoselabs.com') !== -1 ||
                    host.indexOf('octocaptcha.com') !== -1 || host.indexOf('geetest.com') !== -1 ||
                    host.indexOf('friendlycaptcha.com') !== -1 || host.indexOf('datadome') !== -1 ||
                    host.indexOf('stripe.com') !== -1 || host.indexOf('paypal.com') !== -1 ||
                    href.indexOf('/cdn-cgi/') !== -1 || href.indexOf('__cf_chl_') !== -1 ||
                    (document.title && document.title.indexOf('Just a moment') !== -1) ||
                    document.getElementById('challenge-running') ||
                    document.getElementById('challenge-form') ||
                    document.querySelector('script[src*="/cdn-cgi/challenge-platform"], div.cf-turnstile')) {
                    return;
                }
                var pwdInputs = Array.prototype.slice.call(document.querySelectorAll('input[type="password"]'));
                if (!pwdInputs || pwdInputs.length === 0) return;

                var hasNewPwd = pwdInputs.some(function(el) {
                    return (el.getAttribute('autocomplete') || '').toLowerCase() === 'new-password';
                });

                try {
                    window.kaspaVaultBridge.postMessage(JSON.stringify({
                        type: 'PASSWORD_FORM_FOUND',
                        isNewPassword: hasNewPwd
                    }));
                } catch(_) {}

                if (window.__kaspaVaultListenersAttached) return;
                window.__kaspaVaultListenersAttached = true;

                function findUsernameFor(pwdEl) {
                    var scope = (pwdEl && pwdEl.form) ? pwdEl.form : document;
                    var candidates = Array.prototype.slice.call(
                        scope.querySelectorAll('input[type="email"], input[autocomplete*="username"], input[autocomplete="email"], input[name*="user" i], input[name*="mail" i], input[name*="login" i], input[type="text"]')
                    );
                    for (var i = 0; i < candidates.length; i++) {
                        var v = (candidates[i].value || '').trim();
                        if (v.length > 0 && candidates[i].type !== 'password' && candidates[i].type !== 'hidden') {
                            return v;
                        }
                    }
                    return '';
                }

                var lastCapturedSignature = '';
                var lastCapturedAt = 0;

                function captureFromScope(scopeEl) {
                    var root = scopeEl || document;
                    var pInputs = Array.prototype.slice.call(root.querySelectorAll('input[type="password"]'));
                    if (pInputs.length === 0 && root !== document) {
                        pInputs = Array.prototype.slice.call(document.querySelectorAll('input[type="password"]'));
                    }
                    for (var i = 0; i < pInputs.length; i++) {
                        var p = pInputs[i].value || '';
                        if (p.length >= 1) {
                            var u = findUsernameFor(pInputs[i]);
                            var sig = u + '\u0000' + p;
                            var now = Date.now();
                            if (sig === lastCapturedSignature && (now - lastCapturedAt) < 1800) {
                                break;
                            }
                            lastCapturedSignature = sig;
                            lastCapturedAt = now;
                            try {
                                window.kaspaVaultBridge.postMessage(JSON.stringify({
                                    type: 'CREDENTIAL_SUBMITTED',
                                    username: u,
                                    password: p,
                                    siteName: document.title || host
                                }));
                            } catch(_) {}
                            break;
                        }
                    }
                }

                document.addEventListener('submit', function(e) {
                    if (e && e.target && e.target.querySelector && e.target.querySelector('input[type="password"]')) {
                        captureFromScope(e.target);
                    } else if (document.querySelector('input[type="password"]')) {
                        captureFromScope(document);
                    }
                }, true);

                // SPA support: capture login/sign-in button clicks or Enter key when a password field is filled
                document.addEventListener('click', function(e) {
                    var btn = e && e.target && e.target.closest ? e.target.closest('button, input[type="submit"], input[type="button"], [role="button"]') : null;
                    if (!btn) return;
                    var scope = btn.form || (btn.closest ? btn.closest('form') : null) || document;
                    var hasFilledPwd = Array.prototype.slice.call(scope.querySelectorAll('input[type="password"]')).some(function(el) {
                        return (el.value || '').length >= 1;
                    });
                    if (hasFilledPwd) {
                        captureFromScope(scope);
                    }
                }, true);

                document.addEventListener('keydown', function(e) {
                    if (e && (e.key === 'Enter' || e.keyCode === 13) && e.target && e.target.type === 'password' && (e.target.value || '').length >= 1) {
                        captureFromScope(e.target.form || document);
                    }
                }, true);

                pwdInputs.forEach(function(pwdEl) {
                    pwdEl.addEventListener('focus', function() {
                        try {
                            window.kaspaVaultBridge.postMessage(JSON.stringify({
                                type: 'PASSWORD_FORM_FOUND',
                                isNewPassword: (pwdEl.getAttribute('autocomplete') || '').toLowerCase() === 'new-password'
                            }));
                        } catch(_) {}
                    }, true);
                });
            } catch(_) {}
        })();
    """.trimIndent()
}

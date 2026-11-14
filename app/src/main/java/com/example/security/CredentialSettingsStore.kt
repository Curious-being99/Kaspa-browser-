package com.example.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persistent preference & state store for Password Manager, Passkeys, Auto Sign-In,
 * and Password Breach Alerts. Shared consistently across main browser tabs and
 * standalone PWA activities.
 */
object CredentialSettingsStore {
    const val PREFS_NAME = "kaspa_browser_settings"
    private const val ALERTS_PREFS_NAME = "kaspa_password_alerts"

    const val KEY_OFFER_TO_SAVE_PASSWORDS = "offer_to_save_passwords"
    const val KEY_AUTO_CREATE_PASSKEY = "auto_create_passkey_enabled"
    const val KEY_ALLOW_PASSKEY_UPGRADE = "allow_passkey_upgrade_enabled"
    const val KEY_AUTO_SIGN_IN = "auto_sign_in_enabled"
    const val KEY_PASSWORD_ALERTS = "password_alerts_enabled"
    const val KEY_WEB_AUTH_ENABLED = "web_auth_enabled"

    private const val KEY_BREACH_MAP_JSON = "breached_credentials_json"
    private const val KEY_NOTIFIED_SIGNATURES_JSON = "notified_signatures_json"
    private const val KEY_LAST_CHECK_AT_MS = "last_breach_check_at_ms"

    private val initialized = AtomicBoolean(false)

    private val _offerToSavePasswords = MutableStateFlow(true)
    val offerToSavePasswords: StateFlow<Boolean> = _offerToSavePasswords.asStateFlow()

    private val _autoCreatePasskeyEnabled = MutableStateFlow(true)
    val autoCreatePasskeyEnabled: StateFlow<Boolean> = _autoCreatePasskeyEnabled.asStateFlow()

    private val _allowPasskeyUpgradeEnabled = MutableStateFlow(true)
    val allowPasskeyUpgradeEnabled: StateFlow<Boolean> = _allowPasskeyUpgradeEnabled.asStateFlow()

    private val _autoSignInEnabled = MutableStateFlow(true)
    val autoSignInEnabled: StateFlow<Boolean> = _autoSignInEnabled.asStateFlow()

    private val _passwordAlertsEnabled = MutableStateFlow(true)
    val passwordAlertsEnabled: StateFlow<Boolean> = _passwordAlertsEnabled.asStateFlow()

    private val _webAuthEnabled = MutableStateFlow(true)
    val webAuthEnabled: StateFlow<Boolean> = _webAuthEnabled.asStateFlow()

    private val _breachedCredentials = MutableStateFlow<Map<String, Int>>(emptyMap())
    val breachedCredentials: StateFlow<Map<String, Int>> = _breachedCredentials.asStateFlow()

    private val _lastBreachCheckAtMs = MutableStateFlow(0L)
    val lastBreachCheckAtMs: StateFlow<Long> = _lastBreachCheckAtMs.asStateFlow()

    private val _isCheckingBreaches = MutableStateFlow(false)
    val isCheckingBreaches: StateFlow<Boolean> = _isCheckingBreaches.asStateFlow()

    private fun settingsPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun alertsPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(ALERTS_PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        reloadFromPrefs(context)
    }

    fun reloadFromPrefs(context: Context) {
        val prefs = settingsPrefs(context)
        _offerToSavePasswords.value = prefs.getBoolean(KEY_OFFER_TO_SAVE_PASSWORDS, true)
        _autoCreatePasskeyEnabled.value = prefs.getBoolean(KEY_AUTO_CREATE_PASSKEY, true)
        _allowPasskeyUpgradeEnabled.value = prefs.getBoolean(KEY_ALLOW_PASSKEY_UPGRADE, true)
        _autoSignInEnabled.value = prefs.getBoolean(KEY_AUTO_SIGN_IN, true)
        _passwordAlertsEnabled.value = prefs.getBoolean(KEY_PASSWORD_ALERTS, true)
        _webAuthEnabled.value = prefs.getBoolean(KEY_WEB_AUTH_ENABLED, true)

        val aPrefs = alertsPrefs(context)
        _lastBreachCheckAtMs.value = aPrefs.getLong(KEY_LAST_CHECK_AT_MS, 0L)
        _breachedCredentials.value = parseIntMap(aPrefs.getString(KEY_BREACH_MAP_JSON, null))
    }

    fun setOfferToSavePasswords(context: Context, enabled: Boolean) {
        init(context)
        _offerToSavePasswords.value = enabled
        settingsPrefs(context).edit().putBoolean(KEY_OFFER_TO_SAVE_PASSWORDS, enabled).apply()
    }

    fun setAutoCreatePasskeyEnabled(context: Context, enabled: Boolean) {
        init(context)
        _autoCreatePasskeyEnabled.value = enabled
        settingsPrefs(context).edit().putBoolean(KEY_AUTO_CREATE_PASSKEY, enabled).apply()
    }

    fun setAllowPasskeyUpgradeEnabled(context: Context, enabled: Boolean) {
        init(context)
        _allowPasskeyUpgradeEnabled.value = enabled
        settingsPrefs(context).edit().putBoolean(KEY_ALLOW_PASSKEY_UPGRADE, enabled).apply()
    }

    fun setAutoSignInEnabled(context: Context, enabled: Boolean) {
        init(context)
        _autoSignInEnabled.value = enabled
        settingsPrefs(context).edit().putBoolean(KEY_AUTO_SIGN_IN, enabled).apply()
    }

    fun setPasswordAlertsEnabled(context: Context, enabled: Boolean) {
        init(context)
        _passwordAlertsEnabled.value = enabled
        settingsPrefs(context).edit().putBoolean(KEY_PASSWORD_ALERTS, enabled).apply()
    }

    fun setWebAuthEnabled(context: Context, enabled: Boolean) {
        init(context)
        _webAuthEnabled.value = enabled
        settingsPrefs(context).edit().putBoolean(KEY_WEB_AUTH_ENABLED, enabled).apply()
    }

    fun setCheckingBreaches(checking: Boolean) {
        _isCheckingBreaches.value = checking
    }

    fun updateBreachResults(context: Context, breachedMap: Map<String, Int>, checkedAtMs: Long) {
        init(context)
        val cleaned = breachedMap.filterValues { it > 0 }
        _breachedCredentials.value = cleaned
        _lastBreachCheckAtMs.value = checkedAtMs
        val json = JSONObject()
        cleaned.forEach { (id, count) -> json.put(id, count) }
        alertsPrefs(context).edit()
            .putString(KEY_BREACH_MAP_JSON, json.toString())
            .putLong(KEY_LAST_CHECK_AT_MS, checkedAtMs)
            .apply()
    }

    fun removeCredentialBreachState(context: Context, credentialId: String) {
        init(context)
        val current = _breachedCredentials.value
        if (current.containsKey(credentialId)) {
            val next = current.toMutableMap().apply { remove(credentialId) }
            updateBreachResults(context, next, _lastBreachCheckAtMs.value)
        }
        val aPrefs = alertsPrefs(context)
        val sigMap = parseStringMap(aPrefs.getString(KEY_NOTIFIED_SIGNATURES_JSON, null)).toMutableMap()
        if (sigMap.remove(credentialId) != null) {
            aPrefs.edit().putString(KEY_NOTIFIED_SIGNATURES_JSON, JSONObject(sigMap).toString()).apply()
        }
    }

    /**
     * Returns only breached entries whose (credentialId -> sha1Prefix+count) has not been
     * notified yet, and records them so users do not get duplicate alerts on every unlock.
     */
    fun markAndFilterUnnotifiedBreaches(
        context: Context,
        candidates: List<Pair<String, String>> // (credentialId, signature)
    ): Set<String> {
        init(context)
        if (candidates.isEmpty()) return emptySet()
        val aPrefs = alertsPrefs(context)
        val prev = parseStringMap(aPrefs.getString(KEY_NOTIFIED_SIGNATURES_JSON, null)).toMutableMap()
        val freshIds = linkedSetOf<String>()
        for ((credId, sig) in candidates) {
            if (prev[credId] != sig) {
                freshIds.add(credId)
                prev[credId] = sig
            }
        }
        // Prune IDs no longer in breached state if signature map gets large
        val activeIds = _breachedCredentials.value.keys
        prev.keys.retainAll { it in activeIds || it in freshIds }
        if (freshIds.isNotEmpty()) {
            aPrefs.edit().putString(KEY_NOTIFIED_SIGNATURES_JSON, JSONObject(prev).toString()).apply()
        }
        return freshIds
    }

    private fun parseIntMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = linkedMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val v = obj.optInt(key, 0)
                if (v > 0) result[key] = v
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun parseStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = linkedMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val v = obj.optString(key, "")
                if (v.isNotBlank()) result[key] = v
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}

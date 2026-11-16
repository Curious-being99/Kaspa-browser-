package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Dedicated, wallet-isolated store for optional app-level browser privacy lock.
 *
 * Security & Separation Rules (per RULES.md):
 * 1. Completely separate from wallet signing (`isWalletLocked`, `wallet_pwd_hash`, seeds).
 *    Unlocking the browser MUST NEVER count as approving a payment or unlocking seeds.
 * 2. Resetting or recovering a forgotten browser lock password MUST NEVER wipe or reset
 *    any wallet, mnemonic seed, or keystore entry. Recovery falls back to OS device credentials.
 */
object BrowserLockStore {
    const val PREFS_NAME = "kazium_browser_app_lock"
    private const val KEY_LOCK_ENABLED = "browser_lock_enabled"
    private const val KEY_PWD_HASH = "browser_lock_pwd_hash"
    private const val KEY_PWD_SALT = "browser_lock_pwd_salt"
    private const val KEY_PWD_ITERATIONS = "browser_lock_pwd_iterations"
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256

    private val initialized = AtomicBoolean(false)
    private val secureRandom = SecureRandom()

    private val _isBrowserLockEnabled = MutableStateFlow(false)
    val isBrowserLockEnabled: StateFlow<Boolean> = _isBrowserLockEnabled.asStateFlow()

    private val _hasCustomLockPassword = MutableStateFlow(false)
    val hasCustomLockPassword: StateFlow<Boolean> = _hasCustomLockPassword.asStateFlow()

    // Starts unlocked until init() sees KEY_LOCK_ENABLED == true.
    private val _isBrowserUnlocked = MutableStateFlow(true)
    val isBrowserUnlocked: StateFlow<Boolean> = _isBrowserUnlocked.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        reloadFromPrefs(context)
    }

    fun reloadFromPrefs(context: Context) {
        val p = prefs(context)
        val enabled = p.getBoolean(KEY_LOCK_ENABLED, false)
        val hasPwd = !p.getString(KEY_PWD_HASH, null).isNullOrBlank() &&
            !p.getString(KEY_PWD_SALT, null).isNullOrBlank()
        _isBrowserLockEnabled.value = enabled
        _hasCustomLockPassword.value = hasPwd
        // Require unlock on cold launch when lock is enabled; unlocked otherwise.
        _isBrowserUnlocked.value = !enabled
    }

    /**
     * Enables the app-level browser privacy lock with optional fallback password.
     * Immediately marks the active session unlocked because the user enabled it interactively.
     */
    fun enableBrowserLock(context: Context, optionalPassword: String? = null) {
        init(context)
        val editor = prefs(context).edit().putBoolean(KEY_LOCK_ENABLED, true)
        val cleanPwd = optionalPassword?.trim().orEmpty()
        if (cleanPwd.isNotEmpty()) {
            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val hash = pbkdf2(cleanPwd, salt, PBKDF2_ITERATIONS)
            editor.putString(KEY_PWD_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            editor.putString(KEY_PWD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            editor.putInt(KEY_PWD_ITERATIONS, PBKDF2_ITERATIONS)
            _hasCustomLockPassword.value = true
        }
        editor.apply()
        _isBrowserLockEnabled.value = true
        _isBrowserUnlocked.value = true
    }

    /**
     * Updates or clears only the optional browser privacy lock password.
     * NEVER touches any wallet, mnemonic, vault, or seed data.
     */
    fun setCustomLockPassword(context: Context, password: String) {
        init(context)
        val clean = password.trim()
        require(clean.length >= 4) { "Browser lock password must be at least 4 characters." }
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val hash = pbkdf2(clean, salt, PBKDF2_ITERATIONS)
        prefs(context).edit()
            .putString(KEY_PWD_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_PWD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putInt(KEY_PWD_ITERATIONS, PBKDF2_ITERATIONS)
            .apply()
        _hasCustomLockPassword.value = true
    }

    /**
     * Clears a forgotten custom browser lock password after successful OS device-credential unlock.
     * Keeps browser biometric/device lock active and NEVER touches wallet seeds.
     */
    fun clearCustomLockPasswordAfterDeviceUnlock(context: Context) {
        init(context)
        prefs(context).edit()
            .remove(KEY_PWD_HASH)
            .remove(KEY_PWD_SALT)
            .remove(KEY_PWD_ITERATIONS)
            .apply()
        _hasCustomLockPassword.value = false
    }

    /**
     * Disables browser app-level privacy lock cleanly without touching wallet or vault records.
     */
    fun disableBrowserLock(context: Context) {
        init(context)
        prefs(context).edit()
            .putBoolean(KEY_LOCK_ENABLED, false)
            .remove(KEY_PWD_HASH)
            .remove(KEY_PWD_SALT)
            .remove(KEY_PWD_ITERATIONS)
            .apply()
        _isBrowserLockEnabled.value = false
        _hasCustomLockPassword.value = false
        _isBrowserUnlocked.value = true
    }

    fun verifyLockPassword(context: Context, candidate: String): Boolean {
        init(context)
        if (candidate.isEmpty()) return false
        val p = prefs(context)
        val encodedHash = p.getString(KEY_PWD_HASH, null) ?: return false
        val encodedSalt = p.getString(KEY_PWD_SALT, null) ?: return false
        val iterations = p.getInt(KEY_PWD_ITERATIONS, PBKDF2_ITERATIONS).coerceAtLeast(10_000)

        val expected = runCatching { Base64.decode(encodedHash, Base64.NO_WRAP) }.getOrNull() ?: return false
        val salt = runCatching { Base64.decode(encodedSalt, Base64.NO_WRAP) }.getOrNull() ?: return false
        if (expected.isEmpty() || salt.isEmpty()) return false

        val derived = pbkdf2(candidate.trim(), salt, iterations)
        return MessageDigest.isEqual(derived, expected)
    }

    /**
     * Unlocks ONLY the browser UI entry gate. Does not approve transactions or unlock the wallet.
     */
    fun markBrowserUnlocked() {
        _isBrowserUnlocked.value = true
    }

    /**
     * Re-locks the browser UI gate (called when app goes to background) if lock is enabled.
     */
    fun lockBrowser() {
        if (_isBrowserLockEnabled.value) {
            _isBrowserUnlocked.value = false
        }
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

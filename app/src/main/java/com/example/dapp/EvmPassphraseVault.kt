package com.example.dapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object EvmPassphraseVault {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "kaspa_browser_evm_passphrase_v1"
    private const val PREFS = "evm_passphrase_vault"
    private const val IV_SIZE = 12

    @Synchronized
    fun save(context: Context, accountId: String, passphrase: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = preferenceKey(accountId)
        if (passphrase.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        require(iv.size == IV_SIZE)
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        prefs.edit().putString(key, android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)).apply()
    }

    @Synchronized
    fun load(context: Context, accountId: String): String {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(preferenceKey(accountId), null) ?: return ""
        return try {
            val combined = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            require(combined.size > IV_SIZE)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalStateException("Saved EVM passphrase could not be decrypted; re-enter it to continue")
        }
    }

    @Synchronized
    fun isLegacyPhraseConfirmed(context: Context, accountId: String, derivedAddress: String): Boolean {
        if (derivedAddress.isBlank()) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(legacyConfirmationKey(accountId, derivedAddress), false)
    }

    @Synchronized
    fun confirmLegacyPhrase(context: Context, accountId: String, derivedAddress: String) {
        require(derivedAddress.matches(Regex("0x[0-9a-fA-F]{40}"))) { "A valid derived EVM address is required" }
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(legacyConfirmationKey(accountId, derivedAddress), true)
            .commit()
        require(saved) { "Unable to save legacy EVM address confirmation" }
    }

    private fun legacyConfirmationKey(accountId: String, derivedAddress: String): String =
        preferenceKey(accountId) + "_legacy_" + java.security.MessageDigest.getInstance("SHA-256")
            .digest(derivedAddress.lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun preferenceKey(accountId: String): String {
        require(accountId.isNotBlank())
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(accountId.toByteArray(Charsets.UTF_8))
        return "passphrase_" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

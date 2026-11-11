package com.example

import com.example.network.CryptoUtils
import com.example.util.KaspaWebViewConfigurator
import android.webkit.WebView
import android.webkit.CookieManager
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityFixesVerificationTest {

    @Test
    fun walletCreationIsNativeAndContainsNoGoogleIdentity() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val account = CryptoUtils.deriveDecentralizedAccount(
            customHandle = "Independent Wallet",
            seedMnemonic = mnemonic,
            networkPrefix = "kaspa",
            encryptionKeyPhrase = "unit-test-only-wallet-key"
        )

        assertEquals("DECENTRALIZED_NATIVE", account.accountType)
        assertEquals(null, account.googleEmail)
        assertEquals(null, account.googleDisplayName)
        assertEquals(mnemonic, CryptoUtils.getDecryptedSeed(account.seedPhrase, keyPhrase = "unit-test-only-wallet-key"))
    }

    @Test
    fun bip39PassphraseChangesWalletDerivationWithoutGoogleIdentity() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val withoutPassphrase = CryptoUtils.deriveKaspaKeyPair(mnemonic, prefix = "kaspa", passphrase = "")
        val withPassphrase = CryptoUtils.deriveKaspaKeyPair(mnemonic, prefix = "kaspa", passphrase = "wallet-only-test-passphrase")

        assertNotEquals(withoutPassphrase.kaspaAddress, withPassphrase.kaspaAddress)
        assertNotEquals(withoutPassphrase.publicKeyHex, withPassphrase.publicKeyHex)
        withoutPassphrase.wipe()
        withPassphrase.wipe()
    }

    @Test
    fun seedEncryptionAndDecryptionRoundTrip() {
        val secretPhrase = "cat dog elephant fox grape horse igloo jaguar kite lemon mango nut"
        val testKeyPhrase = "unit-test-only-key-phrase-2026"
        val encrypted = CryptoUtils.encryptAes256(secretPhrase, keyPhrase = testKeyPhrase)
        
        assertNotNull(encrypted)
        assertNotEquals("Encrypted text must not be plaintext", secretPhrase, encrypted)
        
        val decrypted = CryptoUtils.decryptAes256(encrypted, keyPhrase = testKeyPhrase)
        assertEquals("Decrypted seed must match original plaintext phrase", secretPhrase, decrypted)
    }

    @Test
    fun legacyEncryptedSeedCanStillBeDecryptedForMigration() {
        val secretPhrase = "one two three four five six seven eight nine ten eleven twelve"
        // Encrypt using legacy key phrase to test backwards compatibility migration
        val legacyEncrypted = CryptoUtils.encryptAes256(secretPhrase, keyPhrase = "DecentralNetStorageKey2026")
        
        // Decrypt without specifying phrase must successfully migrate and decrypt
        val decrypted = CryptoUtils.decryptAes256(legacyEncrypted)
        assertEquals("Legacy encrypted seed must decrypt successfully", secretPhrase, decrypted)
    }

    @Test
    fun webViewDefaultsBlockLocalFileAccessMixedContentAndThirdPartyCookies() {
        val context = RuntimeEnvironment.getApplication()
        val webView = WebView(context)
        try {
            KaspaWebViewConfigurator.applyWebSettings(webView, context)
            assertTrue("Local file access must be disabled", !webView.settings.allowFileAccess)
            assertTrue("Content URI access must be disabled", !webView.settings.allowContentAccess)
            assertEquals(
                "Mixed content must be blocked",
                android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW,
                webView.settings.mixedContentMode
            )
            assertTrue(
                "Third-party cookies must be disabled by default",
                !CookieManager.getInstance().acceptThirdPartyCookies(webView)
            )
        } finally {
            webView.destroy()
        }
    }

}

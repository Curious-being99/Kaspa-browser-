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
    fun googleAccountLinkageDoesNotDeriveKeyFromEmail() {
        val email1 = "alice@example.com"
        val email2 = "alice@example.com" // same email
        
        val acc1 = CryptoUtils.deriveGoogleBridgeAccount(email1, "Alice")
        val acc2 = CryptoUtils.deriveGoogleBridgeAccount(email2, "Alice")
        
        // Two account creations for the same email must NOT result in identical keys.
        // If keys were derived deterministically from email, acc1 and acc2 would have identical seed and addresses.
        val seed1 = CryptoUtils.getDecryptedSeed(acc1.seedPhrase)
        val seed2 = CryptoUtils.getDecryptedSeed(acc2.seedPhrase)
        
        assertNotEquals("Seeds must be randomly generated and not derived from email", seed1, seed2)
        assertNotEquals("Addresses must be unique and not deterministically tied to email", acc1.kaspaAddress, acc2.kaspaAddress)
        
        // Ensure valid 12-word BIP-39 mnemonic is generated
        val words = seed1.split(" ")
        assertEquals("Seed phrase must be 12 words", 12, words.size)
        assertTrue("Seed words must all be lowercase alphabetic", words.all { w -> w.all { c -> c in 'a'..'z' } })
    }

    @Test
    fun googleAccountLinkagePreservesExistingSecureSeedWhenProvided() {
        val existingSeed = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val acc = CryptoUtils.deriveGoogleBridgeAccount(
            email = "bob@example.com",
            displayName = "Bob",
            existingSeedPhrase = existingSeed
        )
        
        val decrypted = CryptoUtils.getDecryptedSeed(acc.seedPhrase)
        assertEquals("Must link existing seed rather than creating a new one", existingSeed, decrypted)
        assertEquals("googleEmail must be stored as metadata", "bob@example.com", acc.googleEmail)
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

    @Test
    fun reattachingExistingAccountPreservesExactKeys() {
        val email = "vicisaac48@gmail.com"
        val originalAccount = CryptoUtils.deriveGoogleBridgeAccount(email, "Vic")
        val originalSeed = CryptoUtils.getDecryptedSeed(originalAccount.seedPhrase)

        // Simulating reconnect with existing seed phrase
        val reconnectedAccount = CryptoUtils.deriveGoogleBridgeAccount(
            email = email,
            displayName = "Vic",
            existingSeedPhrase = originalAccount.seedPhrase
        )
        val reconnectedSeed = CryptoUtils.getDecryptedSeed(reconnectedAccount.seedPhrase)

        assertEquals("Reconnecting existing account must retain identical seed phrase", originalSeed, reconnectedSeed)
        assertEquals("Reconnecting existing account must retain identical Kaspa address", originalAccount.kaspaAddress, reconnectedAccount.kaspaAddress)
    }
}

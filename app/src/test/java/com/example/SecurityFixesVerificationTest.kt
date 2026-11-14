package com.example

import com.example.network.CryptoUtils
import com.example.util.KaspaWebViewConfigurator
import android.webkit.WebView
import android.webkit.CookieManager
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun signedFullBalanceTransferConservesValueAndCoversActualFee() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val sender = CryptoUtils.deriveKaspaKeyPair(mnemonic, prefix = "kaspatest")
        val destination = CryptoUtils.deriveKaspaKeyPair(
            mnemonic,
            prefix = "kaspatest",
            passphrase = "migration-destination-test"
        )
        val inputAmount = 100_000_000L
        try {
            val senderScript = com.example.network.kaspa.KaspaTransactionEngine.decodeAddressToScriptPublicKey(sender.kaspaAddress)
            val destinationScript = com.example.network.kaspa.KaspaTransactionEngine.decodeAddressToScriptPublicKey(destination.kaspaAddress)
            val entry = com.example.network.kaspa.KaspaTransactionEngine.KaspaUtxoEntry(
                amount = inputAmount,
                scriptPublicKey = senderScript
            )
            val transaction = com.example.network.kaspa.KaspaTransactionEngine.KaspaTransaction(
                version = 0,
                inputs = listOf(
                    com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionInput(
                        previousOutpoint = com.example.network.kaspa.KaspaTransactionEngine.KaspaOutpoint(
                            transactionId = "a".repeat(64),
                            index = 0L
                        )
                    )
                ),
                outputs = listOf(
                    com.example.network.kaspa.KaspaTransactionEngine.KaspaTransactionOutput(
                        amount = inputAmount - 20_000_000L,
                        scriptPublicKey = destinationScript
                    )
                )
            )
            com.example.network.kaspa.KaspaTransactionEngine.signTransaction(
                transaction,
                listOf(entry),
                sender.privateKey
            )
            val signedMass = com.example.network.kaspa.KaspaTransactionEngine.calculateMass(
                transaction,
                listOf(inputAmount)
            )
            val actualFee = inputAmount - transaction.outputs.single().amount
            val requiredFee = maxOf(
                com.example.network.kaspa.KaspaTransactionEngine.MINIMUM_WALLET_TRANSACTION_FEE_SOMPIS,
                com.example.network.kaspa.KaspaTransactionEngine.calculateToccataMinimumStandardFee(transaction),
                com.example.network.kaspa.KaspaTransactionEngine.calculateFeeForMass(
                    signedMass,
                    com.example.network.kaspa.KaspaTransactionEngine.DEFAULT_SOMPI_PER_MASS
                )
            )
            assertTrue("Signed full-balance fee must cover the actual required fee", actualFee >= requiredFee)
            assertEquals("All source value must be either transferred or paid as fee", inputAmount, transaction.outputs.single().amount + actualFee)
            assertTrue("Signed transaction must fit the standard mass limit", signedMass <= com.example.network.kaspa.KaspaTransactionEngine.MAXIMUM_STANDARD_TRANSACTION_MASS)
        } finally {
            sender.wipe()
            destination.wipe()
        }
    }

    @Test
    fun generatedReplacementMnemonicIsValidTwelveWordBip39() {
        val first = CryptoUtils.generateMnemonic()
        val second = CryptoUtils.generateMnemonic()
        assertEquals(12, first.trim().split(Regex("\\s+")).size)
        assertTrue(org.web3j.crypto.MnemonicUtils.validateMnemonic(first))
        assertTrue(org.web3j.crypto.MnemonicUtils.validateMnemonic(second))
        assertNotEquals(first, second)
        assertTrue(CryptoUtils.isValidBip39Mnemonic(first))
        assertTrue(CryptoUtils.isValidBip39Mnemonic(second))
    }

    @Test
    fun bip39PassphraseMatchesOfficialSeedVector() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed = CryptoUtils.mnemonicToSeed(mnemonic, "TREZOR")
        try {
            assertEquals(
                "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
                seed.joinToString("") { "%02x".format(it) }
            )
        } finally {
            java.util.Arrays.fill(seed, 0.toByte())
        }
    }

    @Test
    fun bip39ChecksumValidationSupportsTwelveAndTwentyFourWordRecovery() {
        val validTwelve = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val invalidTwelve = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon ability"
        val validTwentyFour = (List(23) { "abandon" } + "art").joinToString(" ")
        val invalidTwentyFour = (List(23) { "abandon" } + "about").joinToString(" ")

        assertTrue(CryptoUtils.isValidBip39Mnemonic(validTwelve))
        assertFalse(CryptoUtils.isValidBip39Mnemonic(invalidTwelve))
        assertTrue(CryptoUtils.isValidBip39Mnemonic(validTwentyFour))
        assertFalse(CryptoUtils.isValidBip39Mnemonic(invalidTwentyFour))
    }

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
    fun nonKeystoreCiphertextRequiresExplicitDecryptionPhrase() {
        val secretPhrase = "one two three four five six seven eight nine ten eleven twelve"
        val testKeyPhrase = "unit-test-only-migration-key"
        val encrypted = CryptoUtils.encryptAes256(secretPhrase, keyPhrase = testKeyPhrase)

        try {
            CryptoUtils.decryptAes256(encrypted)
            throw AssertionError("Decryption without an explicit phrase must fail")
        } catch (_: IllegalArgumentException) {
            // Expected: no implicit or source-code-known fallback keys are accepted.
        }

        assertEquals(secretPhrase, CryptoUtils.decryptAes256(encrypted, keyPhrase = testKeyPhrase))
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
    fun desktopModeAvoidsFingerprintContradictionsAndHeaderSplit() {
        val baseUa = "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.100 Mobile Safari/537.36"
        val desktopUa = com.example.network.KaspaPrivacyEngine.getDesktopUserAgent(baseUa)
        assertTrue("Desktop UA should report Linux x86_64 to match Android TCP/BoringSSL/Skia stack", desktopUa.contains("X11; Linux x86_64"))
        assertTrue("Desktop UA should preserve WebView Chrome version", desktopUa.contains("Chrome/131.0.6778.100"))
        assertTrue("Desktop UA must not claim Windows NT", !desktopUa.contains("Windows NT"))

        val headers = com.example.network.KaspaPrivacyEngine.getDesktopHeaders(true, baseUa)
        assertTrue("getDesktopHeaders must be empty to avoid one-shot Sec-CH-UA header mismatch", headers.isEmpty())

        val shieldJs = com.example.network.KaspaPrivacyEngine.JS_PRIVACY_SHIELD_INJECTION
        assertTrue("Privacy shield must not spoof RTX 4070 WebGL GPU", !shieldJs.contains("RTX 4070"))
        assertTrue(
            "Privacy shield must gate WebRTC and geolocation stubs strictly behind __kaspa_relay_shield flag",
            shieldJs.contains("window.__kaspa_relay_shield === true")
        )
        assertTrue("Privacy shield must exempt turnstile/stripe/paypal domains", shieldJs.contains("turnstile") && shieldJs.contains("stripe.com"))

        val viewportScript = com.example.network.KaspaPrivacyEngine.getDesktopViewportScript(isDesktop = true, baseUa = baseUa)
        assertTrue("Desktop viewport script must skip subframes (e.g., CAPTCHA iframes)", viewportScript.contains("window.top !== window.self"))
        assertTrue("Desktop viewport script must compute isDesktopMode dynamically from navigator.userAgent", viewportScript.contains("ua.indexOf('Android') === -1"))
        assertTrue("Desktop viewport script must not contain frozen true || condition", !viewportScript.contains("true ||"))
        assertTrue("Desktop viewport script must not spoof navigator.userAgentData or Win32 platform", !viewportScript.contains("userAgentData") && !viewportScript.contains("Win32"))
    }

    @Test
    fun errorAndSslHtmlEscapePayloadsAndSanitizeRetryUrl() {
        val errorHtml = KaspaWebViewConfigurator.generateErrorHtml(
            failingUrl = "javascript:alert(1)",
            errorDescription = "<script>xss()</script>"
        )
        assertTrue("Error message must be HTML escaped", errorHtml.contains("&lt;script&gt;xss()&lt;/script&gt;"))
        assertTrue("Raw <script> tag must not appear in error HTML", !errorHtml.contains("<script>xss()"))
        assertTrue("Unsafe javascript: retry URL must fall back to safe https retry URL", errorHtml.contains("var targetUrl = \"https://kaspa.org\";"))

        val sslHtml = KaspaWebViewConfigurator.generateSslErrorHtml(
            failingUrl = "https://example.com/<img src=x onerror=alert(1)>",
            sslReason = "<b>Untrusted</b>"
        )
        assertTrue("SSL failing URL must be HTML escaped", sslHtml.contains("&lt;img src=x onerror=alert(1)&gt;"))
        assertTrue("SSL reason must be HTML escaped", sslHtml.contains("&lt;b&gt;Untrusted&lt;/b&gt;"))
        assertTrue("Raw <img payload must not appear in SSL HTML", !sslHtml.contains("<img src=x"))
    }

    @Test
    fun captchaUrlsRecognizedForThirdPartyCookiesWhileNonAuthRemainsBlocked() {
        assertTrue(
            "Cloudflare Turnstile domain must be recognized as essential CAPTCHA",
            com.example.network.KaspaPrivacyEngine.isEssentialAuthOrCaptchaUrl("https://challenges.cloudflare.com/turnstile/v0/api.js")
        )
        assertTrue(
            "hCaptcha domain must be recognized as essential CAPTCHA",
            com.example.network.KaspaPrivacyEngine.isEssentialAuthOrCaptchaDomain("hcaptcha.com")
        )
        assertTrue(
            "reCAPTCHA domain must be recognized as essential CAPTCHA",
            com.example.network.KaspaPrivacyEngine.isEssentialAuthOrCaptchaDomain("recaptcha.net")
        )
        assertTrue(
            "Regular unrelated sites must not automatically qualify for third-party cookie exemption",
            !com.example.network.KaspaPrivacyEngine.isEssentialAuthOrCaptchaUrl("https://example.com/article")
        )

        val context = RuntimeEnvironment.getApplication()
        val webView = WebView(context)
        try {
            KaspaWebViewConfigurator.applyWebSettings(webView, context, isDesktop = true, acceptThirdPartyCookies = false)
            KaspaWebViewConfigurator.checkAndConfigureForUrl(
                webView,
                "https://challenges.cloudflare.com/turnstile/v0/api.js"
            )
            // Verify third-party cookie exemption never triggers tracker blocking
            assertTrue(
                "Turnstile must never be blocked as a tracker",
                !com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://challenges.cloudflare.com/turnstile/v0/api.js")
            )
        } finally {
            webView.destroy()
        }
    }

    @Test
    fun nativeIntentRoutingBlocksSelfPackageAndUnsafeFallbackUrls() {
        val appContext = RuntimeEnvironment.getApplication()
        var fallbackReceived: String? = null
        val selfPackageIntent = "intent://open#Intent;scheme=https;package=${appContext.packageName};S.browser_fallback_url=https://example.com;end"
        val blockedSelf = com.example.network.NativeIntentRoutingEngine.routeUrl(
            appContext,
            selfPackageIntent,
            hasUserGesture = true
        ) { fallbackReceived = it }
        assertTrue("Self-targeting intent:// must be intercepted", blockedSelf)
        assertEquals("Self-targeting intent must not invoke fallback", null, fallbackReceived)

        val throwingContext = object : android.content.ContextWrapper(appContext) {
            override fun startActivity(intent: android.content.Intent?) {
                throw android.content.ActivityNotFoundException("No handler")
            }
        }
        val unsafeFallbackIntent = "intent://open#Intent;scheme=https;package=com.missing.app;S.browser_fallback_url=javascript:alert(1);end"
        val handledUnsafe = com.example.network.NativeIntentRoutingEngine.routeUrl(
            throwingContext,
            unsafeFallbackIntent,
            hasUserGesture = true
        ) { fallbackReceived = it }
        assertTrue("Intent URI must be handled", handledUnsafe)
        assertEquals("Unsafe javascript: browser_fallback_url must be rejected", null, fallbackReceived)

        val safeFallbackIntent = "intent://open#Intent;scheme=https;package=com.missing.app;S.browser_fallback_url=https://kaspa.org/docs;end"
        com.example.network.NativeIntentRoutingEngine.routeUrl(
            throwingContext,
            safeFallbackIntent,
            hasUserGesture = true
        ) { fallbackReceived = it }
        assertEquals("https://kaspa.org/docs", fallbackReceived)
    }

    @Test
    fun peerIdGenerationIsWellFormedAndNonDeterministic() {
        val peer1 = CryptoUtils.generatePeerId()
        val peer2 = CryptoUtils.generatePeerId()
        assertTrue(peer1.startsWith("12D3KooW"))
        assertTrue(peer2.startsWith("12D3KooW"))
        assertNotEquals(peer1, peer2)
    }

    @Test
    fun cloudflareChallengeAndCdnCgiAreExemptFromBlockingAndCache() {
        val cdnCgiUrl = "https://example.com/cdn-cgi/challenge-platform/h/b/orchestrate/chl_api/v1"
        val turnstileApi = "https://challenges.cloudflare.com/turnstile/v0/b/1234/api.js"

        assertTrue(
            "/cdn-cgi/ challenge endpoints must be recognized as essential auth/CAPTCHA",
            com.example.network.KaspaPrivacyEngine.isEssentialAuthOrCaptchaUrl(cdnCgiUrl)
        )
        assertTrue(
            "/cdn-cgi/ challenge endpoints must never be blocked as trackers",
            !com.example.network.KaspaPrivacyEngine.isTrackerOrAd(cdnCgiUrl)
        )
        assertTrue(
            "Turnstile endpoints must never be blocked as trackers",
            !com.example.network.KaspaPrivacyEngine.isTrackerOrAd(turnstileApi)
        )
        assertTrue(
            "/cdn-cgi/ scripts must never be cached by WebViewAssetLruCache",
            !com.example.network.WebViewAssetLruCache.shouldCache(cdnCgiUrl, "GET", false)
        )
        assertTrue(
            "Turnstile challenge scripts must never be cached by WebViewAssetLruCache",
            !com.example.network.WebViewAssetLruCache.shouldCache(turnstileApi, "GET", false)
        )
    }

    @Test
    fun backgroundPlaybackScriptSkipsChallengesAndDoesNotWrapAddEventListener() {
        val bgScript = com.example.util.BrowserBackgroundPlayback.getBackgroundPlaybackScript()
        assertTrue(
            "Background playback script must skip challenges.cloudflare.com and /cdn-cgi/",
            bgScript.contains("challenges.cloudflare.com") && bgScript.contains("/cdn-cgi/")
        )
        assertTrue(
            "Background playback script must skip unrelated cross-origin iframes",
            bgScript.contains("window.top !== window.self && !isKnownMediaHost")
        )
        assertTrue(
            "Background playback script must not monkey-patch EventTarget.prototype.addEventListener",
            !bgScript.contains("EventTarget.prototype.addEventListener")
        )
        assertTrue(
            "Background playback script must defer prototype overrides on non-media pages until playback",
            bgScript.contains("document.addEventListener('play'")
        )
    }

    @Test
    fun desktopViewportAndMetadataApplyCleanlyAndSkipTurnstileChallenges() {
        val viewportScript = com.example.network.KaspaPrivacyEngine.getDesktopViewportScript(isDesktop = true)
        assertTrue(
            "Viewport script must skip top-level Cloudflare challenge / Turnstile interstitials",
            viewportScript.contains("Just a moment") &&
                viewportScript.contains("challenge-running") &&
                viewportScript.contains("cf-turnstile")
        )

        val context = RuntimeEnvironment.getApplication()
        val webView = WebView(context)
        try {
            val baseUa = "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.100 Mobile Safari/537.36"
            com.example.network.KaspaPrivacyEngine.applyUserAgentAndMetadata(
                webView.settings,
                isDesktop = true,
                baseUa = baseUa
            )
            assertTrue(webView.settings.userAgentString.contains("X11; Linux x86_64"))
            assertTrue(webView.settings.useWideViewPort)
            assertTrue(webView.settings.loadWithOverviewMode)

            com.example.network.KaspaPrivacyEngine.applyUserAgentAndMetadata(
                webView.settings,
                isDesktop = false,
                baseUa = baseUa
            )
            assertTrue(webView.settings.userAgentString.contains("Android"))
            assertTrue(!webView.settings.useWideViewPort)
            assertTrue(!webView.settings.loadWithOverviewMode)
        } finally {
            webView.destroy()
        }
    }

    @Test
    fun legacyWalletDetectionUsesExactFirst256Bip39EnglishWordsWithoutMetadata() {
        val afterCutoff = 1_792_195_200_001L
        val lastIncludedWordPhrase = List(12) { "cable" }.joinToString(" ")
        val firstIncludedWordPhrase = List(12) { "abandon" }.joinToString(" ")

        assertEquals(
            "old wallet format, please move your funds",
            CryptoUtils.legacyWalletWarning(
                storedSeed = lastIncludedWordPhrase,
                createdAt = afterCutoff
            )
        )
        assertEquals(
            "old wallet format, please move your funds",
            CryptoUtils.legacyWalletWarning(
                storedSeed = firstIncludedWordPhrase,
                createdAt = afterCutoff
            )
        )
    }

    @Test
    fun legacyWalletDetectionExcludesThe257thBip39EnglishWord() {
        val outsidePrefixPhrase = List(12) { "cactus" }.joinToString(" ")
        val mixedPhrase = List(11) { "cable" }.plus("cactus").joinToString(" ")

        assertEquals(
            null,
            CryptoUtils.legacyWalletWarning(
                storedSeed = outsidePrefixPhrase,
                createdAt = 1_792_195_200_001L
            )
        )
        assertEquals(
            null,
            CryptoUtils.legacyWalletWarning(
                storedSeed = mixedPhrase,
                createdAt = 1_792_195_200_001L
            )
        )
    }

    @Test
    fun legacyWalletDetectionUsesCreatedAtForPreCutoffWarning() {
        val phrase = List(12) { "cable" }.joinToString(" ")
        assertEquals(
            "made with a non-secure generator, move your funds now",
            CryptoUtils.legacyWalletWarning(
                storedSeed = phrase,
                createdAt = 1_792_195_200_000L
            )
        )
    }

    @Test
    fun legacyWalletSigningGateRefusesLegacyAndAllowsAuthorizedMigration() {
        val legacyPhrase = List(12) { "cable" }.joinToString(" ")
        val rebuiltLegacyAccount = CryptoUtils.deriveDecentralizedAccount(
            customHandle = "Rebuilt Legacy",
            seedMnemonic = legacyPhrase,
            networkPrefix = "kaspatest",
            encryptionKeyPhrase = "unit-test-only-wallet-key",
            accountType = "DECENTRALIZED_NATIVE"
        )
        val decrypted = CryptoUtils.getDecryptedSeed(
            rebuiltLegacyAccount.seedPhrase,
            keyPhrase = "unit-test-only-wallet-key"
        )

        assertTrue(CryptoUtils.hasLegacyMnemonicFingerprint(decrypted))
        assertTrue(CryptoUtils.isLegacyWallet(rebuiltLegacyAccount, decrypted))
        assertTrue(CryptoUtils.isLegacySeed(decrypted))

        var blocked = false
        try {
            CryptoUtils.getDecryptedSeedForSigning(
                account = rebuiltLegacyAccount,
                keyPhrase = "unit-test-only-wallet-key",
                isAuthorizedMigration = false
            )
        } catch (e: IllegalStateException) {
            blocked = true
        }
        assertTrue("getDecryptedSeedForSigning must block legacy wallet", blocked)

        val migratedSeed = CryptoUtils.getDecryptedSeedForSigning(
            account = rebuiltLegacyAccount,
            keyPhrase = "unit-test-only-wallet-key",
            isAuthorizedMigration = true
        )
        assertEquals(legacyPhrase, migratedSeed)
    }

    @Test
    fun legacyWalletSigningGateAllowsStandardBip39WalletWithWordAbove255() {
        // 'cactus' is word index 256 (outside 0..255 legacy range)
        val validSeed = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon cactus ability"
        assertFalse(CryptoUtils.hasLegacyMnemonicFingerprint(validSeed))
        assertEquals(validSeed, CryptoUtils.getDecryptedSeedForSigning(validSeed))
    }

    @Test
    fun googleZkBridgeAccountTypeIsPreservedAndFlaggedCompromised() {
        val phrase = "cactus cactus cactus cactus cactus cactus cactus cactus cactus cactus cactus cactus"
        val account = CryptoUtils.deriveDecentralizedAccount(
            customHandle = "Legacy Google Account",
            seedMnemonic = phrase,
            networkPrefix = "kaspatest",
            encryptionKeyPhrase = "unit-test-only-wallet-key",
            accountType = "GOOGLE_ZK_BRIDGE"
        )
        assertEquals("GOOGLE_ZK_BRIDGE", account.accountType)
        assertTrue(CryptoUtils.isLegacyWallet(account, phrase))
        assertEquals(
            "compromised",
            CryptoUtils.legacyWalletWarning(
                storedSeed = phrase,
                createdAt = 1_792_195_200_001L,
                accountType = account.accountType
            )
        )
    }

    @Test
    fun evmWalletEngineRefusesSigningForLegacySeed() {
        val legacyPhrase = List(12) { "cable" }.joinToString(" ")
        var personalBlocked = false
        try {
            com.example.dapp.EvmWalletEngine.signPersonalMessage(legacyPhrase, "hello")
        } catch (_: IllegalStateException) {
            personalBlocked = true
        }
        assertTrue("EvmWalletEngine.signPersonalMessage must refuse legacy seed", personalBlocked)

        var rawBlocked = false
        try {
            com.example.dapp.EvmWalletEngine.signEthMessage(legacyPhrase, "0x" + "11".repeat(32))
        } catch (_: IllegalStateException) {
            rawBlocked = true
        }
        assertTrue("EvmWalletEngine.signEthMessage must refuse legacy seed", rawBlocked)
    }

    @Test
    fun desktopCaptchaPagesAreExemptedAndNativeDomPrototypesPreserved() {
        val privacy = com.example.network.KaspaPrivacyEngine
        assertTrue(privacy.isEssentialAuthOrCaptchaUrl("https://www.google.com/recaptcha/api2/anchor?ar=1"))
        assertTrue(privacy.isEssentialAuthOrCaptchaUrl("https://example.com/?__cf_chl_tk=abc"))
        assertTrue(privacy.isEssentialAuthOrCaptchaUrl("https://api.geetest.com/get.php"))
        assertTrue(privacy.isEssentialAuthOrCaptchaUrl("https://cdn.friendlycaptcha.com/widget.js"))

        val script = privacy.getDesktopViewportScript(isDesktop = true)
        assertTrue("Must not patch HTMLMetaElement.prototype.setAttribute", !script.contains("HTMLMetaElement.prototype.setAttribute"))
        assertTrue("Must not redefine HTMLMetaElement.prototype properties", !script.contains("Object.defineProperty(HTMLMetaElement.prototype"))
        assertTrue("Must skip /recaptcha and __cf_chl_ at document-start", script.contains("/recaptcha") && script.contains("__cf_chl_"))
        assertTrue("Must remove injected desktop overrides when challenge mounts", script.contains("cleanupDesktopOverrides") && script.contains("__kaspa_desktop_css"))

        val shield = privacy.JS_PRIVACY_SHIELD_INJECTION
        assertTrue("Privacy shield must exempt /recaptcha and __cf_chl_", shield.contains("/recaptcha") && shield.contains("__cf_chl_"))

        val context = RuntimeEnvironment.getApplication()
        val webView = WebView(context)
        try {
            // Even when cold-starting directly into desktop settings then toggling back to mobile,
            // mobile UA must restore an Android UA rather than retaining a desktop UA.
            privacy.applyUserAgentAndMetadata(webView.settings, isDesktop = true, baseUa = null)
            val desktopUa = webView.settings.userAgentString
            assertTrue(desktopUa.contains("X11; Linux x86_64"))

            privacy.applyUserAgentAndMetadata(webView.settings, isDesktop = false, baseUa = desktopUa)
            val mobileUa = webView.settings.userAgentString
            assertTrue("Toggling back to mobile must restore an Android UA", mobileUa.contains("Android") && mobileUa.contains("Mobile"))
        } finally {
            webView.destroy()
        }
    }

    @Test
    fun privacyShieldDoesNotTamperNavigatorDescriptorsAndSkipsCloudflare() {
        val shield = com.example.network.KaspaPrivacyEngine.JS_PRIVACY_SHIELD_INJECTION
        assertFalse("Privacy shield must not redefine navigator.doNotTrack", shield.contains("doNotTrack"))
        assertFalse("Privacy shield must not redefine navigator.globalPrivacyControl", shield.contains("globalPrivacyControl"))
        assertFalse("Privacy shield must not define own properties on navigator", shield.contains("Object.defineProperty(navigator"))
        assertTrue("Privacy shield must exempt /cdn-cgi/, /turnstile, and __cf_chl_",
            shield.contains("/cdn-cgi/") && shield.contains("/turnstile") && shield.contains("__cf_chl_"))
        assertTrue("Privacy shield must detect Cloudflare challenge DOM markers",
            shield.contains("Just a moment") && shield.contains("challenge-running") && shield.contains("cf-turnstile"))
        val relayGateIdx = shield.indexOf("window.__kaspa_relay_shield === true")
        val injectedFlagIdx = shield.indexOf("window.__kaspa_shield_injected = true")
        assertTrue("Privacy shield must gate __kaspa_shield_injected behind relay shield flag",
            relayGateIdx >= 0 && injectedFlagIdx > relayGateIdx)
    }

    @Test
    fun cosmeticHidingAndAutofillSkipCloudflareChallengePages() {
        val cosmeticJs = com.example.network.UBlockEngine.getCosmeticHidingCss()
        assertTrue("Cosmetic hiding must skip Cloudflare challenges and DOM markers",
            cosmeticJs.contains("cloudflare.com") && cosmeticJs.contains("/cdn-cgi/") &&
                cosmeticJs.contains("/turnstile") && cosmeticJs.contains("cf-turnstile"))

        val autofill = com.example.security.PasswordAutofillBridge
        assertFalse(autofill.isUrlEligibleForAutofill("https://challenges.cloudflare.com/turnstile/v0/api.js"))
        assertFalse(autofill.isUrlEligibleForAutofill("https://kaspa.stream/cdn-cgi/challenge-platform/h/b/orchestrate/chl_api/v1"))
        assertFalse(autofill.isUrlEligibleForAutofill("https://kaspa.stream/?__cf_chl_tk=abc123"))
        assertTrue(autofill.isUrlEligibleForAutofill("https://kaspa.stream/"))
        assertTrue("Autofill scanner must skip Cloudflare challenge DOM",
            autofill.JS_FORM_SCANNER.contains("Just a moment") && autofill.JS_FORM_SCANNER.contains("cf-turnstile"))

        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://kaspa.stream/cdn-cgi/challenge-platform/h/b/orchestrate/chl_api/v1"))
        assertFalse(com.example.network.KaspaPrivacyEngine.isTrackerOrAd("https://kaspa.stream/?__cf_chl_tk=abc123"))
        assertFalse(com.example.network.UBlockEngine.shouldBlock("https://kaspa.stream/cdn-cgi/challenge-platform/h/b/orchestrate/chl_api/v1"))
    }

    @Test
    fun mobileAndDesktopUaPreserveChromeVersionAndStripWebViewMarkers() {
        val rawWebViewUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/133.0.6943.50 Mobile Safari/537.36"
        val cleanedMobile = com.example.network.KaspaPrivacyEngine.getMobileUserAgent(rawWebViewUa)
        assertFalse("Mobile UA must strip '; wv' WebView token", cleanedMobile.contains("; wv"))
        assertFalse("Mobile UA must strip 'Version/4.0' WebView token", cleanedMobile.contains("Version/4.0"))
        assertTrue("Mobile UA must preserve actual WebView Chrome version", cleanedMobile.contains("Chrome/133.0.6943.50"))

        val context = RuntimeEnvironment.getApplication()
        val webView = WebView(context)
        try {
            KaspaWebViewConfigurator.applyKazviewSettings(webView.settings)
            webView.settings.userAgentString = rawWebViewUa
            com.example.network.KaspaPrivacyEngine.applyUserAgentAndMetadata(webView.settings, isDesktop = true, baseUa = null)
            assertTrue("Desktop UA must preserve Chrome/133.0.6943.50 from WebSettings",
                webView.settings.userAgentString.contains("Chrome/133.0.6943.50"))

            com.example.network.KaspaPrivacyEngine.applyUserAgentAndMetadata(webView.settings, isDesktop = false, baseUa = null)
            assertTrue("Restored mobile UA must preserve Chrome/133.0.6943.50",
                webView.settings.userAgentString.contains("Chrome/133.0.6943.50") &&
                    webView.settings.userAgentString.contains("Android"))
        } finally {
            webView.destroy()
        }
    }

}


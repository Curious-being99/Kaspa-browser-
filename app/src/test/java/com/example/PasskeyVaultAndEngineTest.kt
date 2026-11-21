package com.example

import android.os.Bundle
import android.webkit.WebView
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.AppDatabase
import com.example.data.VaultCredentialEntity
import com.example.engine.BrowserEngineManager
import com.example.security.PasswordAutofillBridge
import com.example.security.PasswordGenerator
import com.example.security.PasswordVaultRepository
import com.example.util.KaspaWebViewConfigurator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PasskeyVaultAndEngineTest {

    @Test
    fun passwordGeneratorProducesHighEntropyAndHonorsOptions() {
        val generated = PasswordGenerator.generate(
            length = 24,
            includeUppercase = true,
            includeLowercase = true,
            includeDigits = true,
            includeSymbols = true,
            avoidAmbiguous = true
        )
        assertEquals(24, generated.length)
        assertTrue(generated.any { it.isUpperCase() })
        assertTrue(generated.any { it.isLowerCase() })
        assertTrue(generated.any { it.isDigit() })
        assertFalse("Ambiguous O/0/I/l/1 should be excluded when enabled", generated.any { it in "0O1lI|" })

        val strength = PasswordGenerator.evaluateStrength(generated)
        assertTrue("24-char full charset password should be Strong", strength.score >= 3)
        assertTrue(strength.entropyBits > 80)

        val weak = PasswordGenerator.evaluateStrength("12345")
        assertTrue("Short digit-only password should be weak", weak.score <= 2)
    }

    @Test
    fun vaultRepositoryMatchesOriginsAndEncryptsPasswordsWithRoom() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = PasswordVaultRepository(db.vaultCredentialDao())
            val testKey = "unit-test-only-vault-aes-key-2026"

            val saved = repo.savePasswordCredential(
                url = "https://github.com/login",
                siteName = "GitHub",
                username = "kaspa_dev",
                rawPassword = "S3cure!TestPassword#99",
                notes = "Main git login",
                keyPhrase = testKey
            )
            assertNotEquals("S3cure!TestPassword#99", saved.encryptedPassword)
            assertEquals("github.com", saved.host)
            assertEquals("S3cure!TestPassword#99", repo.decryptPassword(saved, keyPhrase = testKey))

            val exactMatches = repo.getMatchingCredentialsForUrl("https://github.com/sessions/verified")
            assertEquals(1, exactMatches.size)

            val subdomainMatches = repo.getMatchingCredentialsForUrl("https://gist.github.com/auth")
            assertEquals(1, subdomainMatches.size)

            val lookalikeMatches = repo.getMatchingCredentialsForUrl("https://evilgithub.com/login")
            assertTrue("Lookalike domain must never match github.com", lookalikeMatches.isEmpty())

            // Adding a passkey on the same host & username promotes credential to TYPE_PASSKEY_AND_PASSWORD
            val combined = repo.recordPasskeyCredential(
                url = "https://github.com",
                rpId = "github.com",
                username = "kaspa_dev",
                siteName = "GitHub"
            )
            assertEquals(VaultCredentialEntity.TYPE_PASSKEY_AND_PASSWORD, combined.credentialType)
            assertTrue(combined.hasPassword && combined.hasPasskey)
            assertEquals("S3cure!TestPassword#99", repo.decryptPassword(combined, keyPhrase = testKey))

            repo.deleteCredential(combined.id)
            assertTrue(repo.getAllList().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun hostMatchingRejectsPublicSuffixCrossMatches() {
        assertTrue(PasswordVaultRepository.isHostMatch("accounts.google.com", "mail.google.com"))
        assertTrue(PasswordVaultRepository.isHostMatch("kaspa.org", "explorer.kaspa.org"))
        assertFalse(PasswordVaultRepository.isHostMatch("com", "example.com"))
        assertFalse(PasswordVaultRepository.isHostMatch("co.uk", "bank.co.uk"))
        assertFalse(PasswordVaultRepository.isHostMatch("example.co.uk", "other.co.uk"))
    }

    @Test
    fun migration13To14CreatesVaultCredentialsTableCleanly() {
        val context = RuntimeEnvironment.getApplication()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        try {
            val sqlDb = helper.writableDatabase
            AppDatabase.MIGRATION_13_14.migrate(sqlDb)
            sqlDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='vault_credentials'").use { cursor ->
                assertTrue("vault_credentials table must exist after MIGRATION_13_14", cursor.moveToFirst())
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun autofillBridgeExcludesChallengesPaymentsAndNonHttps() {
        assertTrue(PasswordAutofillBridge.isUrlEligibleForAutofill("https://github.com/login"))
        assertTrue(PasswordAutofillBridge.isUrlEligibleForAutofill("https://account.proton.me/"))

        assertFalse("Plain HTTP must be excluded from autofill", PasswordAutofillBridge.isUrlEligibleForAutofill("http://example.com/login"))
        assertFalse("Cloudflare challenges must never run autofill scanner", PasswordAutofillBridge.isUrlEligibleForAutofill("https://challenges.cloudflare.com/turnstile"))
        assertFalse("/cdn-cgi/ challenge paths must be excluded", PasswordAutofillBridge.isUrlEligibleForAutofill("https://example.com/cdn-cgi/challenge-platform"))
        assertFalse("hCaptcha must be excluded", PasswordAutofillBridge.isUrlEligibleForAutofill("https://hcaptcha.com/challenge"))
        assertFalse("reCAPTCHA must be excluded", PasswordAutofillBridge.isUrlEligibleForAutofill("https://www.recaptcha.net/recaptcha/api2"))
        assertFalse("Stripe must be excluded", PasswordAutofillBridge.isUrlEligibleForAutofill("https://checkout.stripe.com/pay"))
        assertFalse("PayPal must be excluded", PasswordAutofillBridge.isUrlEligibleForAutofill("https://www.paypal.com/signin"))

        assertTrue(
            "Scanner JS must bail immediately inside subframes",
            PasswordAutofillBridge.JS_FORM_SCANNER.contains("window.top !== window.self")
        )
    }

    @Test
    fun engineManagerAndWebViewConfigPasskeyAndCustomTabSafetyChecks() {
        val context = RuntimeEnvironment.getApplication()
        val status = BrowserEngineManager.refreshEngineStatus(context)
        assertEquals("Kazview", status.engineName)
        assertNotNull(status.webViewPackage)

        // Custom Tab launcher must reject non-http(s) schemes safely
        assertFalse(BrowserEngineManager.openInCustomTab(context, "javascript:alert(1)"))
        assertFalse(BrowserEngineManager.openInCustomTab(context, "file:///etc/passwd"))
        assertFalse(BrowserEngineManager.openInCustomTab(context, "kaspa:qpzry9x8gf2tvdw0s3jn54khce6mua7l"))

        val wv = WebView(context)
        try {
            KaspaWebViewConfigurator.applyWebSettings(wv, context, webAuthEnabled = true)
            val bundle = Bundle()
            assertTrue(BrowserEngineManager.saveWebViewStateBounded(wv, bundle))
        } finally {
            wv.destroy()
        }
    }

    @Test
    fun kazviewPreconnectGatingAndTabHibernation() {
        val context = RuntimeEnvironment.getApplication()

        // Privacy relay / Tor / Incognito MUST suppress speculative preconnects
        assertFalse(BrowserEngineManager.preconnectOrigin("https://kaspa.org", isPrivacyRelayActive = true))
        // Insecure HTTP and Cloudflare Turnstile challenges must also be skipped
        assertFalse(BrowserEngineManager.preconnectOrigin("http://example.com", isPrivacyRelayActive = false))
        assertFalse(BrowserEngineManager.preconnectOrigin("https://challenges.cloudflare.com/turnstile", isPrivacyRelayActive = false))

        // Verify BrowserTabWebViewManager freezes background tabs and hibernates oldest cold tab above limit
        val tabManager = com.example.util.BrowserTabWebViewManager()
        try {
            tabManager.getOrCreateWebView("tab_1", context) {}
            Thread.sleep(5)
            tabManager.getOrCreateWebView("tab_2", context) {}
            Thread.sleep(5)
            tabManager.getOrCreateWebView("tab_3", context) {}

            tabManager.pauseTab("tab_1")
            assertEquals(1, tabManager.getFrozenTabCount())

            val captured = mutableListOf<String>()
            val hibernatedCount = tabManager.hibernateColdTabsIfNeeded(
                activeTabId = "tab_3",
                maxLiveWebViews = 2
            ) { coldId, _, _, _ ->
                captured.add(coldId)
            }
            assertEquals(1, hibernatedCount)
            assertEquals(listOf("tab_1"), captured)
            assertFalse("Oldest cold tab should be hibernated", tabManager.hasWebView("tab_1"))
            assertTrue("Recent tab should remain warm", tabManager.hasWebView("tab_2"))
            assertTrue("Active tab must never be hibernated", tabManager.hasWebView("tab_3"))
        } finally {
            tabManager.destroyAll("unit test cleanup")
        }
    }

    @Test
    fun lightweightTorEngineReportsHonestLoopbackCircuitAndNonSpoofedPort() {
        val port = com.example.network.LightweightTorEngine.activePort.value
        assertNotEquals("Must not squat Orbot default port 9050", 9050, port)
        assertNotEquals("Must not squat Tor Browser default port 9150", 9150, port)

        val circuit = com.example.network.LightweightTorEngine.rotateCircuit()
        assertEquals("LOC", circuit.guardHop.countryCode)
        assertEquals("DoH", circuit.middleHop.countryCode)
        assertTrue(circuit.exitHop.countryCode == "DEV" || circuit.exitHop.countryCode == "PRX")
        assertFalse(circuit.guardHop.countryCode in setOf("CH", "IS", "SE"))
        assertFalse(circuit.exitHop.countryCode in setOf("CH", "IS", "SE"))
    }

    @Test
    fun localNodeManagerInitializesOfflineNonSyntheticMetrics() {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val cancelledScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job().apply { cancel() })
        try {
            val manager = com.example.network.LocalNodeManager(context, db, cancelledScope)
            val initial = manager.metrics.value
            assertFalse("Initial metrics must not report online before health check", initial.isOnline)
            assertEquals("Initial mesh health must be 0 before peer checks", 0, initial.meshHealthPercentage)
            assertEquals("Initial bandwidth saved must be 0", 0L, initial.p2pBandwidthSavedBytes)
        } finally {
            db.close()
        }
    }

    @Test
    fun autofillExpectedHostVerificationPreventsCrossDomainFill() {
        val targetUrl = "https://evil-phish.example.net/login"
        val currentHost = PasswordVaultRepository.normalizeHost(targetUrl)
        assertNotNull(currentHost)
        assertFalse(
            "Saved github.com credential must not match attacker redirect domain",
            PasswordVaultRepository.isHostMatch("github.com", currentHost!!)
        )
        assertTrue(
            "Saved github.com credential should match gist.github.com subdomain",
            PasswordVaultRepository.isHostMatch("github.com", PasswordVaultRepository.normalizeHost("https://gist.github.com/login")!!)
        )
    }

    @Test
    fun credentialSettingsStorePersistsAllFiveCredentialAndPasskeyPolicies() {
        val context = RuntimeEnvironment.getApplication()
        com.example.security.CredentialSettingsStore.reloadFromPrefs(context)

        com.example.security.CredentialSettingsStore.setOfferToSavePasswords(context, false)
        com.example.security.CredentialSettingsStore.setAutoCreatePasskeyEnabled(context, false)
        com.example.security.CredentialSettingsStore.setAllowPasskeyUpgradeEnabled(context, false)
        com.example.security.CredentialSettingsStore.setAutoSignInEnabled(context, false)
        com.example.security.CredentialSettingsStore.setPasswordAlertsEnabled(context, false)

        com.example.security.CredentialSettingsStore.reloadFromPrefs(context)

        assertFalse(com.example.security.CredentialSettingsStore.offerToSavePasswords.value)
        assertFalse(com.example.security.CredentialSettingsStore.autoCreatePasskeyEnabled.value)
        assertFalse(com.example.security.CredentialSettingsStore.allowPasskeyUpgradeEnabled.value)
        assertFalse(com.example.security.CredentialSettingsStore.autoSignInEnabled.value)
        assertFalse(com.example.security.CredentialSettingsStore.passwordAlertsEnabled.value)

        // Restore defaults
        com.example.security.CredentialSettingsStore.setOfferToSavePasswords(context, true)
        com.example.security.CredentialSettingsStore.setAutoCreatePasskeyEnabled(context, true)
        com.example.security.CredentialSettingsStore.setAllowPasskeyUpgradeEnabled(context, true)
        com.example.security.CredentialSettingsStore.setAutoSignInEnabled(context, true)
        com.example.security.CredentialSettingsStore.setPasswordAlertsEnabled(context, true)
    }

    @Test
    fun passwordBreachMonitorUsesKAnonymityPrefixOnlyAndDetectsExposedPasswords() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        com.example.security.CredentialSettingsStore.reloadFromPrefs(context)
        com.example.security.CredentialSettingsStore.setPasswordAlertsEnabled(context, true)

        val sha1 = com.example.security.PasswordBreachMonitor.sha1HexUppercase("password")
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", sha1)

        val suffix = sha1.substring(5)
        val sampleRangeBody = """
            0018A45C4D1DEF81644B54AB7F969B88D65:0
            $suffix:9545824
            00D4F6E8FA6EECAD2A3AA415EEC418D38EC:2
        """.trimIndent()
        assertEquals(9545824, com.example.security.PasswordBreachMonitor.parseRangeMatchCount(sampleRangeBody, suffix))
        assertEquals(0, com.example.security.PasswordBreachMonitor.parseRangeMatchCount(sampleRangeBody, "0018A45C4D1DEF81644B54AB7F969B88D65"))

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = PasswordVaultRepository(db.vaultCredentialDao())
            val testKey = "unit-test-only-vault-aes-key-2026"
            val exposed = repo.savePasswordCredential(
                url = "https://weak.example.org/login",
                siteName = "Weak Site",
                username = "alice",
                rawPassword = "password",
                keyPhrase = testKey
            )
            val safe = repo.savePasswordCredential(
                url = "https://strong.example.org/login",
                siteName = "Strong Site",
                username = "bob",
                rawPassword = "Unique#Vault!Test#9921_Kaspa",
                keyPhrase = testKey
            )

            val transmittedPrefixes = mutableListOf<String>()
            val summary = com.example.security.PasswordBreachMonitor.auditVaultCredentials(
                context = context,
                credentials = repo.getAllList(),
                repository = repo,
                notifyOnNewBreach = false,
                keyPhrase = testKey,
                prefixFetcher = { prefix5 ->
                    transmittedPrefixes.add(prefix5)
                    assertEquals("Only 5-hex SHA-1 prefix must leave device", 5, prefix5.length)
                    if (prefix5 == "5BAA6") sampleRangeBody else "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:0"
                }
            )

            assertEquals(2, summary.checkedCount)
            assertEquals(9545824, summary.breachedMap[exposed.id])
            assertFalse(summary.breachedMap.containsKey(safe.id))
            assertTrue(transmittedPrefixes.contains("5BAA6"))

            // Deduplication check
            val firstNotify = com.example.security.CredentialSettingsStore.markAndFilterUnnotifiedBreaches(
                context,
                listOf(exposed.id to "5BAA6_1E4C9B:9545824")
            )
            assertTrue(firstNotify.contains(exposed.id))
            val secondNotify = com.example.security.CredentialSettingsStore.markAndFilterUnnotifiedBreaches(
                context,
                listOf(exposed.id to "5BAA6_1E4C9B:9545824")
            )
            assertTrue("Duplicate breach alerts for unchanged credential must be suppressed", secondNotify.isEmpty())

            // Cleanup on credential removal
            com.example.security.CredentialSettingsStore.removeCredentialBreachState(context, exposed.id)
            assertFalse(com.example.security.CredentialSettingsStore.breachedCredentials.value.containsKey(exposed.id))
        } finally {
            db.close()
        }
    }

    @Test
    fun webAuthnBridgeScriptFixesBinaryEncodingConditionalUpgradeAndAutoSignInGating() {
        val script = com.example.network.KaspaWebAuthnBridge.buildInjectionScript(
            """{"webAuthEnabled":true,"autoCreatePasskey":false,"allowPasskeyUpgrade":true,"autoSignIn":false,"skipNativeWebView":false}"""
        )
        assertTrue(
            "bufToB64 must respect byteOffset and byteLength for Uint8Array views",
            script.contains("new Uint8Array(buf.buffer, buf.byteOffset, buf.byteLength)")
        )
        assertTrue(
            "userHandle must be null when omitted",
            script.contains("resp.userHandle ? b64ToBuf(resp.userHandle) : null")
        )
        assertTrue(
            "getClientCapabilities must expose conditionalCreate for passkey upgrades",
            script.contains("getClientCapabilities") && script.contains("conditionalCreate")
        )
        assertTrue(
            "Explicit confirmation gates must exist for autoSignIn and autoCreatePasskey",
            script.contains("CONFIRM_SIGN_IN") && script.contains("CONFIRM_CREATE_OR_UPGRADE")
        )
        assertTrue(
            "DOMException errors must preserve WebAuthn error names instead of forcing NotAllowedError everywhere",
            script.contains("makeDomError")
        )
        assertTrue(
            "WebAuthn bridge must fall back on platform/origin restriction messages regardless of timeout duration",
            script.contains("looksPlatformError") && script.contains("privileged")
        )
    }

    @Test
    fun manifestDeclaresCredentialManagerSetOriginAndLoginPagesAreNotSkippedAsBotChallenges() {
        val context = RuntimeEnvironment.getApplication()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        )
        val requestedPerms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(
            "AndroidManifest must declare CREDENTIAL_MANAGER_SET_ORIGIN for WebView FOR_BROWSER passkeys",
            requestedPerms.contains("android.permission.CREDENTIAL_MANAGER_SET_ORIGIN")
        )

        assertFalse(
            "GitHub login page must NOT be skipped as a bot challenge",
            com.example.network.KaspaPrivacyEngine.isBotChallengeInterstitialUrl("https://github.com/login")
        )
        assertFalse(
            "Passkeys demo page must NOT be skipped as a bot challenge",
            com.example.network.KaspaPrivacyEngine.isBotChallengeInterstitialUrl("https://www.passkeys.io/")
        )
        assertFalse(
            "WebAuthn endpoint must NOT be skipped as a bot challenge",
            com.example.network.KaspaPrivacyEngine.isBotChallengeInterstitialUrl("https://example.org/auth/webauthn")
        )
        assertTrue(
            "Cloudflare Turnstile interstitial must still be detected",
            com.example.network.KaspaPrivacyEngine.isBotChallengeInterstitialUrl("https://challenges.cloudflare.com/turnstile/v0/api.js")
        )
    }

    @Test
    fun autofillBridgeScannerIncludesSpaSubmitCapture() {
        val js = PasswordAutofillBridge.JS_FORM_SCANNER
        assertTrue(js.contains("addEventListener('submit'"))
        assertTrue(js.contains("addEventListener('click'"))
        assertTrue(js.contains("e.key === 'Enter'"))
    }

    @Test
    fun allowlistRegistrationKitAndDefaultBrowserRoleAreConfigured() {
        val context = RuntimeEnvironment.getApplication()
        val status = BrowserEngineManager.refreshEngineStatus(context)

        assertEquals(context.packageName, status.packageName)
        assertNotEquals("Custom Tabs must never resolve back to host app package", context.packageName, status.customTabsProvider)

        val formatted = BrowserEngineManager.formatAllowlistSubmissionText(status)
        assertTrue(formatted.contains(status.packageName))
        assertTrue(formatted.contains(status.signingSha256))
        assertTrue(formatted.contains(BrowserEngineManager.GOOGLE_ALLOWLIST_FORM_URL))

        val roleIntent = BrowserEngineManager.createDefaultBrowserRoleIntent(context)
        assertNotNull("Default browser role / settings intent must be creatable", roleIntent)
    }

    @Test
    fun kazviewNativeCoreGatesEligibilityAndCooldownSafelyWithoutNdk() {
        com.example.engine.KazviewNativeCore.resetWarmupCache()
        assertFalse(com.example.engine.KazviewNativeCore.modeLabel().isBlank())

        assertTrue(com.example.engine.KazviewNativeCore.isSpeculativeEligible("https://kaspa.org", privacyBlocked = false, lowRamDevice = false))
        assertFalse("Privacy/Tor/Incognito must block speculative preload", com.example.engine.KazviewNativeCore.isSpeculativeEligible("https://kaspa.org", privacyBlocked = true, lowRamDevice = false))
        assertFalse("Low-RAM device must block speculative preload", com.example.engine.KazviewNativeCore.isSpeculativeEligible("https://kaspa.org", privacyBlocked = false, lowRamDevice = true))
        assertFalse("HTTP must not speculatively preload", com.example.engine.KazviewNativeCore.isSpeculativeEligible("http://kaspa.org", privacyBlocked = false, lowRamDevice = false))
        assertFalse("Login/auth pages must not speculatively preload", com.example.engine.KazviewNativeCore.isSpeculativeEligible("https://github.com/login", privacyBlocked = false, lowRamDevice = false))
        assertFalse("Turnstile/challenges must not speculatively preload", com.example.engine.KazviewNativeCore.isSpeculativeEligible("https://challenges.cloudflare.com/turnstile", privacyBlocked = false, lowRamDevice = false))

        assertTrue(com.example.engine.KazviewNativeCore.tryAcquireOriginWarmupSlot("https://kaspa.org", nowMs = 10_000L, cooldownMs = 60_000L))
        assertFalse("Origin within cooldown must be throttled", com.example.engine.KazviewNativeCore.tryAcquireOriginWarmupSlot("https://kaspa.org", nowMs = 20_000L, cooldownMs = 60_000L))
        assertTrue("Origin after cooldown window should re-acquire", com.example.engine.KazviewNativeCore.tryAcquireOriginWarmupSlot("https://kaspa.org", nowMs = 80_000L, cooldownMs = 60_000L))
    }

    @Test
    fun kazviewStandbyPoolSpeculativeCancelAndMemoryPressureEvictionWorkSafely() {
        val context = RuntimeEnvironment.getApplication()
        com.example.engine.KazviewNativeCore.resetWarmupCache()

        // Privacy / crash guardrails must suppress speculative scheduling cleanly
        assertFalse(
            BrowserEngineManager.scheduleSpeculativePreload(
                context = context,
                rawUrl = "https://kaspa.org",
                allowPrerender = true,
                isPrivacyBlocked = true,
                hasRendererCrash = false
            )
        )
        assertFalse(
            BrowserEngineManager.scheduleSpeculativePreload(
                context = context,
                rawUrl = "https://kaspa.org",
                allowPrerender = true,
                isPrivacyBlocked = false,
                hasRendererCrash = true
            )
        )

        // First-paint latency and standby pool lifecycle
        BrowserEngineManager.recordFirstPaintLatency(142L)
        assertEquals(142L, BrowserEngineManager.engineStatus.value.lastFirstPaintMs)

        BrowserEngineManager.prepareStandbyWebView(context, isIncognito = false)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val consumed = BrowserEngineManager.consumeStandbyWebView(context, isIncognito = false)
        try {
            assertNotNull("Standby WebView should be reusable for fast tab creation", consumed)
        } finally {
            consumed?.destroy()
        }

        BrowserEngineManager.prepareStandbyWebView(context, isIncognito = false)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val evictionsBefore = BrowserEngineManager.engineStatus.value.memoryEvictionsCount
        BrowserEngineManager.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, context)
        assertTrue(
            "Memory pressure trim should increment eviction count and clear standby renderer",
            BrowserEngineManager.engineStatus.value.memoryEvictionsCount > evictionsBefore
        )
        assertFalse(BrowserEngineManager.engineStatus.value.standbyRendererReady)
    }

    @Test
    fun webViewAssetLruCacheWarmSubresourceEnforcesSafetyGates() {
        val context = RuntimeEnvironment.getApplication()
        com.example.network.WebViewAssetLruCache.initialize(context)
        assertFalse(com.example.network.WebViewAssetLruCache.warmSubresourceAsync("http://example.com/app.css"))
        assertFalse(com.example.network.WebViewAssetLruCache.warmSubresourceAsync("https://example.com/cdn-cgi/challenge-platform/h/b"))
        assertFalse(com.example.network.WebViewAssetLruCache.warmSubresourceAsync("https://example.com/login"))
    }

    @Test
    fun originAssetManifestStoreAndWebVitalsMetricsWorkWithPrivacyAndBounds() {
        val context = RuntimeEnvironment.getApplication()
        com.example.network.OriginAssetManifestStore.clear()
        com.example.network.OriginAssetManifestStore.initialize(context)

        // Privacy mode blocks recording & warming
        assertFalse(
            com.example.network.OriginAssetManifestStore.recordCriticalAsset(
                "https://example.org/docs",
                "https://example.org/static/app.css",
                isPrivacyBlocked = true
            )
        )
        // Non-HTTPS, images, localhost, and challenge assets must be rejected
        assertFalse(
            com.example.network.OriginAssetManifestStore.recordCriticalAsset(
                "http://example.org/",
                "https://example.org/static/app.css",
                isPrivacyBlocked = false
            )
        )
        assertFalse(
            com.example.network.OriginAssetManifestStore.recordCriticalAsset(
                "https://127.0.0.1/",
                "https://127.0.0.1/app.css",
                isPrivacyBlocked = false
            )
        )
        assertFalse(
            com.example.network.OriginAssetManifestStore.recordCriticalAsset(
                "https://example.org/",
                "https://example.org/hero.png",
                isPrivacyBlocked = false
            )
        )
        assertFalse(
            com.example.network.OriginAssetManifestStore.recordCriticalAsset(
                "https://example.org/",
                "https://example.org/cdn-cgi/challenge-platform/h/b.js",
                isPrivacyBlocked = false
            )
        )

        // Record out of order -> manifest must sort CSS -> WOFF2 -> JS
        assertTrue(
            com.example.network.OriginAssetManifestStore.recordCriticalAsset(
                "https://example.org/explorer",
                "https://example.org/assets/bundle.js",
                isPrivacyBlocked = false
            )
        )
        assertTrue(
            com.example.network.OriginAssetManifestStore.recordCriticalAsset(
                "https://example.org/explorer",
                "https://example.org/assets/font.woff2",
                isPrivacyBlocked = false
            )
        )
        assertTrue(
            com.example.network.OriginAssetManifestStore.recordCriticalAsset(
                "https://example.org/explorer",
                "https://example.org/assets/main.css",
                isPrivacyBlocked = false
            )
        )
        val sorted = com.example.network.OriginAssetManifestStore.getAssetsForOrigin("https://example.org/other")
        assertEquals(
            listOf(
                "https://example.org/assets/main.css",
                "https://example.org/assets/font.woff2",
                "https://example.org/assets/bundle.js"
            ),
            sorted
        )

        // Enforce per-origin cap cleanly
        for (i in 1..15) {
            com.example.network.OriginAssetManifestStore.recordCriticalAsset(
                "https://example.org/",
                "https://example.org/assets/chunk_$i.js",
                isPrivacyBlocked = false
            )
        }
        assertEquals(
            com.example.network.OriginAssetManifestStore.MAX_ASSETS_PER_ORIGIN,
            com.example.network.OriginAssetManifestStore.getAssetsForOrigin("https://example.org").size
        )

        // Privacy relay blocks warming even when assets exist
        assertEquals(
            0,
            com.example.network.OriginAssetManifestStore.warmOriginAssets(
                "https://example.org",
                isPrivacyBlocked = true
            )
        )

        // Web Vitals + Visual Commit + Manifest Warm counters
        val visualBefore = BrowserEngineManager.engineStatus.value.visualCommitsCount
        val manifestBefore = BrowserEngineManager.engineStatus.value.manifestWarmCount
        BrowserEngineManager.recordWebVitals(ttfbMs = 42L, domInteractiveMs = 115L, fcpMs = 168L)
        BrowserEngineManager.recordVisualCommit()
        BrowserEngineManager.recordManifestWarm()
        val updated = BrowserEngineManager.engineStatus.value
        assertEquals(42L, updated.lastTtfbMs)
        assertEquals(115L, updated.lastDomInteractiveMs)
        assertEquals(168L, updated.lastFcpMs)
        assertEquals(visualBefore + 1, updated.visualCommitsCount)
        assertEquals(manifestBefore + 1, updated.manifestWarmCount)

        // Background memory pressure evicts manifests too
        BrowserEngineManager.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, context)
        assertEquals(0, com.example.network.OriginAssetManifestStore.getOriginCount())
    }
}

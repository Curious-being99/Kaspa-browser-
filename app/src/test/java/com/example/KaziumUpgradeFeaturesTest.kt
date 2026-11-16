package com.example

import com.example.network.KaspaWebAuthnBridge
import com.example.network.siagent.KaziumKnowledgeBase
import com.example.network.siagent.SiAgentMode
import com.example.security.AuthenticatorRepository
import com.example.security.OtpAuthType
import com.example.security.OtpHashAlgorithm
import com.example.security.PasswordGenerator
import com.example.security.TotpAuthenticator
import org.json.JSONObject
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
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KaziumUpgradeFeaturesTest {

    @Test
    fun totpAndHotpMatchOfficialRfc4226AndRfc6238TestVectors() {
        val sha1SecretB32 = TotpAuthenticator.encodeBase32(
            "12345678901234567890".toByteArray(StandardCharsets.US_ASCII)
        )
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", sha1SecretB32)

        // RFC 4226 Appendix D HOTP vectors (6 digits)
        assertEquals("755224", TotpAuthenticator.generateHotpCode(sha1SecretB32, 0L, digits = 6))
        assertEquals("287082", TotpAuthenticator.generateHotpCode(sha1SecretB32, 1L, digits = 6))
        assertEquals("359152", TotpAuthenticator.generateHotpCode(sha1SecretB32, 2L, digits = 6))

        // RFC 6238 Appendix B TOTP vectors at T = 59s (8 digits)
        assertEquals(
            "94287082",
            TotpAuthenticator.generateTotpCode(sha1SecretB32, timeSeconds = 59L, periodSeconds = 30, digits = 8, algorithm = OtpHashAlgorithm.SHA1)
        )

        val sha256SecretB32 = TotpAuthenticator.encodeBase32(
            "12345678901234567890123456789012".toByteArray(StandardCharsets.US_ASCII)
        )
        assertEquals(
            "46119246",
            TotpAuthenticator.generateTotpCode(sha256SecretB32, timeSeconds = 59L, periodSeconds = 30, digits = 8, algorithm = OtpHashAlgorithm.SHA256)
        )

        val sha512SecretB32 = TotpAuthenticator.encodeBase32(
            "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(StandardCharsets.US_ASCII)
        )
        assertEquals(
            "90693936",
            TotpAuthenticator.generateTotpCode(sha512SecretB32, timeSeconds = 59L, periodSeconds = 30, digits = 8, algorithm = OtpHashAlgorithm.SHA512)
        )

        val snapshot = TotpAuthenticator.computeTotpSnapshot(sha1SecretB32, nowMillis = 59_000L, periodSeconds = 30, digits = 8)
        assertEquals("94287082", snapshot.code)
        assertEquals("9428 7082", snapshot.formattedCode)
        assertEquals(1, snapshot.remainingSeconds)
    }

    @Test
    fun authenticatorUriParsingAndEncryptedRepositoryPersistSafely() {
        val context = RuntimeEnvironment.getApplication()
        val uri = "otpauth://totp/Kazium:alice%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=Kazium&algorithm=SHA256&digits=8&period=30"
        val parsed = TotpAuthenticator.parseOtpAuthUri(uri)
        assertNotNull(parsed)
        assertEquals(OtpAuthType.TOTP, parsed!!.type)
        assertEquals("Kazium", parsed.issuer)
        assertEquals("alice@example.com", parsed.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", parsed.base32Secret)
        assertEquals(OtpHashAlgorithm.SHA256, parsed.algorithm)
        assertEquals(8, parsed.digits)

        val repo = AuthenticatorRepository(context, keyPhrase = "unit-test-2fa-aes-key-2026")
        val added = repo.addFromOtpAuthUri(uri)
        assertEquals("JBSWY3DPEHPK3PXP", repo.decryptSecret(added))
        assertNotEquals("JBSWY3DPEHPK3PXP", added.encryptedSecret)

        val hotp = repo.addManual(
            issuer = "HardwareGate",
            accountName = "ops",
            rawBase32Secret = "GEZDGNBVGY3TQOJQ",
            type = OtpAuthType.HOTP,
            counter = 5L
        )
        val incremented = repo.incrementHotpCounter(hotp.id)
        assertEquals(6L, incremented?.counter)

        repo.deleteAccount(added.id)
        repo.deleteAccount(hotp.id)
        assertTrue(repo.accounts.value.isEmpty())
    }

    @Test
    fun passwordGeneratorNeverProducesDuplicatesSequentiallyOrConcurrently() {
        val context = RuntimeEnvironment.getApplication()
        PasswordGenerator.init(context)

        val seen = Collections.synchronizedSet(HashSet<String>())
        repeat(1500) {
            val pwd = PasswordGenerator.generateUnique(length = 24)
            assertEquals(24, pwd.length)
            assertTrue("Password collision detected at iteration $it", seen.add(pwd))
        }

        val pool = Executors.newFixedThreadPool(4)
        try {
            repeat(400) {
                pool.submit {
                    val pwd = PasswordGenerator.generateUnique(length = 20)
                    assertTrue("Concurrent password collision detected", seen.add(pwd))
                }
            }
            pool.shutdown()
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1900, seen.size)
    }

    @Test
    fun webAuthnJsonSanitizerStripsNullsAndBridgePreservesNativePasskeySpec() {
        val dirtyCreate = JSONObject(
            """
            {
              "challenge": "abc123",
              "timeout": null,
              "rp": {"name": "Example", "id": null},
              "user": {"id": "dXNlcg", "name": "alice", "displayName": "Alice"},
              "extensions": {}
            }
            """.trimIndent()
        )
        val cleanCreate = KaspaWebAuthnBridge.sanitizeCredentialJsonForManager(
            source = dirtyCreate,
            fallbackRpId = "app.example.org",
            isCreate = true
        )
        assertFalse("null timeout must be omitted for GMSCore FIDO2", cleanCreate.has("timeout"))
        assertFalse("empty extensions must be omitted", cleanCreate.has("extensions"))
        assertEquals("app.example.org", cleanCreate.getJSONObject("rp").getString("id"))
        assertTrue("default pubKeyCredParams must be populated", cleanCreate.getJSONArray("pubKeyCredParams").length() >= 2)

        val dirtyGet = JSONObject("""{"challenge":"xyz","timeout":null,"rpId":null}""")
        val cleanGet = KaspaWebAuthnBridge.sanitizeCredentialJsonForManager(
            source = dirtyGet,
            fallbackRpId = "login.example.org",
            isCreate = false
        )
        assertFalse(cleanGet.has("timeout"))
        assertEquals("login.example.org", cleanGet.getString("rpId"))

        val script = KaspaWebAuthnBridge.buildInjectionScript(
            """{"webAuthEnabled":true,"autoCreatePasskey":false,"allowPasskeyUpgrade":true,"autoSignIn":false,"skipNativeWebView":false}"""
        )
        assertTrue(script.contains("Symbol.hasInstance"))
        assertTrue(script.contains("isConditionalMediationAvailable"))
        assertTrue(script.contains("sanitizeForCredentialManager"))
    }

    @Test
    fun siAgentKnowsKaziumBrandWhatsNewModesAndFlexibleCommands() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("/whatsnew", KaziumKnowledgeBase.normalizeFlexibleCommand("/new"))
        assertEquals("/authenticator", KaziumKnowledgeBase.normalizeFlexibleCommand("open 2fa authenticator"))
        assertEquals("/vault", KaziumKnowledgeBase.normalizeFlexibleCommand("show password vault"))
        assertEquals("/arcade", KaziumKnowledgeBase.normalizeFlexibleCommand("play offline game"))
        assertEquals("/wallet", KaziumKnowledgeBase.normalizeFlexibleCommand("check my balance"))

        val browserReply = KaziumKnowledgeBase.respondIntelligently(
            context = context,
            rawInput = "What is this browser called?",
            mode = SiAgentMode.ADAPTIVE,
            activeUrl = "https://kaspa.org",
            activeTitle = "Kaspa",
            selectedNetwork = "Kaspa Mainnet"
        )
        assertTrue(browserReply.contains("Kazium"))
        assertTrue(browserReply.contains("Kazview"))

        val whatsNewDeep = KaziumKnowledgeBase.respondIntelligently(
            context = context,
            rawInput = "What's new in Kazium?",
            mode = SiAgentMode.DEEP,
            activeUrl = "",
            activeTitle = "",
            selectedNetwork = "Kaspa Mainnet"
        )
        assertTrue(whatsNewDeep.contains("Passkeys") && whatsNewDeep.contains("Authenticator"))

        val whatsNewConcise = KaziumKnowledgeBase.respondIntelligently(
            context = context,
            rawInput = "What's new?",
            mode = SiAgentMode.CONCISE,
            activeUrl = "",
            activeTitle = "",
            selectedNetwork = "Kaspa Mainnet"
        )
        assertTrue(whatsNewConcise.length < whatsNewDeep.length)

        val authReply = KaziumKnowledgeBase.respondIntelligently(
            context = context,
            rawInput = "How does the 2FA authenticator work?",
            mode = SiAgentMode.ADAPTIVE,
            activeUrl = "",
            activeTitle = "",
            selectedNetwork = "Kaspa Mainnet"
        )
        assertTrue(authReply.contains("RFC 6238") && authReply.contains("Base32"))

        val sompiReply = KaziumKnowledgeBase.respondIntelligently(
            context = context,
            rawInput = "2 kas to sompi",
            mode = SiAgentMode.CONCISE,
            activeUrl = "",
            activeTitle = "",
            selectedNetwork = "Kaspa Mainnet"
        )
        assertTrue(sompiReply.contains("200,000,000"))

        KaziumKnowledgeBase.saveMode(context, SiAgentMode.DEEP)
        assertEquals(SiAgentMode.DEEP, KaziumKnowledgeBase.loadMode(context))
        KaziumKnowledgeBase.saveMode(context, SiAgentMode.ADAPTIVE)
    }
}

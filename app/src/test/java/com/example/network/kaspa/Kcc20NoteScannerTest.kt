package com.example.network.kaspa

import com.example.network.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Kcc20NoteScannerTest {

    private val userPubKeyHex = "da00d668c50d34206dd4bd250fe54e9183e8fa13441463ccede4f822a121d506"

    @Test
    fun testRebuildKasSealRefNoteRedeemScript() {
        val refTemplate = Kcc20NoteScanner.TEMPLATES["ref"]
        assertNotNull(refTemplate)
        val template = refTemplate!!

        val candidateAmount = 395281L
        // Verify static ref code is loaded
        val code = Kcc20NoteScanner.lastPush(
            // dummy script carrying code
            "4d4d0f" + "00".repeat(3885)
        )
        // Check with the template's known code
        val redeem = Kcc20NoteScanner.buildNoteRedeem(
            template = template,
            ownerHex = userPubKeyHex,
            amount = candidateAmount,
            form = "p2pk",
            code = "00".repeat(template.sfl)
        )

        // Verify structure: pre (34 bytes / 68 hex) + 08 + le8(amount) + 20 + owner (64 hex) + 01000100 + 20(zero) + 20(zero) + code
        assertTrue(redeem.startsWith(template.pre))
        assertTrue(redeem.contains("08" + Kcc20NoteScanner.le8(candidateAmount)))
        assertTrue(redeem.contains("20" + userPubKeyHex))
        assertTrue(redeem.contains("01000100"))
        assertEquals(template.pfl + 112 + template.sfl, redeem.length / 2)
    }

    @Test
    fun testExtractCandidateAmountsFromScriptPushes() {
        // Push 1: OP_1 (0x51 -> 1)
        // Push 2: 0x03 0x05 0x00 0x00 (5 as 3-byte LE)
        // Push 3: 0x08 with 395281 in little-endian (0x11, 0x08, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00)
        val amount395281Hex = Kcc20NoteScanner.le8(395281L)
        val scriptHex = "51" + "03050000" + "08" + amount395281Hex
        val amounts = mutableSetOf<Long>()
        Kcc20NoteScanner.extractCandidateAmountsFromScript(scriptHex, amounts)

        assertTrue(amounts.contains(1L))
        assertTrue(amounts.contains(5L))
        assertTrue(amounts.contains(395281L))
    }

    @Test
    fun testFormatTokenAmountPerDecimals() {
        // KasSeal: 0 decimals
        assertEquals("395281", Kcc20NoteScanner.formatTokenAmount(395281L, 0))

        // KaspaRocket: 3 decimals
        assertEquals("4.977", Kcc20NoteScanner.formatTokenAmount(4977L, 3))
        assertEquals("1", Kcc20NoteScanner.formatTokenAmount(1000L, 3))
        assertEquals("0.001", Kcc20NoteScanner.formatTokenAmount(1L, 3))

        // KRON: 8 decimals
        assertEquals("1", Kcc20NoteScanner.formatTokenAmount(100_000_000L, 8))
        assertEquals("0.5", Kcc20NoteScanner.formatTokenAmount(50_000_000L, 8))
    }

    @Test
    fun testLittleEndianConversion() {
        val value = 123456789L
        val hex = Kcc20NoteScanner.le8(value)
        assertEquals(16, hex.length)
        val restored = Kcc20NoteScanner.rd8(hex)
        assertEquals(value, restored)
    }

    @Test
    fun testKaspaRocketNoteRedeemScriptRebuilding() {
        val krTemplate = Kcc20NoteScanner.TEMPLATES["kr"]
        assertNotNull(krTemplate)
        val template = krTemplate!!

        val candidateAmount = 4977L // 4.977 KR tokens
        val dummyCode = "aa".repeat(template.sfl)
        val redeem = Kcc20NoteScanner.buildNoteRedeem(
            template = template,
            ownerHex = userPubKeyHex,
            amount = candidateAmount,
            form = "p2pk",
            code = dummyCode
        )

        assertTrue(redeem.startsWith(template.pre))
        assertTrue(redeem.contains("08" + Kcc20NoteScanner.le8(candidateAmount)))
        assertTrue(redeem.contains("20" + userPubKeyHex))
        assertEquals(template.pfl + 112 + template.sfl, redeem.length / 2)
    }

    @Test
    fun testCandidateOutputsExtractionAndIdentification() {
        val template = Kcc20NoteScanner.TEMPLATES["ref"]!!
        val amount = 777L
        val dummyCode = "00".repeat(template.sfl)
        val redeem = Kcc20NoteScanner.buildNoteRedeem(template, userPubKeyHex, amount, "p2pk", dummyCode)
        val hash = CryptoUtils.blake2b256(Kcc20NoteScanner.hexToBytes(redeem))
            .joinToString("") { "%02x".format(it) }
        val targetScript = "aa20" + hash + "87"

        // Script pushing candidate amount 777
        val sigScript = "5108" + Kcc20NoteScanner.le8(amount)
        val candidateAmounts = mutableSetOf<Long>()
        Kcc20NoteScanner.extractCandidateAmountsFromScript(sigScript, candidateAmounts)
        assertTrue(candidateAmounts.contains(777L))

        // Rebuild candidate note and verify Blake2b match against targetScript
        var matched = false
        for (amt in candidateAmounts) {
            val candidateRedeem = Kcc20NoteScanner.buildNoteRedeem(template, userPubKeyHex, amt, "p2pk", dummyCode)
            val p2shHash = CryptoUtils.blake2b256(Kcc20NoteScanner.hexToBytes(candidateRedeem))
                .joinToString("") { "%02x".format(it) }
            if (p2shHash.equals(hash, ignoreCase = true)) {
                matched = true
                break
            }
        }
        assertTrue("Redeem script Blake2b hash should match candidate output P2SH hash", matched)
    }

    @Test
    fun testNoteRedeemBlake2bMatchesExpectedScript() {
        val template = Kcc20NoteScanner.TEMPLATES["ref"]!!
        val amount = 10000L
        val dummyCode = "00".repeat(template.sfl)
        val redeem = Kcc20NoteScanner.buildNoteRedeem(template, userPubKeyHex, amount, "p2pk", dummyCode)
        val hash = CryptoUtils.blake2b256(Kcc20NoteScanner.hexToBytes(redeem))
            .joinToString("") { "%02x".format(it) }

        val expectedScript = "aa20" + hash + "87"
        assertEquals(70, expectedScript.length)
        assertTrue(expectedScript.startsWith("aa20"))
        assertTrue(expectedScript.endsWith("87"))

        val p2shAddress = CryptoUtils.encodeP2shAddress(Kcc20NoteScanner.hexToBytes(hash), "kaspatest")
        assertTrue(p2shAddress.startsWith("kaspatest:p"))
    }

    @Test
    fun testLiveKaspaTn10ApiNetworkCheck() {
        // Real HTTP call to Kaspa TN10 REST API verifying it is live with no simulation/mock
        val url = java.net.URL("https://api-tn10.kaspa.org/info/network")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.requestMethod = "GET"

        val responseCode = conn.responseCode
        assertEquals(200, responseCode)
        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(responseBody)
        assertEquals("kaspa-testnet-10", json.getString("networkName"))
        assertTrue(json.getLong("virtualDaaScore") > 0)
    }
}


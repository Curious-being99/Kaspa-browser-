package com.example

import com.example.dapp.KaspaDappBridge
import com.example.network.CryptoUtils
import com.example.network.kaspa.KbKcc20ScriptCodec
import com.example.network.kaspa.KbKcc20StandardAssembler
import com.example.network.kaspa.Kcc20KasivoService
import com.example.network.kaspa.Kcc20ReferenceProtocol
import com.example.network.kaspa.Kcc20WalletToken
import com.example.ui.Kcc20AmountValidationError
import com.example.ui.formatKcc20BalanceDisplay
import com.example.ui.validateKcc20SendAmount
import com.example.viewmodel.DecentralViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.BigInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeneralKcc20TransferTest {

    private fun testKeyPair(fill: Byte = 0x11): ByteArray = ByteArray(32) { fill }

    @Test
    fun unnamedGeneralKcc20TokenDoesNotDefaultToKb() {
        val arbitraryContract = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        val meta = Kcc20KasivoService.resolveTokenMetadataFromJson(arbitraryContract, JSONObject())
        assertEquals("KCC-1234", meta.ticker)
        assertEquals("KCC-1234", meta.name)
        assertEquals(0, meta.decimals)
        assertNull(meta.imageUrl)
    }

    @Test
    fun kasivoMetadataUsesAuthoritativeFieldsForArbitraryTokens() {
        val arbitraryContract = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        val tokenJson = JSONObject()
            .put("claimed_ticker", "ALPHA")
            .put("claimed_name", "Alpha Covenant")
            .put("claimed_decimals", 6)
            .put("icon_url", "https://example.com/alpha.png")
        val meta = Kcc20KasivoService.resolveTokenMetadataFromJson(arbitraryContract, tokenJson)
        assertEquals("ALPHA", meta.ticker)
        assertEquals("Alpha Covenant", meta.name)
        assertEquals(6, meta.decimals)
        assertEquals("https://example.com/alpha.png", meta.imageUrl)
    }

    @Test
    fun iceKnownTokenFallsBackToZeroDecimalsWhenIndexerOmitsDecimals() {
        val iceContract = "b20f807bed621ca3ea3aaf1f605c6d16d3e3efc766aedefd089db019ceb30526"
        val meta = Kcc20KasivoService.resolveTokenMetadataFromJson(iceContract, JSONObject())
        assertEquals("ICE", meta.ticker)
        assertEquals(0, meta.decimals)
    }

    @Test
    fun viewModelMergePreservesGeneralTokenDecimalsAndTickerFromKasCov() {
        val arbitraryContract = "9999567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        val scannedPlaceholder = Kcc20WalletToken(
            contractId = arbitraryContract,
            ticker = "999956",
            name = "KCC-20 999956",
            balanceRaw = "15000000",
            decimals = 0,
            holderCount = null,
            validationStatus = "verified_onchain_note",
            marketPriceKas = null,
            marketPriceUsd = null,
            marketPriceSource = "Kaspa Covenant",
            priceUpdatedAtMs = null,
            priceChange24hPercent = null,
            imageUrl = null
        )
        val enrichedFromKascov = Kcc20WalletToken(
            contractId = arbitraryContract,
            ticker = "ZETA",
            name = "Zeta Protocol",
            balanceRaw = "0",
            decimals = 6,
            holderCount = 42L,
            validationStatus = "verified",
            marketPriceKas = "1.25",
            marketPriceUsd = "0.10",
            marketPriceSource = "Kascov",
            priceUpdatedAtMs = null,
            priceChange24hPercent = "2.5",
            imageUrl = "https://example.com/zeta.png"
        )

        val merged = DecentralViewModel.mergeKcc20TokenMetadata(scannedPlaceholder, enrichedFromKascov)
        assertEquals("15000000", merged.balanceRaw)
        assertEquals("ZETA", merged.ticker)
        assertEquals("Zeta Protocol", merged.name)
        assertEquals(6, merged.decimals)
        assertEquals("https://example.com/zeta.png", merged.imageUrl)
    }

    @Test
    fun dappBridgeResolvesAnyKcc20TokenWithoutKbSpecialCasing() {
        val customCov = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val walletTokens = listOf(
            Kcc20WalletToken(
                contractId = customCov,
                ticker = "NOVA",
                name = "Nova Token",
                balanceRaw = "500000000",
                decimals = 6,
                holderCount = 10L,
                validationStatus = "verified",
                marketPriceKas = null,
                marketPriceUsd = null,
                marketPriceSource = "Kascov",
                priceUpdatedAtMs = null,
                priceChange24hPercent = null,
                imageUrl = null
            )
        )

        // 1. Lookup by contractId without explicit tick/decimals
        val byCov = KaspaDappBridge.resolveKcc20TransferSpec(
            paramsObj = JSONObject().put("contractId", customCov).put("amt", "2.5"),
            options = null,
            walletTokens = walletTokens
        )
        assertNull(byCov.errorMessage)
        assertEquals(customCov, byCov.covenantId)
        assertEquals("NOVA", byCov.ticker)
        assertEquals(6, byCov.decimals)

        // 2. Lookup by unique wallet ticker
        val byTick = KaspaDappBridge.resolveKcc20TransferSpec(
            paramsObj = JSONObject().put("tick", "nova").put("amt", "1.0"),
            options = null,
            walletTokens = walletTokens
        )
        assertNull(byTick.errorMessage)
        assertEquals(customCov, byTick.covenantId)
        assertEquals(6, byTick.decimals)

        // 3. Rejects ambiguous ticker across two different contracts
        val duplicateTokens = walletTokens + walletTokens[0].copy(
            contractId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )
        val ambiguous = KaspaDappBridge.resolveKcc20TransferSpec(
            paramsObj = JSONObject().put("tick", "NOVA").put("amt", "1.0"),
            options = null,
            walletTokens = duplicateTokens
        )
        assertNotNull(ambiguous.errorMessage)
        assertTrue(ambiguous.errorMessage!!.contains("specify covenantId"))
    }

    @Test
    fun stateFieldCellReconstructsVerifiesAndPlansArbitraryReferenceTokenTransfer() = runBlocking {
        val senderPubkey = testKeyPair(0x22)
        val senderHex = KbKcc20ScriptCodec.bytesToHex(senderPubkey)
        val recipientPubkey = testKeyPair(0x33)
        val arbitraryCovenant = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
        val txid = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        val expectedRefProgram = Kcc20ReferenceProtocol.programFor(
            state = Kcc20ReferenceProtocol.CellState(
                templateHashHex = Kcc20ReferenceProtocol.REF_TEMPLATE_HASH,
                amount = BigInteger.valueOf(500L),
                owner = senderPubkey
            ),
            pinnedProgram = Kcc20ReferenceProtocol.pinnedRefProgram!!
        )
        val derivedAddr = Kcc20ReferenceProtocol.p2shAddress(expectedRefProgram, "kaspatest")

        // Indexer returns ONLY state fields (no raw program / redeemScriptHex)
        val cellJson = JSONObject()
            .put("txid", txid)
            .put("index", 0)
            .put("owner", senderHex)
            .put("amount", "500")
            .put("template", Kcc20ReferenceProtocol.REF_TEMPLATE_HASH)
            .put("address", derivedAddr)

        val candidatesHex = Kcc20KasivoService.extractCandidateProgramHexesFromCellJson(
            cellJson = cellJson,
            ownerPubKeyHex = senderHex,
            networkPrefix = "kaspatest",
            txid = txid,
            vout = 0
        )
        assertEquals(1, candidatesHex.size)

        val verified = Kcc20ReferenceProtocol.verifyCell(
            coin = Kcc20ReferenceProtocol.CandidateCell(
                redeemScriptHex = candidatesHex.first(),
                address = derivedAddr,
                txid = txid,
                vout = 0,
                kasSompi = 20_000_000L
            ),
            expectedCovenantId = arbitraryCovenant,
            networkPrefix = "kaspatest",
            utxosAt = { addr ->
                if (addr == derivedAddr) {
                    listOf(Kcc20ReferenceProtocol.NodeUtxo(txid, 0, 20_000_000L, arbitraryCovenant))
                } else emptyList()
            }
        )
        assertTrue(verified.ok)
        assertEquals(Kcc20ReferenceProtocol.TemplateKind.REFERENCE, verified.templateKind)
        assertEquals(arbitraryCovenant, verified.covenantId)

        // Plan arbitrary covenant transfer from this verified cell
        val piece = KbKcc20StandardAssembler.SpendPiece(
            transactionId = txid,
            index = 0,
            valueSompi = verified.kasSompi,
            redeem = verified.program
        )
        val plan = KbKcc20StandardAssembler.planStandardTransfer(
            pieces = listOf(piece),
            pubkey32 = senderPubkey,
            recipientOwner = recipientPubkey,
            amountBaseUnits = BigInteger.valueOf(200L),
            covenantId = arbitraryCovenant
        )
        assertEquals(arbitraryCovenant, plan.covenantId)
        assertEquals(2, plan.nextStates.size)
        assertEquals(BigInteger.valueOf(200L), plan.nextStates[0].amountBaseUnits)
        assertEquals(BigInteger.valueOf(300L), plan.nextStates[1].amountBaseUnits)
    }

    @Test
    fun uiAmountValidationHandlesZeroAndArbitraryDecimals() {
        // 0-decimal token (e.g. KasSeal Reference KCC-20)
        val (fracParsed, fracErr) = validateKcc20SendAmount("1.5", "100", 0)
        assertNull(fracParsed)
        assertEquals(Kcc20AmountValidationError.TOO_MANY_DECIMALS, fracErr)

        val (wholeParsed, wholeErr) = validateKcc20SendAmount("25", "100", 0)
        assertEquals(BigDecimal("25"), wholeParsed)
        assertEquals(Kcc20AmountValidationError.NONE, wholeErr)

        // 6-decimal token
        assertEquals("12.5", formatKcc20BalanceDisplay("12500000", 6))
        val (overParsed, overErr) = validateKcc20SendAmount("13.0", "12500000", 6)
        assertNull(overParsed)
        assertEquals(Kcc20AmountValidationError.INSUFFICIENT_BALANCE, overErr)
    }
}

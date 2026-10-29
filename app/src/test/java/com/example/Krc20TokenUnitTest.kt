package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.Krc20TokenBalance
import com.example.model.Krc20TokenMetadata
import com.example.model.Krc20TokenTransaction
import com.example.network.kaspa.Krc20Service
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Krc20TokenUnitTest {

    @Test
    fun testTokenMetadataJsonGeneration() {
        val meta = Krc20TokenMetadata(
            tick = "NACHO",
            tokenId = "85359b7768ba60cde4559b6e03ef2685d27ca7be3b1fa2cda976743e17f7c3ad",
            protocol = "krc-20",
            maxSupply = "28700000000000000000",
            mintedSupply = "28700000000000000000",
            decimals = 8,
            limitPerMint = "2870000000000",
            deployer = "kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7",
            state = "finished"
        )

        val jsonStr = meta.formattedJson()
        assertNotNull(jsonStr)
        val json = JSONObject(jsonStr)

        assertEquals("krc-20", json.getString("p"))
        assertEquals("deploy", json.getString("op"))
        assertEquals("NACHO", json.getString("tick"))
        assertEquals("85359b7768ba60cde4559b6e03ef2685d27ca7be3b1fa2cda976743e17f7c3ad", json.getString("tokenId"))
        assertEquals(8, json.getInt("dec"))
        assertEquals("finished", json.getString("state"))
        assertEquals("100.00%", json.getString("bondingCurveProgress"))
    }

    @Test
    fun testBondingCurveCalculation() {
        val bondingToken = Krc20TokenMetadata(
            tick = "KASPUMP",
            tokenId = "7a4d5e89b2c3d1f0e4a7b9c6d3e1f5a8b4c7d0e3f6a9b2c5d8e1f4a7b0c3d6e9",
            protocol = "krc-20",
            maxSupply = "100000000000000000",
            mintedSupply = "73400000000000000",
            decimals = 8,
            state = "deployed"
        )

        assertTrue(bondingToken.isBondingCurve)
        assertEquals(73.4, bondingToken.bondingCurveProgress, 0.1)

        val balance = Krc20TokenBalance(
            tick = "KASPUMP",
            balance = 50000.0,
            metadata = bondingToken
        )
        assertTrue(balance.isBondingCurve)
        assertEquals(73.4, balance.bondingCurveProgress, 0.1)
    }

    @Test
    fun testTokenTransactionJsonGeneration() {
        val tx = Krc20TokenTransaction(
            txId = "4a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef",
            tokenId = "85359b7768ba60cde4559b6e03ef2685d27ca7be3b1fa2cda976743e17f7c3ad",
            tick = "NACHO",
            op = "SWAP",
            amount = 1500.0,
            amountRaw = "150000000000",
            fromAddress = "kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7",
            toAddress = "kaspa:qp8z4685qf4rvd853eamr3v4g4k0846vms589p7y4k6j9k9nk7",
            status = "ACCEPTED",
            feeKas = 0.0001
        )

        val jsonStr = tx.formattedJson()
        assertNotNull(jsonStr)
        val json = JSONObject(jsonStr)

        assertEquals("4a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef", json.getString("txId"))
        assertEquals("85359b7768ba60cde4559b6e03ef2685d27ca7be3b1fa2cda976743e17f7c3ad", json.getString("tokenId"))
        assertEquals("krc-20", json.getString("p"))
        assertEquals("swap", json.getString("op"))
        assertEquals("NACHO", json.getString("tick"))
        assertEquals("ACCEPTED", json.getString("status"))
    }

    @Test
    fun testTokenPinningAndStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = Krc20Service(context)

        // Default pinned tokens are empty
        val defaults = service.getPinnedTickers()
        assertTrue(defaults.isEmpty())

        // Pin bonding curve token KASPUMP
        service.setPinned("KASPUMP", true)
        val afterPin = service.getPinnedTickers()
        assertTrue(afterPin.contains("KASPUMP"))

        // Unpin KASPUMP
        service.setPinned("KASPUMP", false)
        val afterUnpin = service.getPinnedTickers()
        assertFalse(afterUnpin.contains("KASPUMP"))
    }

    @Test
    fun testRecordCustomTransaction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = Krc20Service(context)

        val tx = Krc20TokenTransaction(
            txId = "tx_test_123456",
            tokenId = "85359b7768ba60cde4559b6e03ef2685d27ca7be3b1fa2cda976743e17f7c3ad",
            tick = "NACHO",
            op = "MINT (BONDING CURVE)",
            amount = 5000.0,
            amountRaw = "500000000000",
            status = "ACCEPTED"
        )

        service.recordTransaction(tx)
        val history = service.getLocalTransactions("NACHO")
        assertTrue(history.any { it.txId == "tx_test_123456" && it.op == "MINT (BONDING CURVE)" && it.amount == 5000.0 })
    }
}

package com.example

import com.example.network.kaspa.KaspaProtocolType
import com.example.network.kaspa.Kcc20KasivoService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Kcc20KasivoServiceUnitTest {

    @Test
    fun testKaspaProtocolTypeParsing() {
        // L1 Kaspa Native
        val l1Json = "{\"txId\":\"0x123\",\"amountKas\":10.5}"
        assertEquals(KaspaProtocolType.L1_KASPA, KaspaProtocolType.parseFromJson(l1Json))

        // L2 KCC-20 Covenant Token
        val kcc20Json = "{\"p\":\"kcc-20\",\"contractId\":\"kcc20_kb_0x123\",\"op\":\"transfer\"}"
        assertEquals(KaspaProtocolType.KCC20_COVENANT, KaspaProtocolType.parseFromJson(kcc20Json))

        // L2 KRC-20 Inscription Token
        val krc20Json = "{\"p\":\"krc-20\",\"op\":\"transfer\",\"tick\":\"NACHO\",\"amt\":\"100000000\"}"
        assertEquals(KaspaProtocolType.KRC20_INSCRIPTION, KaspaProtocolType.parseFromJson(krc20Json))
    }

    @Test
    fun testUnknownKcc20TokenFailsClosedWhenNoIndexerIsConfigured() = runBlocking {
        val service = Kcc20KasivoService(endpointBases = emptyList())
        val result = service.fetchKcc20Balance(
            "kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7",
            "KASDEX"
        )

        assertTrue(result.isFailure)
        assertEquals(
            "KCC-20 balance is unavailable from the configured indexers",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun testKcc20HistoryFetch() = runBlocking {
        val service = Kcc20KasivoService()
        val result = service.fetchKcc20History("kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7", "KASDEX")

        assertTrue(result.isSuccess)
        val items = result.getOrNull()
        assertNotNull(items)
    }

    @Test
    fun testProtocolTaxonomySummary() = runBlocking {
        val service = Kcc20KasivoService()
        val summary = service.fetchProtocolTaxonomySummary("kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7", 500.0)

        assertEquals("kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7", summary.address)
        assertEquals(500.0, summary.l1KasBalance, 0.001)
        assertEquals(0, summary.krc20TokenBalancesCount)
    }

    @Test
    fun testFetchWalletTokenKbPrice() = runBlocking {
        val service = Kcc20KasivoService()
        val dummyPubkey = "e7f7369478ebdbfadbb8e802af6cbf4ee1ee5ce43c0c0b6d3df115ddb1b96037"
        val kbContractId = "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8"
        val res = service.fetchWalletToken(dummyPubkey, kbContractId, "mainnet")
        println("fetchWalletToken result: " + res)
        assertTrue(res.isSuccess)
        val token = res.getOrNull()
        assertNotNull(token)
        println("marketPriceKas: " + token?.marketPriceKas)
        println("marketPriceUsd: " + token?.marketPriceUsd)
        println("imageUrl: " + token?.imageUrl)
        println("priceChange24h: " + token?.priceChange24hPercent)
        assertNotNull("marketPriceKas should not be null", token?.marketPriceKas)
        assertNotNull("imageUrl should not be null", token?.imageUrl)
        assertTrue("imageUrl should be valid URL", token?.imageUrl?.startsWith("https://") == true)
        assertNotNull("priceChange24hPercent should not be null", token?.priceChange24hPercent)

        val oracleKasPrice = service.fetchKasUsdPriceFromOracle()
        assertTrue(oracleKasPrice.isSuccess)
        assertTrue((oracleKasPrice.getOrNull() ?: 0.0) > 0.0)
    }
}

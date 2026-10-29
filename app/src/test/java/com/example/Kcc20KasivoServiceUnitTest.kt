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
    fun testKcc20BalanceFetchKnownToken() = runBlocking {
        val service = Kcc20KasivoService()
        val result = service.fetchKcc20Balance("kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7", "KASDEX")

        assertTrue(result.isSuccess)
        val balanceItem = result.getOrNull()
        assertNotNull(balanceItem)
        assertEquals("KASDEX", balanceItem?.ticker)
        assertEquals(KaspaProtocolType.KCC20_COVENANT, balanceItem?.protocolType)
        assertTrue(balanceItem?.formattedBalance()?.contains("KASDEX") == true)
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
}

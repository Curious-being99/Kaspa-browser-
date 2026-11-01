package com.example.network.kaspa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Kcc20CellOwnerMatchTest {
    private val service = Kcc20KasivoService(endpointBases = emptyList())
    private val key = "e7f7369478ebdbfadbb8e802af6cbf4ee1ee5ce43c0c0b6d3df115ddb1b96037"

    @Test
    fun matchesExactXOnlyPublicKey() {
        assertTrue(Kcc20KasivoService.cellOwnerMatchesWallet(key, "", "00", key, null))
    }

    @Test
    fun matchesExactWalletAddressWhenIndexerProvidesIt() {
        assertTrue(
            Kcc20KasivoService.cellOwnerMatchesWallet(
                "", "kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7",
                "00", key,
                "kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7"
            )
        )
    }

    @Test
    fun rejectsUnverifiedP2pkhOwnerDerivation() {
        assertFalse(Kcc20KasivoService.cellOwnerMatchesWallet(
            "p2pkh:" + "ab".repeat(32), "", "01", key, null
        ))
    }

    @Test
    fun rejectsDifferentOwner() {
        assertFalse(Kcc20KasivoService.cellOwnerMatchesWallet(
            "00".repeat(32), "", "00", key, null
        ))
    }
}

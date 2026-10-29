package com.example.network.kaspa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BraidSwapClientTest {
    @Test
    fun mainnetRegistryContainsOnlyPinnedMainnetTokens() {
        val symbols = BraidSwapClient.supportedTokens(BraidSwapClient.Network.MAINNET).map { it.symbol }.toSet()
        assertTrue(symbols.containsAll(setOf("THEN", "KRON", "PEPE", "IFWEN", "ANSEM", "GUZA", "KASCOV")))
        assertFalse(symbols.contains("ICE"))
        assertFalse(symbols.contains("KB"))
    }

    @Test
    fun testnetRegistryContainsOnlyPinnedTestnetTokens() {
        val symbols = BraidSwapClient.supportedTokens(BraidSwapClient.Network.TN10).map { it.symbol }.toSet()
        assertTrue(symbols.containsAll(setOf("ICE", "ROCKET", "MKRON", "MKPEPE", "FIRE", "KAHA20", "KRKT20", "KZEN20")))
        assertFalse(symbols.contains("THEN"))
        assertFalse(symbols.contains("KB"))
    }

    @Test
    fun kbTokenIsNotDeclaredBraidCompatible() {
        assertFalse(BraidSwapClient.isSupported(BraidSwapClient.Network.MAINNET, "KB"))
        assertFalse(BraidSwapClient.isSupported(BraidSwapClient.Network.MAINNET, "\\$KB"))
        assertFalse(BraidSwapClient.isSupported(BraidSwapClient.Network.TN10, "KB"))
    }

    @Test
    fun supportLookupIsCaseInsensitive() {
        assertTrue(BraidSwapClient.isSupported(BraidSwapClient.Network.MAINNET, "then"))
        assertTrue(BraidSwapClient.isSupported(BraidSwapClient.Network.TN10, "rocket"))
    }

    @Test
    fun routerPinsAreExplicitPerNetwork() {
        assertEquals(
            "f994470f9bd84d2ca2578a5d8294cc403bb699f19f7e73922c51d04cbb17027d",
            BraidSwapClient.Network.MAINNET.currentRouterPin
        )
        assertEquals(
            "905df466072298313e1cc9e86132ba61f26e2a359f7a7dbd5028fa1963872646",
            BraidSwapClient.Network.TN10.currentRouterPin
        )
    }
}

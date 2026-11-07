package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SiAgentResponseQualityTest {
    @Test
    fun removesImmediatelyRepeatedWordsWithoutChangingMeaning() {
        assertEquals(
            "Kaspa Browser is fast and secure.",
            SiAgentResponseQuality.clean("Kaspa Kaspa Browser is is fast and secure.")
        )
    }

    @Test
    fun removesRepeatedAdjacentPhrasesAndSentences() {
        assertEquals(
            "The wallet is on Testnet-10. Check the selected network.",
            SiAgentResponseQuality.clean(
                "The wallet is on Testnet-10. The wallet is on Testnet-10. Check the selected network. Check the selected network."
            )
        )
    }

    @Test
    fun removesAdjacentDuplicateLinesButPreservesNonAdjacentRepetition() {
        assertEquals(
            "Sources:\n- https://example.com\nConclusion:\nSources:",
            SiAgentResponseQuality.clean(
                "Sources:\n- https://example.com\n- https://example.com\nConclusion:\nSources:"
            )
        )
    }

    @Test
    fun preservesIntentionalRepeatedWordsThatAreNotAdjacent() {
        assertEquals(
            "Very important: check the network, then check the balance.",
            SiAgentResponseQuality.clean(
                "Very important: check the network, then check the balance."
            )
        )
    }
}

package com.example.network.siagent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiAgentMemoryStoreTest {
    @Test
    fun promptContextKeepsRecentConversationOrderAndSpeakerRoles() {
        val context = SiAgentMemoryStore.promptContext(
            listOf(
                SiAgentMemoryStore.Turn("I use Kaspa Testnet-10", true),
                SiAgentMemoryStore.Turn("Got it. I will keep the selected network separate.", false),
                SiAgentMemoryStore.Turn("What about my previous question?", true)
            )
        )

        assertTrue(context.indexOf("User: I use Kaspa Testnet-10") < context.indexOf("SI Agent: Got it"))
        assertTrue(context.contains("User: What about my previous question?"))
        assertTrue(context.contains("resolve follow-ups"))
    }

    @Test
    fun promptContextLimitsHistoryToMostRecentTurns() {
        val turns = (1..20).map { SiAgentMemoryStore.Turn("turn-$it", it % 2 == 0) }
        val context = SiAgentMemoryStore.promptContext(turns, maxTurns = 4)

        // Match the complete labelled turn: a substring check for "turn-1" also
        // matches retained turns 10–19 and incorrectly reports that turn 1 remains.
        assertFalse(context.lineSequence().any { it == "SI Agent: turn-1" || it == "User: turn-1" })
        assertTrue(context.contains("turn-17"))
        assertTrue(context.contains("turn-20"))
    }
}

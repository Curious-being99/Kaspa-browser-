package com.example.network.siagent

import org.junit.Assert.assertEquals
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

        val retainedTurns = context.lineSequence()
            .filter { it.startsWith("User: turn-") || it.startsWith("SI Agent: turn-") }
            .toList()

        assertEquals(
            listOf("SI Agent: turn-17", "User: turn-18", "SI Agent: turn-19", "User: turn-20"),
            retainedTurns
        )
    }
}

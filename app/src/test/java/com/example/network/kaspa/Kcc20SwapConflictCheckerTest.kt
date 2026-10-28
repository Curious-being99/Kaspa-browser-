package com.example.network.kaspa

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Kcc20SwapConflictCheckerTest {
    private val input = Kcc20SwapInput("abc123", 2)

    @Test
    fun acceptedSpendOfInputIsConflict() = runBlocking {
        val result = Kcc20SwapConflictChecker.check("kaspa:qaddress", listOf(input)) { _, _, _ ->
            Kcc20SwapHistoryPage(
                transactions = listOf(Kcc20HistoryTransaction("spender", setOf("ABC123:2"), true)),
                nextOffset = null,
                complete = true
            )
        }
        assertEquals(Kcc20SwapConflictResult.Conflict("spender"), result)
    }

    @Test
    fun unacceptedSpendDoesNotCountAsConfirmedConflict() = runBlocking {
        val result = Kcc20SwapConflictChecker.check("kaspa:qaddress", listOf(input)) { _, _, _ ->
            Kcc20SwapHistoryPage(
                transactions = listOf(Kcc20HistoryTransaction("unaccepted", setOf("abc123:2"), false)),
                nextOffset = null,
                complete = true
            )
        }
        assertEquals(Kcc20SwapConflictResult.NoConflict, result)
    }

    @Test
    fun incompleteHistoryFailsClosed() = runBlocking {
        val result = Kcc20SwapConflictChecker.check("kaspa:qaddress", listOf(input)) { _, offset, _ ->
            if (offset == 0) {
                Kcc20SwapHistoryPage(emptyList(), nextOffset = 50, complete = false)
            } else {
                Kcc20SwapHistoryPage(emptyList(), nextOffset = null, complete = false)
            }
        }
        assertTrue(result is Kcc20SwapConflictResult.Unknown)
    }

    @Test
    fun historyTransportFailureFailsClosed() = runBlocking {
        val result = Kcc20SwapConflictChecker.check("kaspa:qaddress", listOf(input)) { _, _, _ ->
            error("network unavailable")
        }
        assertTrue(result is Kcc20SwapConflictResult.Unknown)
    }

    @Test
    fun nonAdvancingPaginationFailsClosed() = runBlocking {
        val result = Kcc20SwapConflictChecker.check("kaspa:qaddress", listOf(input)) { _, _, _ ->
            Kcc20SwapHistoryPage(emptyList(), nextOffset = 0, complete = false)
        }
        assertTrue(result is Kcc20SwapConflictResult.Unknown)
    }

    @Test
    fun missingInputsFailsClosedWithoutQueryingHistory() = runBlocking {
        var queried = false
        val result = Kcc20SwapConflictChecker.check("kaspa:qaddress", emptyList()) { _, _, _ ->
            queried = true
            Kcc20SwapHistoryPage(emptyList(), null, true)
        }
        assertTrue(result is Kcc20SwapConflictResult.Unknown)
        assertEquals(false, queried)
    }
}

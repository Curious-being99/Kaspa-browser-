package com.example.network.kaspa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KbKcc20TransferGateTest {

    @Test
    fun unpublishedArtifactBlocksSigning() {
        val gate = KbKcc20TransferBuilder.gateStatus()
        assertTrue(gate is TransferGate.Blocked)
        assertFalse(KbKcc20TransferBuilder.isSigningAllowed())
    }

    @Test
    fun publishedAndConformantStillBlocksWithoutAssembler() {
        val config = KbKcc20Protocol.reviewedConfig.copy(
            artifactStatus = "published",
            conformant = true
        )
        val gate = KbKcc20TransferBuilder.gateStatus(config)
        assertTrue(gate is TransferGate.Blocked)
        assertTrue((gate as TransferGate.Blocked).reason.contains("assembler"))
    }

    @Test
    fun mismatchedPinnedCovenantBlocksSigning() {
        val config = KbKcc20Protocol.reviewedConfig.copy(
            covenantId = "0".repeat(64),
            artifactStatus = "published",
            conformant = true
        )
        val gate = KbKcc20TransferBuilder.gateStatus(config)
        assertTrue(gate is TransferGate.Blocked)
        assertTrue((gate as TransferGate.Blocked).reason.contains("pinned covenant"))
    }

    @Test
    fun signableTransactionBuilderRejectsWhileBlocked() {
        var assemblerCalled = false
        val result = runCatching {
            KbKcc20TransferBuilder.buildSignableTransaction {
                assemblerCalled = true
                "signable"
            }
        }
        assertTrue(result.isFailure)
        assertFalse(assemblerCalled)
    }
}

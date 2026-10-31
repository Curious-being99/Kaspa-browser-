package com.example.network.kaspa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KbKcc20DeploymentConformanceTest {
    private fun verified(
        tokenStatus: String = "verified",
        layout: String = KbKcc20Protocol.STATE_LAYOUT,
        specCommit: String = "1ed87ae",
        review: String = "reviewed",
        conformant: Boolean? = true,
        artifact: String? = null,
        templateHash: String = KbKcc20Protocol.TEMPLATE_HASH,
        viewTemplateHash: String = KbKcc20Protocol.VIEW_TEMPLATE_HASH,
        dispatch: String = "custom-tags",
        ownerSchemes: Set<String> = setOf("p2pk-schnorr/v1"),
        nonconformities: List<String> = emptyList()
    ) = Kcc20KasivoService.DeploymentConformance(
        tokenStatus = tokenStatus,
        layout = layout,
        kcc = 20,
        specCommit = specCommit,
        review = review,
        conformant = conformant,
        artifact = artifact,
        templateHash = templateHash,
        viewTemplateHash = viewTemplateHash,
        dispatch = dispatch,
        ownerSchemes = ownerSchemes,
        nonconformities = nonconformities
    )

    @Test
    fun acceptsOnlyThePinnedReviewedConformantBuildAndVerifiedToken() {
        assertTrue(verified().safeForStandardTransfer)
    }

    @Test
    fun rejectsUnvalidatedTokenOrUnreviewedBuild() {
        assertFalse(verified(tokenStatus = "unvalidated").safeForStandardTransfer)
        assertFalse(verified(review = "unreviewed").safeForStandardTransfer)
        assertFalse(verified(specCommit = "ea5176aa").safeForStandardTransfer)
        assertFalse(verified(conformant = null).safeForStandardTransfer)
        assertFalse(verified(conformant = false).safeForStandardTransfer)
    }

    @Test
    fun rejectsArtifactOrTemplateMismatch() {
        assertFalse(verified(artifact = "unpublished").safeForStandardTransfer)
        assertFalse(verified(templateHash = "00".repeat(32)).safeForStandardTransfer)
        assertFalse(verified(viewTemplateHash = "00".repeat(32)).safeForStandardTransfer)
        assertFalse(verified(layout = "kcc20-46").safeForStandardTransfer)
    }

    @Test
    fun rejectsUnknownDispatchAndReportedNonconformities() {
        assertFalse(verified(dispatch = "unknown").safeForStandardTransfer)
        assertFalse(verified(ownerSchemes = setOf("p2pkh-schnorr/v1")).safeForStandardTransfer)
        assertFalse(verified(nonconformities = listOf("wrong hash-chain link")).safeForStandardTransfer)
    }
}

package com.example.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaspaWalletExtensionSupportTest {
    private fun entry(
        packageFormat: ExtensionPackageFormat = ExtensionPackageFormat.SIGNED_XPI,
        install: Boolean = true,
        dapp: Boolean = true,
        approval: Boolean = true,
        routing: Boolean = true,
        version: String? = "1.0"
    ) = WalletExtensionCompatibility(
        displayName = "Test Kaspa wallet",
        engine = ExtensionEngine.GECKOVIEW,
        packageFormat = packageFormat,
        testedVersion = version,
        installVerified = install,
        dappConnectionVerified = dapp,
        transactionApprovalVerified = approval,
        privacyRoutingVerified = routing
    )

    @Test fun unknownWalletIsNotTested() {
        assertEquals(SupportStatus.NOT_TESTED, KaspaWalletExtensionSupport.status(null))
    }

    @Test fun unverifiedPackageIsNeverSupported() {
        assertEquals(
            SupportStatus.UNSUPPORTED_PACKAGE,
            KaspaWalletExtensionSupport.status(entry(packageFormat = ExtensionPackageFormat.UNVERIFIED))
        )
    }

    @Test fun incompleteSecurityValidationIsNotSupported() {
        assertEquals(
            SupportStatus.INCOMPLETE,
            KaspaWalletExtensionSupport.status(entry(routing = false))
        )
    }

    @Test fun allRequiredChecksMustPass() {
        assertTrue(entry().isSupported)
        assertFalse(entry(version = null).isSupported)
        assertFalse(entry(approval = false).isSupported)
    }
}

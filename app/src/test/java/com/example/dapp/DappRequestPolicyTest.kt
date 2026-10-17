package com.example.dapp

import org.junit.Assert.assertEquals
import org.junit.Test

class DappRequestPolicyTest {
    private fun request(
        pageUrl: String = "https://market.example/item/1",
        frameUrl: String = pageUrl,
        method: DappWalletMethod = DappWalletMethod.CONNECT,
        granted: Boolean = false,
        activeNetwork: String = "kaspa-mainnet",
        requestedNetwork: String? = null,
        payload: Boolean = false
    ) = DappWalletRequest(
        pageUrl = pageUrl,
        frameUrl = frameUrl,
        method = method,
        connectionGranted = granted,
        activeNetwork = activeNetwork,
        requestedNetwork = requestedNetwork,
        hasTransactionPayload = payload
    )

    @Test
    fun connectionRequiresPermissionWhenNotGranted() {
        assertEquals(
            DappPolicyDecision.PROMPT_FOR_CONNECTION,
            DappRequestPolicy.evaluate(request())
        )
    }

    @Test
    fun approvedAccountReadIsReadOnly() {
        assertEquals(
            DappPolicyDecision.ALLOW_READ_ONLY,
            DappRequestPolicy.evaluate(request(method = DappWalletMethod.GET_ACCOUNTS, granted = true))
        )
    }

    @Test
    fun signingAlwaysRequiresApproval() {
        assertEquals(
            DappPolicyDecision.REQUIRE_USER_APPROVAL,
            DappRequestPolicy.evaluate(
                request(method = DappWalletMethod.SIGN_TRANSACTION, granted = true, payload = true)
            )
        )
    }

    @Test
    fun transactionWithoutPayloadIsDenied() {
        assertEquals(
            DappPolicyDecision.DENY,
            DappRequestPolicy.evaluate(request(method = DappWalletMethod.SUBMIT_TRANSACTION, granted = true))
        )
    }

    @Test
    fun networkMismatchCannotBeSignedSilently() {
        assertEquals(
            DappPolicyDecision.DENY,
            DappRequestPolicy.evaluate(
                request(
                    method = DappWalletMethod.SIGN_TRANSACTION,
                    granted = true,
                    requestedNetwork = "kasplex-mainnet",
                    payload = true
                )
            )
        )
    }

    @Test
    fun networkSwitchRequiresApproval() {
        assertEquals(
            DappPolicyDecision.REQUIRE_USER_APPROVAL,
            DappRequestPolicy.evaluate(
                request(
                    method = DappWalletMethod.SWITCH_NETWORK,
                    granted = true,
                    requestedNetwork = "igra-mainnet"
                )
            )
        )
    }

    @Test
    fun insecureHttpOriginIsDenied() {
        assertEquals(
            DappPolicyDecision.DENY,
            DappRequestPolicy.evaluate(request(pageUrl = "http://market.example", frameUrl = "http://market.example"))
        )
    }

    @Test
    fun credentialsInUrlAreDenied() {
        assertEquals(
            DappPolicyDecision.DENY,
            DappRequestPolicy.evaluate(
                request(
                    pageUrl = "https://user:pass@market.example/item",
                    frameUrl = "https://user:pass@market.example/item"
                )
            )
        )
    }

    @Test
    fun crossOriginFrameIsDenied() {
        assertEquals(
            DappPolicyDecision.DENY,
            DappRequestPolicy.evaluate(
                request(frameUrl = "https://untrusted.example/frame", granted = true)
            )
        )
    }

    @Test
    fun equivalentDefaultHttpsPortsMatch() {
        assertEquals(
            DappPolicyDecision.PROMPT_FOR_CONNECTION,
            DappRequestPolicy.evaluate(
                request(
                    pageUrl = "https://market.example:443/item",
                    frameUrl = "https://market.example/another"
                )
            )
        )
    }

    @Test
    fun unknownMethodIsDenied() {
        assertEquals(
            DappPolicyDecision.DENY,
            DappRequestPolicy.evaluate(request(method = DappWalletMethod.UNKNOWN, granted = true))
        )
    }
}

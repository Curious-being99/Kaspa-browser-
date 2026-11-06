package com.example.network.siagent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SiAgentToolRuntimeTest {

    @Test
    fun exposesLiveReadOnlyAndBoundedBrowserTools() {
        val tools = SiAgentToolRuntime.definitions()
        val names = (0 until tools.length()).mapNotNull { index ->
            tools.optJSONObject(index)?.optJSONObject("function")?.optString("name")
        }.toSet()

        assertTrue(names.contains("prepare_kaspa_payment"))
        assertTrue(names.contains("get_wallet_status"))
        assertTrue(names.contains("get_wallet_transactions"))
        assertTrue(names.contains("get_receive_address"))
        assertTrue(names.contains("get_kcc20_tokens"))
        assertTrue(names.contains("get_krc20_tokens"))
        assertTrue(names.contains("get_krc20_transactions"))
        assertTrue(names.contains("get_kcc20_transactions"))
        assertTrue(names.contains("get_browser_state"))
        assertTrue(names.contains("get_dapp_state"))
        assertTrue(names.contains("search_public_web"))
        assertTrue(names.contains("crawl_public_website"))
        assertTrue(names.contains("browser_navigate"))
        assertTrue(names.contains("browser_open_tab"))
        assertTrue(names.contains("browser_switch_tab"))
        assertTrue(names.contains("browser_close_tab"))
        assertTrue(names.contains("browser_bookmark_current_page"))
        assertTrue(names.contains("browser_go_back"))
        assertTrue(names.contains("browser_go_forward"))
        assertTrue(names.contains("browser_reload"))
    }

    @Test
    fun resolvesKaspaL1NetworksWithoutConflatingTestnets() {
        val mainnet = SiAgentToolRuntime.resolveKaspaNetworkContext("Kaspa Mainnet")
        val tn10 = SiAgentToolRuntime.resolveKaspaNetworkContext("Kaspa Testnet 10")
        val tn11 = SiAgentToolRuntime.resolveKaspaNetworkContext("Kaspa Testnet 11")

        assertTrue(mainnet?.networkId == "mainnet")
        assertTrue(mainnet?.addressPrefix == "kaspa")
        assertTrue(tn10?.networkId == "testnet-10")
        assertTrue(tn11?.networkId == "testnet-11")
        assertTrue(tn10?.addressPrefix == "kaspatest")
        assertTrue(tn11?.addressPrefix == "kaspatest")
        assertTrue(tn10?.rpcEndpoint == "https://api-tn10.kaspa.org")
        assertTrue(tn11?.rpcEndpoint == "https://api-tn11.kaspa.org")
        assertTrue(SiAgentToolRuntime.resolveKaspaNetworkContext("Kasplex Mainnet") == null)
        assertTrue(SiAgentToolRuntime.resolveKaspaNetworkContext("Igra Galleon Testnet") == null)
    }

    @Test
    fun exposesPaymentPreparationButNeverSigningOrBroadcastTools() {
        val tools = SiAgentToolRuntime.definitions()
        val names = (0 until tools.length()).mapNotNull { index ->
            tools.optJSONObject(index)?.optJSONObject("function")?.optString("name")
        }
        assertFalse(names.any { name ->
            val normalized = name.lowercase()
            normalized == "send" ||
                normalized.contains("sign") ||
                normalized.contains("broadcast") ||
                normalized.contains("approve") ||
                normalized.contains("private_key") ||
                normalized.contains("seed")
        })
    }

    @Test
    fun toolSchemasRejectUnexpectedArguments() {
        val tools = SiAgentToolRuntime.definitions()
        val payment = (0 until tools.length()).mapNotNull { tools.optJSONObject(it) }
            .firstOrNull { it.optJSONObject("function")?.optString("name") == "prepare_kaspa_payment" }
        val paymentParameters = payment?.optJSONObject("function")?.optJSONObject("parameters")
        assertTrue(paymentParameters?.optBoolean("additionalProperties", true) == false)
        assertTrue(paymentParameters?.optJSONArray("required")?.length() == 2)
        for (index in 0 until tools.length()) {
            val function = tools.optJSONObject(index)?.optJSONObject("function") ?: continue
            val parameters = function.optJSONObject("parameters") ?: continue
            assertFalse(parameters.optBoolean("additionalProperties", true))
        }
    }
}

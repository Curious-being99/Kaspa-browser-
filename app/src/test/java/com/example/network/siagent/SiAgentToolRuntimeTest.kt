package com.example.network.siagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiAgentToolRuntimeTest {

    @Test
    fun exposesLiveReadOnlyAndBoundedBrowserTools() {
        val tools = SiAgentToolRuntime.definitions()
        val names = (0 until tools.length()).mapNotNull { index ->
            tools.optJSONObject(index)?.optJSONObject("function")?.optString("name")
        }.toSet()

        assertTrue(names.contains("get_wallet_status"))
        assertTrue(names.contains("get_wallet_transactions"))
        assertTrue(names.contains("get_receive_address"))
        assertTrue(names.contains("get_kcc20_tokens"))
        assertTrue(names.contains("get_krc20_tokens"))
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
    }

    @Test
    fun neverExposesSigningOrBroadcastToolsToTheModel() {
        val tools = SiAgentToolRuntime.definitions()
        val names = (0 until tools.length()).mapNotNull { index ->
            tools.optJSONObject(index)?.optJSONObject("function")?.optString("name")
        }
        assertFalse(names.any { name ->
            val normalized = name.lowercase()
            normalized.contains("send") ||
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
        for (index in 0 until tools.length()) {
            val function = tools.optJSONObject(index)?.optJSONObject("function") ?: continue
            val parameters = function.optJSONObject("parameters") ?: continue
            assertEquals(false, parameters.optBoolean("additionalProperties", true))
        }
    }
}

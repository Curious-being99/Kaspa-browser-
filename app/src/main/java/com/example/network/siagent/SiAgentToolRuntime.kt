package com.example.network.siagent

import com.example.network.siagent.AndroidWebResearch
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Executes SI Agent's narrowly-scoped app tools against live KaspaBrowser services.
 * Wallet details are returned only when the model explicitly invokes the matching tool.
 */
object SiAgentToolRuntime {
    private const val KB_CONTRACT = "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8"

    fun definitions(): JSONArray = JSONArray().apply {
        addTool("get_wallet_status", "Read the current selected network, KAS balance, USD estimate, and UTXO count. Call only when the user asks about their wallet or balance.")
        addTool("get_wallet_transactions", "Read recent native KAS wallet transactions. Counterparty addresses and signing material are never returned.")
        addTool("get_receive_address", "Read the receive address for the currently selected network. Call only when the user asks to receive KAS or requests their address.")
        addTool("get_kcc20_tokens", "Read live KCC-20 wallet token balances, metadata, validation and indexed prices. Includes $KB when its wallet record is available.")
        addTool("get_krc20_tokens", "Read the app's current KRC-20 pinned token balances and metadata. Keep KRC-20 distinct from KCC-20.")
        addTool("browser_bookmark_current_page", "Save the current webpage as a bookmark if it is not already bookmarked.")
        addTool("get_kcc20_transactions", "Read recent KCC-20 token transaction history.")
        addTool("get_browser_state", "Read current page, open tabs, bookmarks, recent browsing history and download status.")
        addTool("get_dapp_state", "Read installed PWAs and the current PWA context. This does not connect to or control a dApp wallet.")
        addTool("search_public_web", "Search the public web and retrieve readable pages using KaspaBrowser's safe web research service.", "query")
        addTool("crawl_public_website", "Crawl a public HTTPS website and retrieve readable pages. Private, local and non-public destinations are blocked.", "url")
        addTool("browser_navigate", "Navigate the active browser tab to a public HTTP or HTTPS URL. This changes browser state but never submits a form or performs a purchase.", "url")
        addTool("browser_open_tab", "Open a new browser tab at a public HTTP or HTTPS URL, or a blank home tab.", "url")
        addTool("browser_switch_tab", "Switch to an existing browser tab by its 1-based tab number.", "tab_number")
        addTool("browser_close_tab", "Close an existing browser tab by its 1-based tab number. Do not close the final tab.", "tab_number")
    }

    private fun JSONArray.addTool(name: String, description: String, required: String? = null) {
        val properties = JSONObject()
        when (required) {
            "query" -> properties.put("query", JSONObject().put("type", "string").put("description", "The public web search query"))
            "url" -> properties.put("url", JSONObject().put("type", "string").put("description", "An absolute public HTTP or HTTPS URL"))
            "tab_number" -> properties.put("tab_number", JSONObject().put("type", "integer").put("minimum", 1))
        }
        val parameters = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("additionalProperties", false)
        if (required != null) parameters.put("required", JSONArray().put(required))
        put(JSONObject().put("type", "function").put("function", JSONObject()
            .put("name", name).put("description", description).put("parameters", parameters)))
    }

    suspend fun execute(viewModel: DecentralViewModel, name: String, args: JSONObject): String {
        return try {
            when (name) {
                "get_wallet_status" -> {
                    val wallet = viewModel.kaspaWalletState.value
                    JSONObject()
                        .put("network", viewModel.selectedNetwork.value)
                        .put("balance_kas", wallet.balanceKas)
                        .put("balance_usd_estimate", wallet.balanceUsd)
                        .put("kas_usd_price", wallet.priceUsd)
                        .put("utxo_count", wallet.utxosCount)
                        .put("is_loading", wallet.isLoading)
                        .put("wallet_initialized", viewModel.activeAccount.value != null)
                        .toString()
                }
                "get_wallet_transactions" -> {
                    val txs = viewModel.kaspaWalletState.value.recentTransactions
                    JSONObject().put("transactions", JSONArray().apply {
                        txs.take(30).forEach { tx ->
                            put(JSONObject()
                                .put("tx_id", tx.txId)
                                .put("timestamp_ms", tx.blockTime)
                                .put("amount_kas", tx.amountKas)
                                .put("type", tx.type)
                                .put("accepted", tx.isAccepted)
                                .put("fee_kas", tx.feeKas)
                                .put("confirmation_count", tx.confirmationCount ?: JSONObject.NULL))
                        }
                    }).put("count", txs.size).toString()
                }
                "get_receive_address" -> {
                    val network = viewModel.selectedNetwork.value
                    val address = if (network.contains("Mainnet", true)) viewModel.mainnetAddress.value else viewModel.testnetAddress.value
                    if (address.isBlank()) errorJson("No receive address is available for the selected network.")
                    else JSONObject().put("network", network).put("receive_address", address).toString()
                }
                "get_kcc20_tokens" -> {
                    val tokens = viewModel.kcc20Tokens.value
                    JSONObject().put("network", viewModel.selectedNetwork.value)
                        .put("tokens", JSONArray().apply {
                            tokens.forEach { token ->
                                put(JSONObject()
                                    .put("contract_id", token.contractId)
                                    .put("ticker", token.ticker)
                                    .put("name", token.name)
                                    .put("balance", token.balanceFormatted)
                                    .put("raw_balance", token.balanceRaw)
                                    .put("decimals", token.decimals)
                                    .put("validation_status", token.validationStatus)
                                    .put("price_kas", token.marketPriceKas ?: JSONObject.NULL)
                                    .put("price_usd", token.marketPriceUsd ?: JSONObject.NULL)
                                    .put("price_source", token.marketPriceSource ?: JSONObject.NULL)
                                    .put("price_updated_at_ms", token.priceUpdatedAtMs ?: JSONObject.NULL)
                                    .put("change_24h_percent", token.priceChange24hPercent ?: JSONObject.NULL)
                                    .put("is_kb", token.contractId.equals(KB_CONTRACT, true)))
                            }
                        }).put("count", tokens.size).toString()
                }
                "get_krc20_tokens" -> {
                    val tokens = viewModel.pinnedTokens.value
                    JSONObject().put("protocol", "KRC-20")
                        .put("tokens", JSONArray().apply {
                            tokens.forEach { token ->
                                val metadata = token.metadata
                                put(JSONObject()
                                    .put("ticker", token.tick)
                                    .put("balance", token.balance)
                                    .put("raw_balance", token.balanceRaw)
                                    .put("decimals", token.decimals)
                                    .put("token_id", token.tokenId)
                                    .put("is_pinned", token.isPinned)
                                    .put("state", metadata?.state ?: JSONObject.NULL)
                                    .put("bonding_curve_progress_percent", metadata?.bondingCurveProgress ?: JSONObject.NULL)
                                    .put("holder_count", metadata?.holderTotal ?: JSONObject.NULL)
                                    .put("logo_url", token.effectiveLogoUrl ?: JSONObject.NULL))
                            }
                        }).put("count", tokens.size).toString()
                }
                "get_kcc20_transactions" -> {
                    val txs = viewModel.kcc20Transactions.value
                    JSONObject().put("transactions", JSONArray().apply {
                        txs.take(40).forEach { tx ->
                            put(JSONObject()
                                .put("tx_id", tx.txId)
                                .put("contract_id", tx.tokenContractId)
                                .put("ticker", tx.ticker)
                                .put("operation", tx.operation)
                                .put("amount", tx.amount)
                                .put("from", tx.fromAddress)
                                .put("to", tx.toAddress)
                                .put("timestamp_ms", tx.timestamp)
                                .put("status", tx.status)
                                .put("fee_kas", tx.feeKas))
                        }
                    }).put("count", txs.size).toString()
                }
                "get_browser_state" -> {
                    val tabs = viewModel.browserTabs.value
                    val activeId = viewModel.activeTabId.value
                    val resource = viewModel.currentResource.value
                    JSONObject()
                        .put("active_page", JSONObject().put("title", resource?.title ?: "").put("url", resource?.url ?: ""))
                        .put("tabs", JSONArray().apply {
                            tabs.forEachIndexed { index, tab ->
                                put(JSONObject().put("number", index + 1).put("title", tab.title)
                                    .put("url", tab.url).put("active", tab.id == activeId))
                            }
                        })
                        .put("bookmarks", JSONArray().apply {
                            viewModel.bookmarks.value.take(50).forEach { put(JSONObject().put("title", it.title).put("url", it.url)) }
                        })
                        .put("history", JSONArray().apply {
                            viewModel.history.value.take(30).forEach { put(JSONObject().put("title", it.title).put("url", it.url).put("timestamp_ms", it.timestamp)) }
                        })
                        .put("downloads", JSONArray().apply {
                            viewModel.activeDownloads.value.take(30).forEach { put(JSONObject().put("file_name", it.fileName).put("status", it.status).put("progress", it.progress)) }
                        }).toString()
                }
                "get_dapp_state" -> {
                    val current = viewModel.currentPagePwa.value
                    val installed = viewModel.installedPwas.value
                    JSONObject().put("current_pwa", current?.let {
                        JSONObject().put("name", it.name).put("url", it.url).put("manifest_url", it.manifestUrl ?: JSONObject.NULL)
                    } ?: JSONObject.NULL)
                        .put("installed_pwas", JSONArray().apply {
                            installed.forEach { pwa -> put(JSONObject().put("name", pwa.name).put("url", pwa.url).put("has_manifest", pwa.hasManifest)) }
                        }).put("note", "This reports browser PWA state only; it does not imply a wallet connection or dApp authorization.")
                        .toString()
                }
                "search_public_web" -> {
                    val query = args.optString("query").trim().take(240)
                    if (query.isBlank()) errorJson("A non-empty search query is required.")
                    else AndroidWebResearch.searchAndCrawl(query).toPromptContext()
                }
                "crawl_public_website" -> {
                    val url = args.optString("url").trim()
                    if (!isPublicWebUrl(url)) errorJson("Only absolute public HTTP or HTTPS URLs are accepted.")
                    else AndroidWebResearch.crawlUrl(url).toPromptContext()
                }
                "browser_bookmark_current_page" -> {
                    val page = viewModel.currentResource.value
                    val url = page?.url.orEmpty()
                    val title = page?.title.orEmpty()
                    if (!isPublicWebUrl(url)) errorJson("There is no public webpage available to bookmark.")
                    else {
                        val exists = viewModel.isBookmarked(url)
                        if (!exists) withContext(Dispatchers.Main) { viewModel.toggleBookmark(url, title) }
                        JSONObject().put("success", true).put("already_bookmarked", exists)
                            .put("title", title).put("url", url).toString()
                    }
                }
                "browser_navigate" -> {
                    val url = args.optString("url").trim()
                    if (!isPublicWebUrl(url)) errorJson("Only absolute public HTTP or HTTPS URLs are accepted.")
                    else {
withContext(Dispatchers.Main) { viewModel.openUrlInBrowser(url, isExternal = false) }
withContext(Dispatchers.Main) { viewModel.setTab(AppTab.BROWSER_GATEWAY) }
                        JSONObject().put("success", true).put("action", "navigate_active_tab").put("url", url).toString()
                    }
                }
                "browser_open_tab" -> {
                    val raw = args.optString("url").trim()
                    if (raw.isNotBlank() && !isPublicWebUrl(raw)) errorJson("Only absolute public HTTP or HTTPS URLs are accepted.")
                    else {
withContext(Dispatchers.Main) { viewModel.createNewTab(raw) }
withContext(Dispatchers.Main) { viewModel.setTab(AppTab.BROWSER_GATEWAY) }
                        JSONObject().put("success", true).put("action", "open_new_tab").put("url", raw).toString()
                    }
                }
                "browser_switch_tab" -> {
                    val index = args.optInt("tab_number", 0) - 1
                    val tab = viewModel.browserTabs.value.getOrNull(index)
                    if (tab == null) errorJson("That browser tab number does not exist.")
                    else {
withContext(Dispatchers.Main) { viewModel.setActiveTab(tab.id) }
withContext(Dispatchers.Main) { viewModel.setTab(AppTab.BROWSER_GATEWAY) }
                        JSONObject().put("success", true).put("action", "switch_tab").put("tab_number", index + 1).put("title", tab.title).toString()
                    }
                }
                "browser_close_tab" -> {
                    val tabs = viewModel.browserTabs.value
                    val index = args.optInt("tab_number", 0) - 1
                    val tab = tabs.getOrNull(index)
                    if (tab == null) errorJson("That browser tab number does not exist.")
                    else if (tabs.size <= 1) errorJson("The final browser tab cannot be closed by SI Agent.")
                    else {
withContext(Dispatchers.Main) { viewModel.closeTab(tab.id) }
                        JSONObject().put("success", true).put("action", "close_tab").put("tab_number", index + 1).put("title", tab.title).toString()
                    }
                }
                else -> errorJson("Unknown or unsupported SI Agent tool: $name")
            }
        } catch (error: Exception) {
            errorJson(error.message ?: "Tool execution failed.")
        }
    }

    private fun isPublicWebUrl(value: String): Boolean = try {
        val uri = URI(value)
        (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }

    private fun errorJson(message: String): String =
        JSONObject().put("success", false).put("error", message).toString()
}

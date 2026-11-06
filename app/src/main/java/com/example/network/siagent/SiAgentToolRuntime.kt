package com.example.network.siagent

import com.example.network.siagent.AndroidWebResearch
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Executes SI Agent's narrowly-scoped app tools against live KaspaBrowser services.
 * Wallet details are returned only when the model explicitly invokes the matching tool.
 */
object SiAgentToolRuntime {
    private const val KB_CONTRACT = "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8"

    internal data class KaspaNetworkContext(
        val name: String,
        val networkId: String,
        val addressPrefix: String,
        val rpcEndpoint: String
    )

    internal fun resolveKaspaNetworkContext(selectedNetwork: String): KaspaNetworkContext? = when {
        selectedNetwork.equals("Kaspa Mainnet", ignoreCase = true) ->
            KaspaNetworkContext("Kaspa Mainnet", "mainnet", "kaspa", "https://api.kaspa.org")
        selectedNetwork.equals("Kaspa Testnet 10", ignoreCase = true) ->
            KaspaNetworkContext("Kaspa Testnet 10", "testnet-10", "kaspatest", "https://api-tn10.kaspa.org")
        selectedNetwork.equals("Kaspa Testnet 11", ignoreCase = true) ->
            KaspaNetworkContext("Kaspa Testnet 11", "testnet-11", "kaspatest", "https://api-tn11.kaspa.org")
        else -> null
    }

    fun definitions(): JSONArray = JSONArray().apply {
        addPaymentTool()
        addTool("get_wallet_status", "Refresh and read the selected Kaspa L1 network (Mainnet, Testnet-10, or Testnet-11), its network identifier, RPC endpoint, KAS balance, USD estimate, and UTXO count. Never infer Testnet-10 versus Testnet-11 from the shared kaspatest: prefix; use the selected network.")
        addTool("get_wallet_transactions", "Read recent native KAS wallet transactions. Counterparty addresses and signing material are never returned.")
        addTool("get_receive_address", "Read the receive address for the currently selected network. Call only when the user asks to receive KAS or requests their address.")
        addTool("get_kcc20_tokens", "Refresh and read KCC-20 wallet token balances, metadata, validation and indexed prices. Includes \$KB when its wallet record is available.")
        addTool("get_krc20_tokens", "Refresh and read the app's current KRC-20 pinned token balances and metadata. Keep KRC-20 distinct from KCC-20.")
        addTool("get_krc20_transactions", "Read recent KRC-20 token transaction history from the app's pinned token records.")
        addTool("browser_bookmark_current_page", "Save the current webpage as a bookmark if it is not already bookmarked.")
        addTool("browser_go_back", "Go back one page in the active browser tab when history is available.")
        addTool("browser_go_forward", "Go forward one page in the active browser tab when history is available.")
        addTool("browser_reload", "Reload the active browser tab.")
        addTool("get_kcc20_transactions", "Read recent KCC-20 token transaction history.")
        addTool("get_browser_state", "Read current page, open tabs, bookmarks, recent browsing history and download status.")
        addTool("get_dapp_state", "Read installed PWAs and the current PWA context. This does not connect to or control a dApp wallet.")
        addTool("search_public_web", "Search the public web and retrieve readable pages using KaspaBrowser's safe web research service.", "query")
        addTool("crawl_public_website", "Crawl a public HTTPS website and retrieve readable pages. Private, local and non-public destinations are blocked.", "url")
        addTool("browser_navigate", "Navigate the active browser tab to a public HTTP or HTTPS URL. This changes browser state but never submits a form or performs a purchase.", "url")
        addTool("browser_open_tab", "Open a new browser tab at a public HTTP or HTTPS URL, or a blank home tab.", optional = "url")
        addTool("browser_switch_tab", "Switch to an existing browser tab by its 1-based tab number.", "tab_number")
        addTool("browser_close_tab", "Close an existing browser tab by its 1-based tab number. Do not close the final tab.", "tab_number")
    }

    private fun JSONArray.addPaymentTool() {
        val properties = JSONObject()
            .put("recipient_address", JSONObject().put("type", "string").put("description", "Full Kaspa bech32 recipient address for the currently selected Kaspa network."))
            .put("amount_kas", JSONObject().put("type", "number").put("exclusiveMinimum", 0))
        val parameters = JSONObject().put("type", "object").put("properties", properties)
            .put("required", JSONArray().put("recipient_address").put("amount_kas"))
            .put("additionalProperties", false)
        put(JSONObject().put("type", "function").put("function", JSONObject()
            .put("name", "prepare_kaspa_payment")
            .put("description", "Prepare a KAS payment only on the currently selected Kaspa L1 network (Mainnet, Testnet-10, or Testnet-11). If the user names a different network, do not prepare the payment; explain that they must select that network in the wallet first. Testnet-10 and Testnet-11 share the kaspatest: address prefix, so never infer which one from the address alone. This only opens the native review sheet; it never signs or broadcasts.")
            .put("parameters", parameters)))
    }

    private fun JSONArray.addTool(name: String, description: String, required: String? = null, optional: String? = null) {
        val properties = JSONObject()
        when (required ?: optional) {
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

    private suspend fun refreshWallet(viewModel: DecentralViewModel): Boolean {
        val network = resolveKaspaNetworkContext(viewModel.selectedNetwork.value) ?: return false
        val address = if (network.networkId == "mainnet") viewModel.mainnetAddress.value else viewModel.testnetAddress.value
        if (address.isBlank()) return false
        val alreadyLoading = viewModel.kaspaWalletState.value.isLoading
        if (!alreadyLoading) withContext(Dispatchers.Main) { viewModel.refreshKaspaWallet(address) }
        return withTimeoutOrNull(30_000L) {
            if (!alreadyLoading) viewModel.kaspaWalletState.first { it.isLoading }
            viewModel.kaspaWalletState.first { !it.isLoading }
            true
        } ?: false
    }

    private suspend fun refreshKcc20(viewModel: DecentralViewModel): Boolean {
        if (viewModel.activeAccount.value == null) return false
        val alreadyLoading = viewModel.isLoadingKcc20.value
        if (!alreadyLoading) withContext(Dispatchers.Main) { viewModel.refreshKcc20Tokens() }
        return withTimeoutOrNull(45_000L) {
            if (!alreadyLoading) viewModel.isLoadingKcc20.first { it }
            viewModel.isLoadingKcc20.first { !it }
            true
        } ?: false
    }

    private suspend fun refreshKrc20(viewModel: DecentralViewModel): Boolean {
        if (viewModel.activeAccount.value == null) return false
        val alreadyLoading = viewModel.isLoadingKrc20.value
        if (!alreadyLoading) withContext(Dispatchers.Main) { viewModel.refreshKrc20Tokens() }
        return withTimeoutOrNull(30_000L) {
            if (!alreadyLoading) viewModel.isLoadingKrc20.first { it }
            viewModel.isLoadingKrc20.first { !it }
            true
        } ?: false
    }

    suspend fun execute(viewModel: DecentralViewModel, name: String, args: JSONObject, onPaymentPrepared: (suspend (String, Double) -> Unit)? = null): String {
        return try {
            when (name) {
                "prepare_kaspa_payment" -> {
                    val recipient = args.optString("recipient_address").trim()
                    val amount = args.optDouble("amount_kas", Double.NaN)
                    val network = viewModel.selectedNetwork.value
                    val networkContext = resolveKaspaNetworkContext(network)
                    val expectedPrefix = networkContext?.addressPrefix.orEmpty()
                    if (viewModel.activeAccount.value == null) {
                        errorJson("No active wallet is available. Open and unlock the native wallet first.")
                    } else if (expectedPrefix.isBlank()) {
                        errorJson("KAS payments are unavailable on the selected network: $network. Select Kaspa Mainnet, Kaspa Testnet 10, or Kaspa Testnet 11 in the wallet first. No network was changed.")
                    } else if (!amount.isFinite() || amount <= 0.0) {
                        errorJson("amount_kas must be a finite number greater than zero.")
                    } else if (!recipient.startsWith("$expectedPrefix:", true) ||
                        !com.example.network.CryptoUtils.isValidKaspaAddress(recipient, expectedPrefix)) {
                        errorJson("Recipient address is invalid or does not match the selected $network network.")
                    } else if (onPaymentPrepared == null) {
                        errorJson("Native payment review is unavailable in this context. No transaction was created.")
                    } else {
                        onPaymentPrepared(recipient, amount)
                        JSONObject().put("success", true)
                            .put("status", "awaiting_user_review")
                            .put("network", network)
                            .put("asset", "KAS")
                            .put("amount_kas", amount)
                            .put("recipient_address", recipient)
                            .put("signed", false)
                            .put("broadcast", false)
                            .put("next_step", "The native wallet review sheet is open. The user must verify the recipient, amount and live fee, then explicitly confirm.")
                            .toString()
                    }
                }
                "get_wallet_status" -> {
                    val selectedNetwork = viewModel.selectedNetwork.value
                    val networkContext = resolveKaspaNetworkContext(selectedNetwork)
                    val refreshed = if (networkContext == null) false else refreshWallet(viewModel)
                    val wallet = viewModel.kaspaWalletState.value
                    JSONObject()
                        .put("refresh_completed", refreshed)
                        .put("network", selectedNetwork)
                        .put("network_id", networkContext?.networkId ?: JSONObject.NULL)
                        .put("address_prefix", networkContext?.addressPrefix ?: JSONObject.NULL)
                        .put("rpc_endpoint", networkContext?.rpcEndpoint ?: JSONObject.NULL)
                        .put("network_supported_for_native_kas", networkContext != null)
                        .put("balance_kas", if (networkContext != null) wallet.balanceKas else JSONObject.NULL)
                        .put("balance_usd_estimate", wallet.balanceUsd)
                        .put("kas_usd_price", wallet.priceUsd)
                        .put("utxo_count", if (networkContext != null) wallet.utxosCount else JSONObject.NULL)
                        .put("is_loading", wallet.isLoading)
                        .put("wallet_initialized", viewModel.activeAccount.value != null)
                        .toString()
                }
                "get_wallet_transactions" -> {
                    val refreshed = refreshWallet(viewModel)
                    val txs = viewModel.kaspaWalletState.value.recentTransactions
                    JSONObject().put("refresh_completed", refreshed).put("transactions", JSONArray().apply {
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
                    val networkContext = resolveKaspaNetworkContext(network)
                    val address = when (networkContext?.networkId) {
                        "mainnet" -> viewModel.mainnetAddress.value
                        "testnet-10", "testnet-11" -> viewModel.testnetAddress.value
                        else -> ""
                    }
                    if (networkContext == null) errorJson("Native Kaspa receive addresses are unavailable on the selected EVM network: $network.")
                    else if (address.isBlank()) errorJson("No receive address is available for ${networkContext.name}.")
                    else JSONObject()
                        .put("network", networkContext.name)
                        .put("network_id", networkContext.networkId)
                        .put("address_prefix", networkContext.addressPrefix)
                        .put("rpc_endpoint", networkContext.rpcEndpoint)
                        .put("receive_address", address)
                        .toString()
                }
                "get_kcc20_tokens" -> {
                    val refreshed = refreshKcc20(viewModel)
                    val tokens = viewModel.kcc20Tokens.value
                    JSONObject().put("refresh_completed", refreshed).put("network", viewModel.selectedNetwork.value)
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
                    val refreshed = refreshKrc20(viewModel)
                    val tokens = viewModel.pinnedTokens.value
                    JSONObject().put("refresh_completed", refreshed).put("protocol", "KRC-20")
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
                "get_krc20_transactions" -> {
                    val refreshed = refreshKrc20(viewModel)
                    val transactions = viewModel.pinnedTokens.value.flatMap { token ->
                        token.recentTransactions.map { tx -> token.tick to tx }
                    }.sortedByDescending { it.second.timestamp }.take(40)
                    JSONObject().put("refresh_completed", refreshed).put("protocol", "KRC-20")
                        .put("transactions", JSONArray().apply {
                            transactions.forEach { (ticker, tx) ->
                                put(JSONObject()
                                    .put("ticker", ticker)
                                    .put("tx_id", tx.txId)
                                    .put("token_id", tx.tokenId)
                                    .put("operation", tx.op)
                                    .put("amount", tx.amount)
                                    .put("from", tx.fromAddress)
                                    .put("to", tx.toAddress)
                                    .put("timestamp_ms", tx.timestamp)
                                    .put("status", tx.status)
                                    .put("fee_kas", tx.feeKas))
                            }
                        }).put("count", transactions.size).toString()
                }
                "get_kcc20_transactions" -> {
                    val refreshed = refreshKcc20(viewModel)
                    val txs = viewModel.kcc20Transactions.value
                    JSONObject().put("refresh_completed", refreshed).put("transactions", JSONArray().apply {
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
                    val pending = viewModel.dappPromptState.value
                    val pendingJson = when (pending) {
                        is DecentralViewModel.DappPromptState.Connect ->
                            JSONObject().put("type", "connect").put("origin", pending.origin).put("network", pending.network)
                        is DecentralViewModel.DappPromptState.SignTransaction ->
                            JSONObject().put("type", "sign_transaction").put("origin", pending.origin)
                                .put("network", pending.network).put("title", pending.title).put("action", pending.action)
                                .put("token_ticker", pending.tick).put("amount", pending.amount)
                                .put("destination", pending.destination).put("fee_kas", pending.feeKas)
                                .put("requires_user_approval", true)
                        is DecentralViewModel.DappPromptState.SignMessage ->
                            JSONObject().put("type", "sign_message").put("origin", pending.origin)
                                .put("network", pending.network).put("requires_user_approval", true)
                        is DecentralViewModel.DappPromptState.NoWallet ->
                            JSONObject().put("type", "no_wallet").put("origin", pending.origin)
                        is DecentralViewModel.DappPromptState.UnlockWallet ->
                            JSONObject().put("type", "unlock_wallet").put("origin", pending.origin)
                                .put("reason", pending.reason).put("requires_user_authentication", true)
                        null -> JSONObject.NULL
                    }
                    JSONObject().put("selected_network", viewModel.selectedNetwork.value)
                        .put("pending_dapp_request", pendingJson)
                        .put("current_pwa", current?.let {
                            JSONObject().put("name", it.name).put("url", it.url).put("manifest_url", it.manifestUrl ?: JSONObject.NULL)
                        } ?: JSONObject.NULL)
                        .put("installed_pwas", JSONArray().apply {
                            installed.forEach { pwa -> put(JSONObject().put("name", pwa.name).put("url", pwa.url).put("has_manifest", pwa.hasManifest)) }
                        }).put("note", "Pending dApp requests require the existing native user approval or authentication flow. This tool cannot approve or sign.")
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
                        if (!exists) {
                            withContext(Dispatchers.Main) { viewModel.toggleBookmark(url, title) }
                            withTimeoutOrNull(5_000L) { viewModel.bookmarks.first { list -> list.any { it.url == url } } }
                        }
                        val saved = exists || viewModel.isBookmarked(url)
                        JSONObject().put("success", saved).put("already_bookmarked", exists)
                            .put("title", title).put("url", url).toString()
                    }
                }
                "browser_go_back", "browser_go_forward", "browser_reload" -> {
                    val tabId = viewModel.activeTabId.value
                    val webView = tabId?.let { viewModel.webViewTabManager.getWebView(it) }
                    if (webView == null) errorJson("The active browser tab does not have an attached webpage.")
                    else {
                        val action = when (name) {
                            "browser_go_back" -> "go_back"
                            "browser_go_forward" -> "go_forward"
                            else -> "reload"
                        }
                        val canNavigate = withContext(Dispatchers.Main) {
                            when (name) {
                                "browser_go_back" -> webView.canGoBack()
                                "browser_go_forward" -> webView.canGoForward()
                                else -> true
                            }
                        }
                        if (!canNavigate) errorJson("The active webpage has no matching navigation history.")
                        else {
                            withContext(Dispatchers.Main) {
                                when (name) {
                                    "browser_go_back" -> webView.goBack()
                                    "browser_go_forward" -> webView.goForward()
                                    else -> webView.reload()
                                }
                            }
                            JSONObject().put("success", true).put("action", action).toString()
                        }
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

    private fun isPublicWebUrl(value: String): Boolean {
        return try {
        if (value.length > 2048) return false
        val uri = URI(value)
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (!(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) ||
            host.isBlank() || uri.userInfo != null ||
            host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".internal")) return false
        val addresses = java.net.InetAddress.getAllByName(host)
        addresses.isNotEmpty() && addresses.all { address ->
            if (address.isAnyLocalAddress || address.isLoopbackAddress ||
                address.isLinkLocalAddress || address.isSiteLocalAddress ||
                address.isMulticastAddress) {
                false
            } else {
                val bytes = address.address.map { it.toInt() and 0xff }
                if (bytes.size == 4) {
                    val a = bytes[0]
                    val b = bytes[1]
                    !(a == 0 || a == 10 || a == 127 || a >= 224 ||
                        (a == 169 && b == 254) ||
                        (a == 172 && b in 16..31) ||
                        (a == 192 && b == 168) ||
                        (a == 100 && b in 64..127) ||
                        (a == 198 && b in 18..19))
                } else {
                    (bytes[0] and 0xfe) != 0xfc
                }
            }
        }
        } catch (_: Exception) { false }
    }

    private fun errorJson(message: String): String =
        JSONObject().put("success", false).put("error", message).toString()
}

package com.example.dapp

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.network.CryptoUtils
import com.example.network.kaspa.KaspaTransactionEngine
import com.example.viewmodel.DecentralViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.Collections
import java.util.WeakHashMap
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers

object KaspaDappBridge {
    private const val BRIDGE_NAME = "kaspaDappBridge"
    private const val LOG_TAG = "KaspaDappBridge"
    private const val PREFS = "dapp_wallet_permissions"
    private const val MAX_MESSAGE_LENGTH = 4096
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cryptoExecutor = Executors.newSingleThreadExecutor()
    private val installedWebViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
    private val listenerRegisteredWebViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

    private fun isInstalled(webView: WebView): Boolean =
        synchronized(installedWebViews) { installedWebViews.contains(webView) }

    private fun markInstalled(webView: WebView) {
        synchronized(installedWebViews) { installedWebViews.add(webView) }
    }

    private fun hasListener(webView: WebView): Boolean =
        synchronized(listenerRegisteredWebViews) { listenerRegisteredWebViews.contains(webView) }

    private fun markListenerRegistered(webView: WebView) {
        synchronized(listenerRegisteredWebViews) { listenerRegisteredWebViews.add(webView) }
    }

    private fun scriptToAddress(scriptPublicKey: KaspaTransactionEngine.KaspaScriptPublicKey, prefix: String): String {
        val s = scriptPublicKey.script.replace(" ", "").lowercase()
        return try {
            if (scriptPublicKey.version == 0 && s.matches(Regex("20[0-9a-f]{64}ac"))) {
                val pubBytes = CryptoUtils.hexToBytes(s.substring(2, 66))
                CryptoUtils.encodeRealKaspaAddress(pubBytes, prefix)
            } else if (scriptPublicKey.version == 0 && s.matches(Regex("aa20[0-9a-f]{64}87"))) {
                val hashBytes = CryptoUtils.hexToBytes(s.substring(4, 68))
                CryptoUtils.encodeP2shAddress(hashBytes, prefix)
            } else {
                s.take(22)
            }
        } catch (_: Throwable) {
            s.take(22)
        }
    }

    fun install(context: Context, webView: WebView, viewModel: DecentralViewModel) {
        val supportsWebMessageListener =
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        val supportsDocumentStartScript =
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (!supportsWebMessageListener || !supportsDocumentStartScript) {
            Log.w(
                LOG_TAG,
                "Provider installation skipped: WEB_MESSAGE_LISTENER=$supportsWebMessageListener, " +
                    "DOCUMENT_START_SCRIPT=$supportsDocumentStartScript. Update Android System WebView or Chrome."
            )
            return
        }

        try {
            if (!hasListener(webView)) {
                try {
                    WebViewCompat.addWebMessageListener(
                        webView,
                        BRIDGE_NAME,
                        setOf("*")
                    ) { view, message, sourceOrigin, isMainFrame, replyProxy ->
                val origin = normalizeOrigin(sourceOrigin.toString())
                val currentOrigin = normalizeOrigin(view.url.orEmpty())
                if (!isMainFrame || origin == null || origin != currentOrigin) return@addWebMessageListener
                val request = try {
                    JSONObject(message.data ?: return@addWebMessageListener)
                } catch (_: Exception) {
                    return@addWebMessageListener
                }
                val requestId = request.optString("id")
                val method = request.optString("method")
                val params = request.opt("params")
                val account = viewModel.activeAccount.value ?: runCatching {
                    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        viewModel.getActiveAccountFromDb()
                    }
                }.getOrNull()
                val currentSelectedNet = viewModel.selectedNetwork.value
                val network = currentSelectedNet
                val selectedNetworkId = networkId(network)
                val prefix = when (selectedNetworkId) {
                    "mainnet" -> "kaspa"
                    "testnet-10", "testnet-11" -> "kaspatest"
                    else -> ""
                }
                val isConnected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(origin, false)

                fun respond(
                    result: Any? = null,
                    errorCode: Int? = null,
                    errorMessage: String? = null,
                    errorData: JSONObject? = null
                ) {
                    val response = JSONObject().put("id", requestId)
                    if (errorCode != null) {
                        val errorObject = JSONObject()
                            .put("code", errorCode)
                            .put("message", errorMessage ?: "Request rejected")
                        if (errorData != null) errorObject.put("data", errorData)
                        response.put("error", errorObject)
                    } else {
                        response.put("result", result ?: JSONObject.NULL)
                    }
                    try {
                        replyProxy.postMessage(response.toString())
                    } catch (_: Throwable) {}
                }

                fun currentKeyPair(): CryptoUtils.KaspaKeyPair? {
                    if (prefix.isBlank()) return null
                    val active = account ?: runCatching {
                        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            viewModel.getActiveAccountFromDb()
                        }
                    }.getOrNull() ?: return null
                    return try {
                        val seed = CryptoUtils.getDecryptedSeed(active.seedPhrase)
                        CryptoUtils.deriveKaspaKeyPair(seed, prefix)
                    } catch (_: Throwable) {
                        try { CryptoUtils.deriveKaspaKeyPair(active.seedPhrase, prefix) } catch (_: Throwable) { null }
                    }
                }

                fun ensureUnlocked(onRejectMsg: String = "Wallet is locked. User cancelled unlock", onSuccess: () -> Unit) {
                    if (viewModel.isWalletLocked.value && viewModel.hasWalletPassword.value) {
                        mainHandler.post {
                            viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.UnlockWallet(
                                origin = origin,
                                reason = "Unlock wallet to authorize request from $origin",
                                onAuthenticatePassword = { pwd, callback ->
                                    viewModel.unlockWalletWithPassword(pwd) { success ->
                                        callback(success)
                                    }
                                },
                                onAuthenticateBiometric = if (viewModel.biometricsEnabled.value) {
                                    { callback ->
                                        com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                            context = context,
                                            title = "Unlock Kaspa Wallet",
                                            subtitle = "Authenticate to connect to $origin",
                                            onSuccess = { callback(true) },
                                            onError = { callback(false) }
                                        )
                                    }
                                } else null,
                                onUnlocked = {
                                    viewModel.dappPromptState.value = null
                                    onSuccess()
                                },
                                onReject = {
                                    viewModel.dappPromptState.value = null
                                    respond(errorCode = 4001, errorMessage = onRejectMsg)
                                }
                            )
                        }
                    } else {
                        onSuccess()
                    }
                }

                fun ensureConnectedAndUnlocked(keyPair: CryptoUtils.KaspaKeyPair?, onRejectMsg: String = "User rejected connection", onSuccess: () -> Unit) {
                    if (keyPair == null) {
                        mainHandler.post {
                            viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.NoWallet(
                                origin = origin,
                                onOpenWallet = {
                                    viewModel.setTab(com.example.viewmodel.AppTab.KASPA_WALLET)
                                    respond(errorCode = 4001, errorMessage = "User has not created a Kaspa wallet yet")
                                    viewModel.dappPromptState.value = null
                                },
                                onReject = {
                                    respond(errorCode = 4001, errorMessage = "User rejected wallet connection")
                                    viewModel.dappPromptState.value = null
                                }
                            )
                        }
                        return
                    }

                    val isCurrentlyConnected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getBoolean(origin, false)

                    if (!isCurrentlyConnected) {
                        cryptoExecutor.execute {
                            val balance = try {
                                kotlinx.coroutines.runBlocking {
                                    viewModel.walletService.fetchDappBalance(keyPair.kaspaAddress, network)
                                }
                            } catch (_: Throwable) { null }
                            val balanceSompi = balance?.optLong("balance", 0L) ?: 0L
                            val balanceKasStr = com.example.network.kaspa.KaspaTransactionEngine.formatKas(
                                com.example.network.kaspa.KaspaTransactionEngine.sompiToKas(balanceSompi)
                            ) + " KAS"
                            val activeKcc20 = viewModel.kcc20Tokens.value.filter {
                                (it.balanceRaw.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) > java.math.BigDecimal.ZERO
                            }
                            viewModel.refreshKcc20Tokens()
                            mainHandler.post {
                                viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.Connect(
                                    origin = origin,
                                    network = network,
                                    accountAddress = keyPair.kaspaAddress,
                                    accountHandle = account?.handle.orEmpty(),
                                    balanceKas = balanceKasStr,
                                    kcc20Tokens = activeKcc20,
                                    onApprove = {
                                        viewModel.dappPromptState.value = null
                                        ensureUnlocked(onSuccess = {
                                            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                                .edit().putBoolean(origin, true).apply()
                                            onSuccess()
                                        })
                                    },
                                    onReject = {
                                        respond(errorCode = 4001, errorMessage = onRejectMsg)
                                        viewModel.dappPromptState.value = null
                                    }
                                )
                            }
                        }
                    } else {
                        ensureUnlocked(onSuccess = onSuccess)
                    }
                }

                when (method) {
                    "requestAccounts" -> {
                        val keyPair = currentKeyPair()
                        ensureConnectedAndUnlocked(keyPair) {
                            respond(JSONArray().put(keyPair!!.kaspaAddress))
                        }
                    }

                    "requestNetworkAccounts", "getNetworkAccounts" -> {
                        val keyPair = currentKeyPair()
                        if (keyPair == null) {
                            mainHandler.post {
                                viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.NoWallet(
                                    origin = origin,
                                    onOpenWallet = {
                                        viewModel.setTab(com.example.viewmodel.AppTab.KASPA_WALLET)
                                        respond(errorCode = 4001, errorMessage = "User has not created a Kaspa wallet yet")
                                        viewModel.dappPromptState.value = null
                                    },
                                    onReject = {
                                        respond(errorCode = 4001, errorMessage = "User rejected wallet connection")
                                        viewModel.dappPromptState.value = null
                                    }
                                )
                            }
                        } else {
                            val active = account
                            val evmAddress = active?.seedPhrase?.let {
                                com.example.dapp.EvmWalletEngine.address(it)
                            } ?: "0x0000000000000000000000000000000000000000"
                            val accountsObj = JSONObject()
                                .put(networkId(network), keyPair.kaspaAddress)
                                .put("kasplex", evmAddress)
                                .put("igra", evmAddress)
                                .put("selected_network", network)
                            if (method == "requestNetworkAccounts" && !isConnected) {
                                cryptoExecutor.execute {
                                    val balance = try {
                                        kotlinx.coroutines.runBlocking {
                                            viewModel.walletService.fetchDappBalance(keyPair.kaspaAddress, network)
                                        }
                                    } catch (_: Throwable) { null }
                                    val balanceSompi = balance?.optLong("balance", 0L) ?: 0L
                                    val balanceKasStr = com.example.network.kaspa.KaspaTransactionEngine.formatKas(
                                        com.example.network.kaspa.KaspaTransactionEngine.sompiToKas(balanceSompi)
                                    ) + " KAS"
                                    mainHandler.post {
                                        viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.Connect(
                                            origin = origin,
                                            network = "$network (L1 & L2)",
                                            accountAddress = keyPair.kaspaAddress,
                                            accountHandle = account?.handle.orEmpty(),
                                            balanceKas = balanceKasStr,
                                            onApprove = {
                                                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                                    .edit().putBoolean(origin, true).apply()
                                                respond(accountsObj)
                                                viewModel.dappPromptState.value = null
                                            },
                                            onReject = {
                                                respond(errorCode = 4001, errorMessage = "User rejected network connection")
                                                viewModel.dappPromptState.value = null
                                            }
                                        )
                                    }
                                }
                            } else {
                                respond(accountsObj)
                            }
                        }
                    }

                    "getAccounts" -> {
                        val keyPair = if (isConnected) currentKeyPair() else null
                        respond(if (keyPair == null) JSONArray() else JSONArray().put(keyPair.kaspaAddress))
                    }

                    "getNetwork" -> {
                        val id = networkId(network)
                        if (id == "unsupported") {
                            respond(errorCode = -32000, errorMessage = "Native Kaspa provider is unavailable on the selected network: $network")
                        } else {
                            respond(id)
                        }
                    }

                    "getBalance" -> {
                        if (!isConnected) {
                            respond(errorCode = 4100, errorMessage = "Connect this site first")
                        } else {
                            val keyPair = currentKeyPair()
                            if (keyPair == null) {
                                respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                            } else {
                                cryptoExecutor.execute {
                                    val balance = try {
                                        kotlinx.coroutines.runBlocking {
                                            viewModel.walletService.fetchDappBalance(keyPair.kaspaAddress, network)
                                        }
                                    } catch (error: Throwable) {
                                        null
                                    }
                                    mainHandler.post {
                                        if (balance == null) respond(errorCode = -32000, errorMessage = "Unable to fetch live Kaspa balance")
                                        else respond(balance)
                                    }
                                }
                            }
                        }
                    }

                    "getUtxoEntries" -> {
                        if (!isConnected) {
                            respond(errorCode = 4100, errorMessage = "Connect this site first")
                        } else {
                            val keyPair = currentKeyPair()
                            if (keyPair == null) {
                                respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                            } else {
                                cryptoExecutor.execute {
                                    val utxos = try {
                                        kotlinx.coroutines.runBlocking {
                                            viewModel.walletService.fetchDappUtxos(keyPair.kaspaAddress, network)
                                        }
                                    } catch (error: Throwable) {
                                        null
                                    }
                                    mainHandler.post {
                                        if (utxos == null) respond(errorCode = -32000, errorMessage = "Unable to fetch live Kaspa UTXOs")
                                        else respond(utxos)
                                    }
                                }
                            }
                        }
                    }

                    "getPublicKey" -> {
                        val keyPair = currentKeyPair()
                        if (keyPair == null) {
                            mainHandler.post {
                                viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.NoWallet(
                                    origin = origin,
                                    onOpenWallet = {
                                        viewModel.setTab(com.example.viewmodel.AppTab.KASPA_WALLET)
                                        respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                                        viewModel.dappPromptState.value = null
                                    },
                                    onReject = {
                                        respond(errorCode = 4001, errorMessage = "User rejected wallet connection")
                                        viewModel.dappPromptState.value = null
                                    }
                                )
                            }
                        } else if (isConnected) {
                            respond(keyPair.publicKeyHex)
                        } else {
                            cryptoExecutor.execute {
                                val balance = try {
                                    kotlinx.coroutines.runBlocking {
                                        viewModel.walletService.fetchDappBalance(keyPair.kaspaAddress, network)
                                    }
                                } catch (_: Throwable) { null }
                                val balanceSompi = balance?.optLong("balance", 0L) ?: 0L
                                val balanceKasStr = com.example.network.kaspa.KaspaTransactionEngine.formatKas(
                                    com.example.network.kaspa.KaspaTransactionEngine.sompiToKas(balanceSompi)
                                ) + " KAS"
                                mainHandler.post {
                                    viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.Connect(
                                        origin = origin,
                                        network = network,
                                        accountAddress = keyPair.kaspaAddress,
                                        accountHandle = account?.handle.orEmpty(),
                                        balanceKas = balanceKasStr,
                                        onApprove = {
                                            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                                .edit().putBoolean(origin, true).apply()
                                            respond(keyPair.publicKeyHex)
                                            viewModel.dappPromptState.value = null
                                        },
                                        onReject = {
                                            respond(errorCode = 4001, errorMessage = "User rejected wallet connection")
                                            viewModel.dappPromptState.value = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    "signMessage" -> {
                        if (!isConnected) {
                            respond(errorCode = 4100, errorMessage = "Connect this site first")
                            return@addWebMessageListener
                        }
                        val text = when (params) {
                            is String -> params
                            is JSONArray -> params.optString(0)
                            is JSONObject -> params.optString("message", params.optString("msg"))
                            else -> ""
                        }
                        if (text.isBlank() || text.length > MAX_MESSAGE_LENGTH) {
                            respond(errorCode = -32602, errorMessage = "Message must contain 1-$MAX_MESSAGE_LENGTH characters")
                            return@addWebMessageListener
                        }
                        val keyPair = currentKeyPair()
                        if (keyPair == null) {
                            respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                            return@addWebMessageListener
                        }
                        mainHandler.post {
                            viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.SignMessage(
                                origin = origin,
                                network = network,
                                accountAddress = keyPair.kaspaAddress,
                                message = text,
                                onApprove = {
                                    viewModel.dappPromptState.value = null
                                    cryptoExecutor.execute {
                                        val signature = try {
                                            CryptoUtils.signKaspaPersonalMessage(
                                                keyPair.privateKey,
                                                text.toByteArray(Charsets.UTF_8)
                                            )
                                        } catch (_: Throwable) {
                                            null
                                        }
                                        mainHandler.post {
                                            if (signature == null) respond(errorCode = -32603, errorMessage = "Message signing failed")
                                            else {
                                                respond(signature)
                                            }
                                        }
                                    }
                                },
                                onReject = {
                                    viewModel.dappPromptState.value = null
                                    respond(errorCode = 4001, errorMessage = "User rejected message signature")
                                }
                            )
                        }
                    }

                    "signPskt", "signPksk", "signPsktTransaction", "signKcc20Pskt" -> {
                        if (!isConnected) {
                            respond(errorCode = 4100, errorMessage = "Connect this site first")
                            return@addWebMessageListener
                        }
                        val keyPair = currentKeyPair()
                        if (keyPair == null) {
                            respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                            return@addWebMessageListener
                        }
                        val paramsObject = when (params) {
                            is JSONObject -> params
                            is String -> try { JSONObject(params) } catch (_: Exception) { null }
                            is JSONArray -> params.optJSONObject(0)
                            else -> null
                        }
                        val requestedSender = paramsObject?.optString("sender", "").orEmpty()
                        if (requestedSender.isNotBlank() &&
                            !requestedSender.equals(keyPair.kaspaAddress, ignoreCase = true)
                        ) {
                            respond(errorCode = 4100, errorMessage = "Requested sender does not match the active wallet")
                            return@addWebMessageListener
                        }
                        val txJson = paramsObject?.optString("txJsonString",
                            paramsObject.optString("pskt",
                            paramsObject.optString("transaction",
                            paramsObject.optString("txJson",
                            paramsObject.optString("rawTx", ""))))).orEmpty()
                        val options = paramsObject?.optJSONObject("options")
                            ?: paramsObject?.optJSONObject("params")
                        val rawSignInputs = options?.optJSONArray("signInputs")
                            ?: paramsObject?.optJSONArray("signInputs")
                            ?: paramsObject?.optJSONArray("inputs")
                        if (txJson.isBlank() || rawSignInputs == null || rawSignInputs.length() == 0) {
                            respond(errorCode = -32602, errorMessage = "signPskt requires txJsonString and a non-empty options.signInputs array")
                            return@addWebMessageListener
                        }
                        cryptoExecutor.execute {
                            var topLevelRedeemScript = sequenceOf(
                                options?.optString("redeemScript", ""),
                                options?.optString("redeem_script", ""),
                                options?.optString("redeem", ""),
                                paramsObject?.optString("redeemScript", ""),
                                paramsObject?.optString("redeem_script", ""),
                                paramsObject?.optString("redeem", "")
                            ).filterNotNull().firstOrNull { it.isNotBlank() }.orEmpty()

                            val signInputs = JSONArray()
                            val txObj = try { JSONObject(txJson) } catch (_: Exception) { null }
                            val txInputsArray = txObj?.optJSONObject("transaction")?.optJSONArray("inputs")
                                ?: txObj?.optJSONArray("inputs")
                            val txUtxosArray = txObj?.optJSONObject("transaction")?.optJSONArray("utxoEntries")
                                ?: txObj?.optJSONArray("utxoEntries")
                                ?: txObj?.optJSONArray("utxos")

                            for (i in 0 until rawSignInputs.length()) {
                                val item = rawSignInputs.opt(i)
                                val (idx, shType, reqRedeem) = when (item) {
                                    is JSONObject -> Triple(
                                        item.optInt("index", item.optInt("inputIndex", -1)),
                                        if (item.has("sighashType")) item.optInt("sighashType", 1) else 1,
                                        sequenceOf(
                                            item.optString("redeemScript", ""),
                                            item.optString("redeem_script", ""),
                                            item.optString("redeem", "")
                                        ).firstOrNull { it.isNotBlank() }.orEmpty()
                                    )
                                    is Number -> Triple(item.toInt(), 1, "")
                                    is String -> Triple(item.toIntOrNull() ?: -1, 1, "")
                                    else -> Triple(-1, 1, "")
                                }
                                var finalInputRedeem = reqRedeem
                                if (finalInputRedeem.isBlank() && idx >= 0) {
                                    finalInputRedeem = sequenceOf(
                                        options?.optJSONArray("redeemScripts")?.optString(idx, ""),
                                        paramsObject?.optJSONArray("redeemScripts")?.optString(idx, ""),
                                        options?.optJSONObject("redeemScripts")?.optString(idx.toString(), ""),
                                        paramsObject?.optJSONObject("redeemScripts")?.optString(idx.toString(), "")
                                    ).filterNotNull().firstOrNull { it.isNotBlank() }.orEmpty()
                                }

                                if (finalInputRedeem.isBlank() && idx >= 0 && txInputsArray != null && idx < txInputsArray.length()) {
                                    val txIn = txInputsArray.optJSONObject(idx)
                                    val utxoIn = txIn?.optJSONObject("utxoEntry") ?: txUtxosArray?.optJSONObject(idx)
                                    val spk = utxoIn?.optJSONObject("scriptPublicKey")?.optString("script", "")
                                        ?: utxoIn?.optString("script", "")
                                        ?: ""
                                    val p2shMatch = Regex("aa20([0-9a-fA-F]{64})87").matchEntire(spk.replace(" ", "").lowercase())?.groupValues?.get(1)
                                    if (p2shMatch != null) {
                                        val prevTxId = txIn?.optJSONObject("previousOutpoint")?.optString("transactionId", "")
                                            ?: txIn?.optString("transactionId", "")
                                            ?: ""
                                        val prevIdx = txIn?.optJSONObject("previousOutpoint")?.optInt("index", -1)
                                            ?: txIn?.optInt("index", -1)
                                            ?: 0
                                        finalInputRedeem = kotlinx.coroutines.runBlocking {
                                            com.example.network.kaspa.Kcc20NoteScanner.resolveOrReconstructRedeemScript(
                                                txId = prevTxId,
                                                outputIndex = prevIdx,
                                                p2shScriptHash = p2shMatch,
                                                walletAddress = keyPair.kaspaAddress,
                                                walletPublicKeyHex = keyPair.publicKeyHex,
                                                network = network
                                            )
                                        }.orEmpty()
                                    }
                                }

                                if (finalInputRedeem.isNotBlank() && topLevelRedeemScript.isBlank()) {
                                    topLevelRedeemScript = finalInputRedeem
                                }

                                val resolvedInputObj = JSONObject()
                                    .put("index", idx)
                                    .put("sighashType", shType)
                                if (finalInputRedeem.isNotBlank()) {
                                    resolvedInputObj.put("redeemScript", finalInputRedeem)
                                }
                                signInputs.put(resolvedInputObj)
                            }

                            val prepared = try {
                                DappPsktTransaction.prepareForSigning(
                                    txJson,
                                    signInputs,
                                    keyPair.publicKeyHex,
                                    redeemScript = topLevelRedeemScript
                                )
                            } catch (error: Throwable) {
                                mainHandler.post {
                                    respond(errorCode = -32602, errorMessage = error.message ?: "Transaction review failed")
                                }
                                return@execute
                            }
                            val externalOutput = prepared.transaction.outputs.firstOrNull { out ->
                                val script = out.scriptPublicKey.script.replace(" ", "").lowercase()
                                val walletScript = ("20" + keyPair.publicKeyHex.lowercase() + "ac")
                                script != walletScript
                            } ?: prepared.transaction.outputs.firstOrNull()
                            val resolvedDest = externalOutput?.let { out ->
                                scriptToAddress(out.scriptPublicKey, prefix)
                            } ?: keyPair.kaspaAddress

                            mainHandler.post {
                                viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.SignTransaction(
                                    origin = origin,
                                    network = network,
                                    accountAddress = keyPair.kaspaAddress,
                                    title = "Sign Kaspa Transaction",
                                    action = "PSKT Transaction Signing",
                                    tick = "KAS",
                                    amount = com.example.network.kaspa.KaspaTransactionEngine.formatKas(com.example.network.kaspa.KaspaTransactionEngine.sompiToKas(prepared.outputSumSompi)),
                                    amountLabel = "Total outputs (includes wallet change)",
                                    destination = resolvedDest,
                                    inputsCount = prepared.signInputs.size,
                                    feeKas = prepared.feeKas,
                                    totalAmountKas = com.example.network.kaspa.KaspaTransactionEngine.formatKas(com.example.network.kaspa.KaspaTransactionEngine.sompiToKas(prepared.outputSumSompi)),
                                    isBroadcast = false,
                                    onApprove = {
                                        viewModel.dappPromptState.value = null
                                        cryptoExecutor.execute {
                                            val signingOutcome = try {
                                                Result.success(DappPsktTransaction.signForDapp(prepared, keyPair.privateKey))
                                            } catch (error: Throwable) {
                                                Result.failure(error)
                                            }
                                            mainHandler.post {
                                                signingOutcome.fold(
                                                    onSuccess = { signedResult ->
                                                        viewModel.onSwapTransactionSigned(prepared.originalJson.toString())
                                                        viewModel.triggerPostSwapRefresh()
                                                        respond(signedResult)
                                                    },
                                                    onFailure = { error ->
                                                        val diagnostic = JSONObject()
                                                            .put("type", "kaspa_pskt_signing_failure")
                                                            .put("network", network)
                                                            .put("txJsonString", txJson)
                                                            .put("signInputs", signInputs)
                                                            .put("redeemScript", options?.optString("redeemScript", "").orEmpty())
                                                            .put("error", error.message ?: error.javaClass.simpleName)
                                                            .put("broadcastAttempted", false)
                                                        respond(
                                                            errorCode = -32603,
                                                            errorMessage = error.message ?: "Transaction signing failed",
                                                            errorData = diagnostic
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    onReject = {
                                        viewModel.dappPromptState.value = null
                                        respond(errorCode = 4001, errorMessage = "User rejected transaction signature")
                                    }
                                )
                            }
                        }
                    }

                    "signKrc20Transaction", "signKcc20Transaction", "signKcc20", "signKrc20", "sendKRC20", "sendKCC20", "transferKRC721", "transferKNS", "mintCovenantWyrm", "actCovenantWyrm" -> {
                        if (!isConnected) {
                            respond(errorCode = 4100, errorMessage = "Connect this site first")
                            return@addWebMessageListener
                        }
                        val keyPair = currentKeyPair()
                        if (keyPair == null) {
                            respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                            return@addWebMessageListener
                        }
                        val active = account
                        if (active == null) {
                            respond(errorCode = 4100, errorMessage = "No active Kaspa account found")
                            return@addWebMessageListener
                        }
                        val paramsObj = when (params) {
                            is JSONObject -> params
                            is JSONArray -> params.optJSONObject(0) ?: JSONObject()
                            is String -> try { JSONObject(params) } catch (_: Exception) { JSONObject() }
                            else -> JSONObject()
                        }
                        val tick = paramsObj.optString("tick", paramsObj.optString("ticker", paramsObj.optString("symbol", "KAS"))).uppercase()
                        val op = paramsObj.optString("op", paramsObj.optString("type", "transfer")).lowercase()
                        val amtStr = paramsObj.optString("amt", paramsObj.optString("amount", "0"))
                        val toAddr = paramsObj.optString("to", paramsObj.optString("destination", ""))
                        val rawTxJson = paramsObj.optString("txJsonString", paramsObj.optString("transaction", ""))
                        val protocolLabel = if (method.contains("kcc", ignoreCase = true)) "KCC-20" else "KRC-20"

                        val isStructuredTx = rawTxJson.isNotBlank() && (rawTxJson.contains("inputs") || rawTxJson.contains("previousOutpoint") || rawTxJson.contains("version"))
                        val options = paramsObj.optJSONObject("options")
                        val signInputs = options?.optJSONArray("signInputs")
                            ?: JSONArray().put(JSONObject().put("index", 0).put("sighashType", KaspaTransactionEngine.SIGHASH_ALL))
                        val rScript = options?.optString("redeemScript", options?.optString("redeem_script", "")).orEmpty()

                        val prePrepared = if (isStructuredTx) {
                            try {
                                DappPsktTransaction.prepareForSigning(rawTxJson, signInputs, keyPair.publicKeyHex, redeemScript = rScript)
                            } catch (e: Throwable) {
                                respond(errorCode = -32602, errorMessage = "Malformed transaction payload: ${e.message}")
                                return@addWebMessageListener
                            }
                        } else null

                        // SECURITY: Verify prompt parameters against the ACTUAL transaction payload being signed!
                        val externalOutputs = prePrepared?.transaction?.outputs?.filter { out ->
                            val script = out.scriptPublicKey.script.replace(" ", "").lowercase()
                            val walletScript = ("20" + keyPair.publicKeyHex.lowercase() + "ac")
                            script != walletScript
                        } ?: emptyList()

                        val externalSompi = externalOutputs.sumOf { it.amount }
                        val actualOutgoingKas = if (prePrepared != null) {
                            if (externalOutputs.isNotEmpty()) {
                                KaspaTransactionEngine.formatKas(KaspaTransactionEngine.sompiToKas(externalSompi))
                            } else {
                                KaspaTransactionEngine.formatKas(KaspaTransactionEngine.sompiToKas(prePrepared.outputSumSompi))
                            }
                        } else null

                        val resolvedDestination = if (prePrepared != null) {
                            externalOutputs.firstOrNull()?.let { out ->
                                scriptToAddress(out.scriptPublicKey, prefix)
                            } ?: (if (toAddr.isNotBlank()) toAddr else keyPair.kaspaAddress)
                        } else {
                            if (toAddr.isNotBlank()) toAddr else keyPair.kaspaAddress
                        }

                        val resolvedAmount = if (actualOutgoingKas != null) {
                            if (amtStr.isNotBlank() && amtStr != "0") "$amtStr $tick ($actualOutgoingKas KAS)" else "$actualOutgoingKas KAS"
                        } else {
                            amtStr
                        }

                        mainHandler.post {
                            viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.SignTransaction(
                                origin = origin,
                                network = network,
                                accountAddress = keyPair.kaspaAddress,
                                title = if (prePrepared != null) "Confirm On-Chain Transaction" else "Confirm $tick Transaction",
                                action = "${op.uppercase()} $tick ($protocolLabel)",
                                tick = tick,
                                amount = resolvedAmount,
                                destination = resolvedDestination,
                                inputsCount = prePrepared?.transaction?.inputs?.size ?: 1,
                                feeKas = prePrepared?.feeKas ?: "0.0001 KAS",
                                totalAmountKas = actualOutgoingKas ?: amtStr,
                                onApprove = {
                                    viewModel.dappPromptState.value = null
                                    val contractId = paramsObj.optString("contractId",
                                        paramsObj.optString("contract",
                                        paramsObj.optString("tokenId",
                                        paramsObj.optString("covenantId",
                                        paramsObj.optString("covenant_id", "")))))
                                    if (contractId.isNotBlank()) {
                                        viewModel.addTrackedKcc20Contract(contractId)
                                    }
                                cryptoExecutor.execute {
                                    // Case A: dApp provided structured PSKT/transaction JSON to sign
                                    if (prePrepared != null) {
                                        val prepared = prePrepared
                                        if (prepared != null) {
                                            val signedJson = try {
                                                DappPsktTransaction.sign(prepared, keyPair.privateKey)
                                            } catch (_: Throwable) { null }
                                            if (signedJson == null) {
                                                mainHandler.post {
                                                    respond(errorCode = -32603, errorMessage = "Transaction signing failed; nothing was broadcast")
                                                }
                                                return@execute
                                            }

                                            val receipt = try {
                                                kotlinx.coroutines.runBlocking {
                                                    viewModel.walletService.submitDappTransaction(
                                                        prepared.transaction,
                                                        keyPair.kaspaAddress,
                                                        network
                                                    )
                                                }
                                            } catch (_: Throwable) { null }

                                            val realTxId = receipt?.transactionId?.takeIf { it.isNotBlank() }
                                                ?: com.example.network.kaspa.KaspaTransactionEngine.calcTransactionId(prepared.transaction)

                                            if (receipt?.accepted == true) {
                                                val amtDouble = amtStr.toDoubleOrNull()
                                                if (amtDouble != null && amtDouble > 0.0) {
                                                    viewModel.recordAndPinToken(
                                                        tick = tick,
                                                        op = op.uppercase(),
                                                        amount = amtDouble,
                                                        txId = realTxId,
                                                        rawJson = signedJson
                                                    )
                                                }
                                            }

                                            mainHandler.post {
                                                if (receipt?.accepted == true) {
                                                    viewModel.onSwapTransactionSigned(signedJson)
                                                    viewModel.triggerPostSwapRefresh()
                                                    respond(
                                                        JSONObject()
                                                            .put("commitTxId", realTxId)
                                                            .put("revealTxId", realTxId)
                                                            .put("transactionId", realTxId)
                                                            .put("status", "accepted")
                                                            .put("tick", tick)
                                                            .put("op", op)
                                                            .put("amt", amtStr)
                                                            .put("signedTxJson", signedJson)
                                                    )
                                                } else {
                                                    respond(
                                                        errorCode = -32000,
                                                        errorMessage = receipt?.error?.takeIf { it.isNotBlank() }
                                                            ?: "Kaspa node did not accept the transaction; it was not recorded as a successful token transfer"
                                                    )
                                                }
                                            }
                                            return@execute
                                        }
                                    }

                                    if (method.contains("kcc", ignoreCase = true)) {
                                        val amtDouble = amtStr.toDoubleOrNull() ?: 0.0
                                        if (toAddr.isNotBlank() && amtDouble > 0.0) {
                                            val res = kotlinx.coroutines.runBlocking {
                                                viewModel.executeKbKcc20Transfer(toAddr, amtDouble)
                                            }
                                            mainHandler.post {
                                                res.fold(
                                                    onSuccess = { txId ->
                                                        respond(
                                                            JSONObject()
                                                                .put("transactionId", txId)
                                                                .put("status", "accepted")
                                                                .put("tick", "KB")
                                                                .put("op", "transfer")
                                                                .put("amt", amtStr)
                                                                .put("to", toAddr)
                                                        )
                                                    },
                                                    onFailure = { err ->
                                                        respond(
                                                            errorCode = -32000,
                                                            errorMessage = err.message ?: "KCC-20 transfer failed"
                                                        )
                                                    }
                                                )
                                            }
                                        } else {
                                            mainHandler.post {
                                                respond(
                                                    errorCode = -32602,
                                                    errorMessage = "KCC-20 transfer requires recipient address and amount > 0."
                                                )
                                            }
                                        }
                                        return@execute
                                    }

                                    // KRC-20 fallback path remains separate from KCC-20 covenant transfers.
                                    val destination = if (toAddr.isNotBlank() && (toAddr.startsWith("kaspa:") || toAddr.startsWith("kaspatest:"))) {
                                        toAddr
                                    } else if (op == "transfer") {
                                        mainHandler.post {
                                            respond(
                                                errorCode = -32602,
                                                errorMessage = "KRC-20 transfer requires a valid recipient address (kaspa: or kaspatest:)"
                                            )
                                        }
                                        return@execute
                                    } else {
                                        // For KRC-20 deploy/mint commit inscriptions, the commit UTXO must be self-addressed to the user's own wallet!
                                        keyPair.kaspaAddress
                                    }

                                    val sendResult = try {
                                        kotlinx.coroutines.runBlocking {
                                            viewModel.walletService.sendKaspa(
                                                senderAddress = keyPair.kaspaAddress,
                                                senderSeed = active.seedPhrase,
                                                recipientAddress = destination,
                                                amountKas = 0.2 // Standard inscription commit amount on Kaspa BlockDAG
                                            )
                                        }
                                    } catch (e: Throwable) {
                                        Result.failure(e)
                                    }

                                    mainHandler.post {
                                        if (sendResult.isSuccess) {
                                            val txItem = sendResult.getOrThrow()
                                            val amtDouble = amtStr.toDoubleOrNull() ?: 1.0
                                            viewModel.recordAndPinToken(
                                                tick = tick,
                                                op = op.uppercase(),
                                                amount = amtDouble,
                                                txId = txItem.txId,
                                                rawJson = paramsObj.toString(2)
                                            )
                                            viewModel.refreshTestnetWallet()
                                            respond(
                                                JSONObject()
                                                    .put("commitTxId", txItem.txId)
                                                    .put("revealTxId", txItem.txId)
                                                    .put("transactionId", txItem.txId)
                                                    .put("status", "accepted")
                                                    .put("tick", tick)
                                                    .put("op", op)
                                                    .put("amt", amtStr)
                                            )
                                        } else {
                                            respond(
                                                errorCode = -32000,
                                                errorMessage = sendResult.exceptionOrNull()?.message ?: "Real on-chain transaction failed on Kaspa BlockDAG"
                                            )
                                        }
                                    }
                                }
                            },
                            onReject = {
                                viewModel.dappPromptState.value = null
                                respond(errorCode = 4001, errorMessage = "User rejected $protocolLabel transaction")
                            }
                        )
                    }
                }

                    "sendKaspa", "sendTransaction" -> {
                        if (!isConnected) {
                            respond(errorCode = 4100, errorMessage = "Connect this site first")
                            return@addWebMessageListener
                        }
                        val keyPair = currentKeyPair()
                        val active = account
                        if (keyPair == null || active == null) {
                            respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                            return@addWebMessageListener
                        }
                        val paramsObj = when (params) {
                            is JSONObject -> params
                            is JSONArray -> {
                                val obj = JSONObject()
                                obj.put("to", params.optString(0))
                                obj.put("amount", params.opt(1))
                                obj
                            }
                            else -> JSONObject()
                        }
                        val toAddress = paramsObj.optString("to", paramsObj.optString("address", paramsObj.optString("recipient", ""))).trim()
                        val rawSompi = paramsObj.opt("amountSompi")
                        val rawAmount = if (rawSompi != null) rawSompi else paramsObj.opt("amount")
                        val amountKas: Double = when (rawAmount) {
                            is Number -> {
                                val d = rawAmount.toDouble()
                                if (d >= 10_000.0) d / 100_000_000.0 else d
                            }
                            is String -> {
                                val d = rawAmount.toDoubleOrNull() ?: 0.0
                                if (d >= 10_000.0) d / 100_000_000.0 else d
                            }
                            else -> 0.0
                        }

                        if (toAddress.isBlank() || amountKas <= 0.0) {
                            respond(errorCode = -32602, errorMessage = "Invalid recipient address or amount")
                            return@addWebMessageListener
                        }

                        mainHandler.post {
                            viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.SignTransaction(
                                origin = origin,
                                network = network,
                                accountAddress = keyPair.kaspaAddress,
                                title = "Send Kaspa",
                                action = "Send KAS Transaction",
                                tick = "KAS",
                                amount = "$amountKas",
                                destination = toAddress,
                                inputsCount = 1,
                                feeKas = "0.0001 KAS",
                                totalAmountKas = "$amountKas KAS",
                                onApprove = {
                                    viewModel.dappPromptState.value = null
                                    cryptoExecutor.execute {
                                        val sendResult = try {
                                            kotlinx.coroutines.runBlocking {
                                                viewModel.walletService.sendKaspa(
                                                    senderAddress = keyPair.kaspaAddress,
                                                    senderSeed = active.seedPhrase,
                                                    recipientAddress = toAddress,
                                                    amountKas = amountKas
                                                )
                                            }
                                        } catch (e: Throwable) {
                                            Result.failure(e)
                                        }
                                        mainHandler.post {
                                            if (sendResult.isSuccess) {
                                                val txItem = sendResult.getOrThrow()
                                                viewModel.refreshTestnetWallet()
                                                respond(txItem.txId)
                                            } else {
                                                respond(errorCode = -32000, errorMessage = sendResult.exceptionOrNull()?.message ?: "Broadcast failed")
                                            }
                                        }
                                    }
                                },
                                onReject = {
                                    viewModel.dappPromptState.value = null
                                    respond(errorCode = 4001, errorMessage = "User rejected transaction")
                                }
                            )
                        }
                    }

                    "submitTransaction", "pushTx" -> {
                        if (!isConnected) {
                            respond(errorCode = 4100, errorMessage = "Connect this site first")
                            return@addWebMessageListener
                        }
                        val keyPair = currentKeyPair()
                        if (keyPair == null) {
                            respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                            return@addWebMessageListener
                        }
                        val txJson = when (params) {
                            is String -> params
                            is JSONObject -> params.optString("txJsonString", params.optString("transaction", ""))
                            else -> ""
                        }
                        if (txJson.isBlank()) {
                            respond(errorCode = -32602, errorMessage = "A signed transaction JSON string is required")
                            return@addWebMessageListener
                        }
                        cryptoExecutor.execute {
                            val prepared = try {
                                DappPsktTransaction.inspectForSubmission(txJson)
                            } catch (error: Throwable) {
                                mainHandler.post {
                                    respond(errorCode = -32602, errorMessage = error.message ?: "Transaction validation failed")
                                }
                                return@execute
                            }
                            mainHandler.post {
                                val firstOutput = prepared.transaction.outputs.firstOrNull()
                                val outKas = com.example.network.kaspa.KaspaTransactionEngine.formatKas(
                                    com.example.network.kaspa.KaspaTransactionEngine.sompiToKas(prepared.outputSumSompi)
                                )
                                viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.SignTransaction(
                                    origin = origin,
                                    network = network,
                                    accountAddress = keyPair.kaspaAddress,
                                    title = "Submit Kaspa Transaction",
                                    action = "Broadcast Raw Transaction",
                                    tick = "KAS",
                                    amount = outKas,
                                    destination = firstOutput?.scriptPublicKey?.script?.take(22).orEmpty(),
                                    inputsCount = prepared.signInputs.size,
                                    feeKas = prepared.feeKas,
                                    totalAmountKas = outKas,
                                    onApprove = {
                                        viewModel.dappPromptState.value = null
                                        cryptoExecutor.execute {
                                            val receipt = try {
                                                kotlinx.coroutines.runBlocking {
                                                    viewModel.walletService.submitDappTransaction(
                                                        prepared.transaction,
                                                        keyPair.kaspaAddress,
                                                        network
                                                    )
                                                }
                                            } catch (error: Throwable) {
                                                null
                                            }
                                            mainHandler.post {
                                                if (receipt == null) {
                                                    respond(errorCode = -32603, errorMessage = "Transaction submission failed")
                                                } else if (!receipt.accepted) {
                                                    respond(errorCode = -32000, errorMessage = receipt.error)
                                                } else {
                                                    viewModel.onSwapTransactionSigned(txJson)
                                                    viewModel.triggerPostSwapRefresh()
                                                    respond(receipt.transactionId)
                                                }
                                            }
                                        }
                                    },
                                    onReject = {
                                        viewModel.dappPromptState.value = null
                                        respond(errorCode = 4001, errorMessage = "User rejected transaction submission")
                                    }
                                )
                            }
                        }
                    }

                    "signPolicyTransaction" -> {
                        if (!isConnected) {
                            respond(errorCode = 4100, errorMessage = "Connect this site first")
                            return@addWebMessageListener
                        }
                        val keyPair = currentKeyPair()
                        if (keyPair == null) {
                            respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                            return@addWebMessageListener
                        }
                        val paramsObject = params as? JSONObject
                        val txJson = paramsObject?.optString("txJsonString", "").orEmpty()
                        val signInputIndexes = paramsObject?.optJSONArray("signInputIndexes")
                        val redeemScript = paramsObject?.optString("redeemScript", "").orEmpty()
                        if (txJson.isBlank() || signInputIndexes == null) {
                            respond(errorCode = -32602, errorMessage = "signPolicyTransaction requires txJsonString and signInputIndexes")
                            return@addWebMessageListener
                        }
                        cryptoExecutor.execute {
                            val signInputs = JSONArray()
                            for (i in 0 until signInputIndexes.length()) {
                                signInputs.put(JSONObject().put("index", signInputIndexes.optInt(i)).put("sighashType", 1))
                            }
                            val prepared = try {
                                DappPsktTransaction.prepareForSigning(txJson, signInputs, keyPair.publicKeyHex, isPolicy = true, redeemScript = redeemScript)
                            } catch (error: Throwable) {
                                mainHandler.post {
                                    respond(errorCode = -32602, errorMessage = error.message ?: "Policy transaction review failed")
                                }
                                return@execute
                            }
                            mainHandler.post {
                                val outKas = com.example.network.kaspa.KaspaTransactionEngine.formatKas(
                                    com.example.network.kaspa.KaspaTransactionEngine.sompiToKas(prepared.outputSumSompi)
                                )
                                viewModel.dappPromptState.value = com.example.viewmodel.DecentralViewModel.DappPromptState.SignTransaction(
                                    origin = origin,
                                    network = network,
                                    accountAddress = keyPair.kaspaAddress,
                                    title = "Review KasCoven Policy Transaction",
                                    action = "Sign KasCoven Policy Vault",
                                    tick = "KAS",
                                    amount = outKas,
                                    destination = "KasCoven Policy Contract",
                                    inputsCount = prepared.signInputs.size,
                                    feeKas = prepared.feeKas,
                                    totalAmountKas = outKas,
                                    onApprove = {
                                        viewModel.dappPromptState.value = null
                                        cryptoExecutor.execute {
                                            val signedJson = try {
                                                DappPsktTransaction.sign(prepared, keyPair.privateKey)
                                            } catch (error: Throwable) {
                                                null
                                            }
                                            mainHandler.post {
                                                if (signedJson == null) {
                                                    respond(errorCode = -32603, errorMessage = "Policy signing failed")
                                                } else {
                                                    val reviewHash = CryptoUtils.sha256(prepared.originalJson.toString())
                                                    val result = JSONObject()
                                                        .put("signedTxJson", signedJson)
                                                        .put("profile", if (txJson.contains("heartbeat", true)) "DMS_HEARTBEAT" else "VAULT_CREATE")
                                                        .put("reviewHash", reviewHash)
                                                    respond(result)
                                                }
                                            }
                                        }
                                    },
                                    onReject = {
                                        viewModel.dappPromptState.value = null
                                        respond(errorCode = 4001, errorMessage = "User rejected policy signature")
                                    }
                                )
                            }
                        }
                    }

                    "disconnect" -> {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().remove(origin).apply()
                        respond(JSONObject().put("disconnected", true))
                    }

                    else -> respond(errorCode = 4200, errorMessage = "Unsupported Kaspa wallet method")
                }
                    }
                    markListenerRegistered(webView)
                    Log.i(LOG_TAG, "Native message listener registered for WebView ${System.identityHashCode(webView)}")
                } catch (error: Throwable) {
                    Log.e(
                        LOG_TAG,
                        "Native message listener registration failed for WebView ${System.identityHashCode(webView)}: ${error.javaClass.name}: ${error.message}",
                        error
                    )
                }
            }

            val script = """
                (() => {
                  if (window.__kaspaBrowserProviderInstalled) return;
                  const nativeChannel = window.$BRIDGE_NAME || window.kaspaDappPort || window.kaspaBridgePort;
                  if (!nativeChannel || typeof nativeChannel.postMessage !== 'function') {
                    window.__kaspaBrowserProviderInstallWarning = 'native-channel-delayed';
                  } else {
                    try { delete window.__kaspaBrowserProviderInstallWarning; } catch (_) {}
                  }
                  try { delete window.__kaspaBrowserProviderInstallError; } catch (_) {}
                  window.__kaspaBrowserProviderInstalled = true;
                  const pending = new Map();
                  let nextId = 1;
                  const getChannel = () => window.$BRIDGE_NAME || window.kaspaDappPort || window.kaspaBridgePort;
                  const bindChannelListener = () => {
                    const ch = getChannel();
                    if (ch && typeof ch.addEventListener === 'function' && !window.__kaspaChannelBound) {
                      window.__kaspaChannelBound = true;
                      try {
                        ch.addEventListener('message', event => {
                          let response;
                          try { response = JSON.parse(event.data); } catch (_) { return; }
                          const entry = pending.get(String(response.id));
                          if (!entry) return;
                          pending.delete(String(response.id));
                          if (response.error) {
                            const error = new Error(response.error.message || 'Wallet request failed');
                            error.code = response.error.code;
                            entry.reject(error);
                          } else {
                            entry.resolve(response.result);
                          }
                        });
                      } catch (_) {}
                    }
                  };
                  bindChannelListener();

                  const sendMsg = (ch, msgObj, resolve, reject) => {
                    try {
                      ch.postMessage(JSON.stringify(msgObj));
                    } catch (err) {
                      reject(err);
                    }
                  };

                  const request = (method, params) => new Promise((resolve, reject) => {
                    const id = String(nextId++);
                    pending.set(id, { resolve, reject });
                    bindChannelListener();
                    const ch = getChannel();
                    if (ch && typeof ch.postMessage === 'function') {
                      sendMsg(ch, { id, method, params }, resolve, reject);
                    } else {
                      let attempts = 0;
                      const timer = setInterval(() => {
                        attempts++;
                        bindChannelListener();
                        const activeCh = getChannel();
                        if (activeCh && typeof activeCh.postMessage === 'function') {
                          clearInterval(timer);
                          sendMsg(activeCh, { id, method, params }, resolve, reject);
                        } else if (attempts >= 20) {
                          clearInterval(timer);
                          pending.delete(id);
                          reject(Object.assign(new Error('Wallet communication channel unavailable'), { code: -32603 }));
                        }
                      }, 50);
                    }
                  });
                  const listeners = new Map();
                  const emit = (event, value) => {
                    const group = listeners.get(event);
                    if (group) for (const listener of [...group]) {
                      try { listener(value); } catch (_) {}
                    }
                  };
                  const on = (event, listener) => {
                    if (typeof listener !== 'function') return provider;
                    if (!listeners.has(event)) listeners.set(event, new Set());
                    listeners.get(event).add(listener);
                    return provider;
                  };
                  const removeListener = (event, listener) => {
                    listeners.get(event)?.delete(listener);
                    return provider;
                  };
                  const provider = {
                    requestAccounts: async () => {
                      const accounts = await request('requestAccounts');
                      emit('accountsChanged', accounts);
                      return accounts;
                    },
                    getAccounts: async () => {
                      const accounts = await request('getAccounts');
                      emit('accountsChanged', accounts);
                      return accounts;
                    },
                    getNetwork: async () => {
                      const network = await request('getNetwork');
                      emit('chainChanged', network);
                      return network;
                    },
                    getPublicKey: () => request('getPublicKey'),
                    getBalance: () => request('getBalance'),
                    getUtxoEntries: () => request('getUtxoEntries'),
                    signMessage: message => request('signMessage', message),
                    signPskt: payload => request('signPskt', payload),
                    signKrc20Transaction: payload => request('signKrc20Transaction', payload),
                    signKcc20Transaction: payload => request('signKcc20Transaction', payload),
                    signKcc20: payload => request('signKcc20Transaction', payload),
                    sendKaspa: (to, amount) => request('sendKaspa', { to, amount }),
                    sendTransaction: payload => request('sendTransaction', payload),
                    submitTransaction: txJsonString => request('submitTransaction', txJsonString),
                    pushTx: txJsonString => request('pushTx', txJsonString),
                    signPolicyTransaction: payload => request('signPolicyTransaction', payload),
                    disconnect: async () => {
                      const result = await request('disconnect');
                      emit('accountsChanged', []);
                      emit('disconnect', result);
                      return result;
                    },
                    enable: async () => {
                      const accounts = await request('requestAccounts');
                      emit('accountsChanged', accounts);
                      return accounts;
                    },
                    on,
                    addListener: on,
                    removeListener,
                    off: removeListener
                  };
                  Object.freeze(provider);
                  const browserProvider = Object.freeze({
                    ...provider,
                    connect: async () => {
                      const accounts = await provider.requestAccounts();
                      return { address: accounts[0] || '', accounts };
                    }
                  });
                  const requestCompat = (firstArg, secondArg) => {
                    let method, params;
                    if (typeof firstArg === 'string') {
                      method = firstArg;
                      params = secondArg;
                    } else if (firstArg && typeof firstArg === 'object') {
                      method = firstArg.method;
                      params = firstArg.params;
                    }
                    if (typeof method !== 'string' || !method) {
                      return Promise.reject(Object.assign(new Error('A wallet method is required'), { code: -32600 }));
                    }
                    const aliases = {
                      'kaspa:connect': 'requestAccounts',
                      'kaspa:requestAccounts': 'requestAccounts',
                      'kaspa:getAccounts': 'getAccounts',
                      'kaspa:getNetwork': 'getNetwork',
                      'kaspa:getBalance': 'getBalance',
                      'kaspa:getUtxoEntries': 'getUtxoEntries',
                      'kaspa:getPublicKey': 'getPublicKey',
                      'kaspa:signMessage': 'signMessage',
                      'kaspa:signPskt': 'signPskt',
                      'kaspa:submitTransaction': 'submitTransaction',
                      'kaspa:disconnect': 'disconnect',
                      connect: 'requestAccounts',
                      enable: 'requestAccounts',
                      kaspa_requestAccounts: 'requestAccounts',
                      kaspa_getAccounts: 'getAccounts',
                      kaspa_getNetwork: 'getNetwork',
                      kaspa_getBalance: 'getBalance',
                      kaspa_getUtxoEntries: 'getUtxoEntries',
                      kaspa_getPublicKey: 'getPublicKey',
                      kaspa_signMessage: 'signMessage',
                      kaspa_signPskt: 'signPskt',
                      kaspa_signKrc20Transaction: 'signKrc20Transaction',
                      kasware_signKrc20Transaction: 'signKrc20Transaction',
                      kaspa_signKcc20Transaction: 'signKcc20Transaction',
                      kasware_signKcc20Transaction: 'signKcc20Transaction',
                      signKrc20: 'signKrc20Transaction',
                      signKrc20Transaction: 'signKrc20Transaction',
                      signKcc20: 'signKcc20Transaction',
                      signKcc20Transaction: 'signKcc20Transaction',
                      kaspa_sendKaspa: 'sendKaspa',
                      kasware_sendKaspa: 'sendKaspa',
                      sendKaspa: 'sendKaspa',
                      kaspa_sendTransaction: 'sendTransaction',
                      sendTransaction: 'sendTransaction',
                      kaspa_submitTransaction: 'submitTransaction',
                      kasperia_requestAccounts: 'requestAccounts',
                      kasperia_accounts: 'getAccounts',
                      kasperia_getNetwork: 'getNetwork',
                      kasperia_getBalance: 'getBalance',
                      kasperia_getUtxoEntries: 'getUtxoEntries',
                      kasperia_getPublicKey: 'getPublicKey',
                      kasperia_signMessage: 'signMessage',
                      kasperia_signPskt: 'signPskt',
                      kasperia_signKrc20Transaction: 'signKrc20Transaction',
                      kasperia_signKcc20Transaction: 'signKcc20Transaction',
                      kasperia_sendKaspa: 'sendKaspa',
                      kasperia_sendTransaction: 'sendTransaction',
                      kasperia_submitTransaction: 'submitTransaction',
                      kasperia_disconnect: 'disconnect',
                      kaspire_requestAccounts: 'requestAccounts',
                      kaspire_getAccounts: 'getAccounts',
                      kaspire_getNetwork: 'getNetwork',
                      kaspire_getBalance: 'getBalance',
                      kaspire_getUtxoEntries: 'getUtxoEntries',
                      kaspire_getPublicKey: 'getPublicKey',
                      kaspire_signMessage: 'signMessage',
                      kaspire_signPskt: 'signPskt',
                      kaspire_signKrc20Transaction: 'signKrc20Transaction',
                      kaspire_signKcc20Transaction: 'signKcc20Transaction',
                      kaspire_sendKaspa: 'sendKaspa',
                      kaspire_sendTransaction: 'sendTransaction',
                      kaspire_submitTransaction: 'submitTransaction',
                      kaspire_disconnect: 'disconnect',
                      kaspabrowser_requestAccounts: 'requestAccounts',
                      kaspabrowser_getAccounts: 'getAccounts',
                      kaspabrowser_getNetwork: 'getNetwork',
                      kaspabrowser_getBalance: 'getBalance',
                      kaspabrowser_getUtxoEntries: 'getUtxoEntries',
                      kaspabrowser_getPublicKey: 'getPublicKey',
                      kaspabrowser_signMessage: 'signMessage',
                      kaspabrowser_signPskt: 'signPskt',
                      kaspabrowser_signKrc20Transaction: 'signKrc20Transaction',
                      kaspabrowser_signKcc20Transaction: 'signKcc20Transaction',
                      kaspabrowser_sendKaspa: 'sendKaspa',
                      kaspabrowser_sendTransaction: 'sendTransaction',
                      kaspabrowser_submitTransaction: 'submitTransaction',
                      kaspabrowser_disconnect: 'disconnect',
                      pushTx: 'pushTx',
                      signPolicyTransaction: 'signPolicyTransaction'
                    };
                    const normalized = aliases[method] || method;
                    const value = Array.isArray(params) ? params[0] : params;
                    switch (normalized) {
                      case 'requestAccounts': return request('requestAccounts');
                      case 'getAccounts': return request('getAccounts');
                      case 'getNetwork': return request('getNetwork');
                      case 'getBalance': return request('getBalance');
                      case 'getUtxoEntries': return request('getUtxoEntries');
                      case 'getPublicKey': return request('getPublicKey');
                      case 'signMessage': {
                        const message = value && typeof value === 'object'
                          ? (value.message !== undefined ? value.message : value.msg)
                          : value;
                        return request('signMessage', message);
                      }
                      case 'signPskt': {
                        const payload = (params && typeof params === 'object') ? params : (value && typeof value === 'object' ? value : {});
                        const txJson = payload.txJsonString || payload.psktTransactionJson || payload.signedTxJson || '';
                        const options = payload.options || { signInputs: payload.signInputs || payload.signInputIndexes || [] };
                        const signInputs = options.signInputs || payload.signInputs;
                        if (typeof txJson !== 'string' || !txJson || !Array.isArray(signInputs)) {
                          return Promise.reject(Object.assign(new Error('signPskt requires txJsonString and options.signInputs'), { code: -32602 }));
                        }
                        return request('signPskt', {
                          txJsonString: txJson,
                          options: { ...options, signInputs },
                          sender: payload.sender
                        });
                      }
                      case 'signKrc20Transaction': return request('signKrc20Transaction', value);
                      case 'signKcc20Transaction': return request('signKcc20Transaction', value);
                      case 'sendKaspa': {
                        const to = value && typeof value === 'object' ? (value.to || value.address || value.recipient) : (Array.isArray(params) ? params[0] : '');
                        const amount = value && typeof value === 'object' ? (value.amount !== undefined ? value.amount : value.sompi) : (Array.isArray(params) ? params[1] : 0);
                        return request('sendKaspa', { to, amount });
                      }
                      case 'sendTransaction': {
                        const to = value && typeof value === 'object' ? (value.to || value.address || value.recipient) : (Array.isArray(params) ? params[0] : '');
                        const amount = value && typeof value === 'object' ? (value.amount !== undefined ? value.amount : value.sompi) : (Array.isArray(params) ? params[1] : 0);
                        return request('sendTransaction', { to, amount });
                      }
                      case 'disconnect': return request('disconnect');
                      case 'signPolicyTransaction': {
                        const payload = value && typeof value === 'object' ? value : {};
                        if (typeof payload.txJsonString !== 'string' || !Array.isArray(payload.signInputIndexes)) {
                          return Promise.reject(Object.assign(new Error('signPolicyTransaction requires txJsonString and signInputIndexes'), { code: -32602 }));
                        }
                        return request('signPolicyTransaction', payload);
                      }
                      case 'pushTx':
                      case 'submitTransaction': {
                        const txJsonString = typeof value === 'string'
                          ? value
                          : value && typeof value === 'object'
                            ? (value.txJsonString || value.transaction || value.signedTxJson || '')
                            : '';
                        if (!txJsonString) {
                          return Promise.reject(Object.assign(new Error('A signed transaction JSON string is required'), { code: -32602 }));
                        }
                        return request('submitTransaction', txJsonString);
                      }
                      default:
                        return request(normalized, value !== undefined ? value : params);
                    }
                  };
                  const requestProvider = Object.freeze({
                    ...browserProvider,
                    request: requestCompat
                  });
                  const installAlias = (name, value) => {
                    try {
                      if (!(name in window)) {
                        Object.defineProperty(window, name, {
                          configurable: false, enumerable: true, writable: false, value
                        });
                      }
                    } catch (_) {}
                  };
                  const kaspaBrowserProvider = Object.freeze({
                    ...requestProvider,
                    id: 'org.kaspa.browser',
                    name: 'Kaspa Browser',
                    isKaspabrowser: true,
                    isKaspaBrowser: true,
                    isKasware: true,
                    isKastle: true,
                    isKaspire: true,
                    isKaspa: true,
                    isInstalled: true,
                    type: 'extension',
                    version: '1.2.0',
                    request: requestCompat,
                    requestAccounts: () => requestCompat({ method: 'requestAccounts' }),
                    getAccounts: () => requestCompat({ method: 'getAccounts' }),
                    getNetwork: () => requestCompat({ method: 'getNetwork' }),
                    getPublicKey: () => requestCompat({ method: 'getPublicKey' }),
                    getBalance: () => requestCompat({ method: 'getBalance' }),
                    signPskt: payload => requestCompat({ method: 'signPskt', params: payload }),
                    signKrc20Transaction: payload => requestCompat({ method: 'signKrc20Transaction', params: payload }),
                    signKcc20Transaction: payload => requestCompat({ method: 'signKcc20Transaction', params: payload }),
                    sendKaspa: (to, amount) => requestCompat({ method: 'sendKaspa', params: [to, amount] }),
                    sendTransaction: payload => requestCompat({ method: 'sendTransaction', params: payload }),
                    disconnect: () => requestCompat({ method: 'disconnect' })
                  });
                  installAlias('kastle', Object.freeze({
                    ...requestProvider,
                    isKastle: true,
                    isInstalled: true,
                    isKaspa: true,
                    version: '1.2.0',
                    signPskt: payload => requestCompat({ method: 'signPskt', params: payload }),
                    signKrc20Transaction: payload => requestCompat({ method: 'signKrc20Transaction', params: payload }),
                    signKcc20Transaction: payload => requestCompat({ method: 'signKcc20Transaction', params: payload }),
                    sendKaspa: (to, amount) => requestCompat({ method: 'sendKaspa', params: [to, amount] }),
                    sendTransaction: payload => requestCompat({ method: 'sendTransaction', params: payload })
                  }));
                  installAlias('kasware', Object.freeze({
                    ...requestProvider,
                    isKasware: true,
                    isInstalled: true,
                    isKaspa: true,
                    version: '1.2.0',
                    getNetwork: () => requestCompat({ method: 'kaspa:getNetwork' }),
                    getAccounts: () => requestCompat({ method: 'kaspa:getAccounts' }),
                    requestAccounts: () => requestCompat({ method: 'kaspa:requestAccounts' }),
                    getBalance: () => requestCompat({ method: 'kaspa:getBalance' }),
                    getPublicKey: () => requestCompat({ method: 'kaspa:getPublicKey' }),
                    signPskt: payload => requestCompat({ method: 'signPskt', params: payload }),
                    signKrc20Transaction: payload => requestCompat({ method: 'signKrc20Transaction', params: payload }),
                    signKcc20Transaction: payload => requestCompat({ method: 'signKcc20Transaction', params: payload }),
                    sendKaspa: (to, amount) => requestCompat({ method: 'sendKaspa', params: [to, amount] }),
                    sendTransaction: payload => requestCompat({ method: 'sendTransaction', params: payload })
                  }));
                  const kasperiaProvider = {
                    ...requestProvider,
                    isKasperia: true,
                    isInstalled: true,
                    isKaspa: true
                  };
                  Object.defineProperty(kasperiaProvider, 'ethereum', {
                    configurable: false,
                    enumerable: true,
                    get: () => window.ethereum
                  });
                  Object.freeze(kasperiaProvider);
                  installAlias('kasperia', kasperiaProvider);
                  const kaspireProvider = Object.freeze({
                    ...kaspaBrowserProvider,
                    id: 'kaspire',
                    name: 'Kaspire',
                    provider: 'kaspire',
                    isKaspire: true,
                    isKaspabrowser: true,
                    isKaspaBrowser: true,
                    isInstalled: true,
                    isKaspa: true,
                    type: 'extension',
                    version: '1.2.5'
                  });
                  window.kaspire = kaspireProvider;
                  window.isKaspire = true;
                  installAlias('kaspire', kaspireProvider);

                  window.kaspabrowser = kaspaBrowserProvider;
                  window.kaspabrowserProvider = kaspaBrowserProvider;
                  try {
                    Object.defineProperty(window, 'kaspabrowser', {
                      configurable: true, enumerable: true, writable: true, value: kaspaBrowserProvider
                    });
                    Object.defineProperty(window, 'kaspabrowserProvider', {
                      configurable: true, enumerable: true, writable: true, value: kaspaBrowserProvider
                    });
                    Object.defineProperty(window, 'kaspa', {
                      configurable: true, enumerable: true, writable: true, value: kaspaBrowserProvider
                    });
                    Object.defineProperty(window, 'kaspaBrowser', {
                      configurable: true, enumerable: true, writable: true, value: browserProvider
                    });
                  } catch (_) {}

                  window.isKasware = true;
                  window.isKaspa = true;

                  const walletNamespace = window.wallet && typeof window.wallet === 'object' ? window.wallet : {};
                  if (!Object.prototype.hasOwnProperty.call(walletNamespace, 'kaspa')) {
                    try {
                      Object.defineProperty(walletNamespace, 'kaspa', {
                        configurable: false, enumerable: true, writable: false, value: browserProvider
                      });
                      if (window.wallet !== walletNamespace) {
                        Object.defineProperty(window, 'wallet', {
                          configurable: true, enumerable: true, writable: false, value: walletNamespace
                        });
                      }
                    } catch (_) {}
                  }
                  const info = Object.freeze({
                    id: 'org.kaspa.browser',
                    name: 'Kaspa Browser',
                    icon: '',
                    methods: ['kaspa:requestAccounts', 'kaspa:getAccounts', 'kaspa:getNetwork', 'kaspa:getBalance', 'kaspa:getUtxoEntries', 'kaspa:getPublicKey', 'kaspa:signMessage', 'kaspa:signPskt', 'kaspa:submitTransaction', 'kaspa:disconnect'],
                    uuid: 'UUID_PLACEHOLDER',
                    rdns: 'org.kaspa.browser'
                  });
                  const announce = () => {
                    const detail = Object.freeze({ info, provider: kaspaBrowserProvider });
                    window.dispatchEvent(new CustomEvent('kaspa:provider', { detail }));
                    window.dispatchEvent(new CustomEvent('kaspire:provider', { detail: Object.freeze({ info: { ...info, id: 'kaspire', name: 'Kaspire', rdns: 'io.kaspire' }, provider: kaspireProvider }) }));
                    window.dispatchEvent(new CustomEvent('kasware:provider', { detail: Object.freeze({ info: { ...info, id: 'kasware', name: 'Kasware', rdns: 'com.kasware.wallet' }, provider: window.kasware }) }));
                  };
                  window.addEventListener('kaspa:requestProvider', announce);
                  window.addEventListener('kaspabrowser#requestProvider', announce);
                  window.addEventListener('kaspire:requestProvider', announce);
                  window.addEventListener('kasware:requestProvider', announce);
                  announce();
                  window.dispatchEvent(new Event('kaspabrowser#initialized'));
                  window.dispatchEvent(new Event('kasperia#initialized'));
                  window.dispatchEvent(new Event('kaspire#initialized'));
                  window.dispatchEvent(new Event('kasware#initialized'));
                })();
            """.trimIndent().replace("UUID_PLACEHOLDER", UUID.randomUUID().toString())

            if (!isInstalled(webView)) {
                try {
                    WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
                    Log.i(LOG_TAG, "Provider document-start script registered for WebView ${System.identityHashCode(webView)}")
                    markInstalled(webView)
                } catch (error: Throwable) {
                    Log.e(
                        LOG_TAG,
                        "Provider document-start script registration failed for WebView ${System.identityHashCode(webView)}: ${error.javaClass.name}: ${error.message}",
                        error
                    )
                    throw error
                }
            }
            webView.post {
                try {
                    webView.evaluateJavascript(script, null)
                } catch (error: Throwable) {
                    Log.e(
                        LOG_TAG,
                        "Current-page provider injection failed for WebView ${System.identityHashCode(webView)}: ${error.javaClass.name}: ${error.message}",
                        error
                    )
                }
            }
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "Provider installation failed while configuring WebView messaging or script injection", error)
        }
    }

    fun injectProviderScript(webView: WebView) {
        try {
            webView.post {
                try {
                    webView.evaluateJavascript("if (typeof window.kaspabrowser === 'undefined') { window.dispatchEvent(new Event('kaspabrowser#requestProvider')); }", null)
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    private fun networkId(network: String): String = when {
        network.equals("Kaspa Mainnet", ignoreCase = true) -> "mainnet"
        network.equals("Kaspa Testnet 10", ignoreCase = true) -> "testnet-10"
        network.equals("Kaspa Testnet 11", ignoreCase = true) -> "testnet-11"
        else -> "unsupported"
    }

    private fun normalizeOrigin(value: String): String? {
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return null
        }
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null) return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port
        if (port == 0 || port > 65535 || port < -1) return null
        val normalizedHost = host.lowercase(Locale.ROOT)
        return if (port == -1 || port == 443) "https://$normalizedHost" else "https://$normalizedHost:$port"
    }
}

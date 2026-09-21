package com.example.network

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.data.AccountEntity
import com.example.network.kaspa.DotkProtocol
import com.example.network.kaspa.KaspaTransactionEngine
import com.example.viewmodel.DAppApprovalRequest
import com.example.viewmodel.DecentralViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Universal Kaspa Web3 DApp & Wallet Provider Bridge (window.kasware, window.kaspire, window.kaspa).
 * Provides full support for standard Kaspa dApp methods:
 * - isInstalled(), requestAccounts(), getAccounts(), getPublicKey(), getBalance(), getNetwork(), switchNetwork()
 * - signPskt(), signTransaction(), signKCC20Transaction(), sendKaspa(), signMessage(), pushTx()
 * - Standard event emitter: on(), removeListener(), addListener(), off()
 */
class KaspaWalletBridge(
    private val context: Context,
    private val webViewProvider: () -> WebView?,
    private val viewModel: DecentralViewModel,
    private val scope: CoroutineScope
) {
    private val tag = "KaspaWalletBridge"
    private val okHttpClient = CronetClientFactory.buildClient(
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
    )

    private fun getActiveAccount(): AccountEntity? {
        val current = viewModel.activeAccount.value
        if (current != null) return current
        val all = viewModel.allAccounts.value
        if (all.isNotEmpty()) {
            val first = all.first()
            viewModel.switchAccount(first.did)
            return first
        }
        val newAcc = CryptoUtils.deriveDecentralizedAccount(
            customHandle = "Kaspa Primary",
            seedMnemonic = null
        ).copy(isActive = true)
        viewModel.insertAccount(newAcc)
        return newAcc
    }

    private fun getSeedPhrase(account: AccountEntity): String? {
        return try {
            CryptoUtils.getDecryptedSeed(account.seedPhrase)
        } catch (e: Exception) {
            Log.e(tag, "Failed to decrypt wallet seed phrase: ${e.message}", e)
            null
        }
    }

    @JavascriptInterface
    fun isInstalled(): Boolean {
        return true
    }

    @JavascriptInterface
    fun requestAccounts(callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                if (account == null) {
                    val errorMsg = "No active Kaspa account found in wallet. Please create or import a Kaspa wallet in the app."
                    withContext(Dispatchers.Main) {
                        viewModel.setStatusMessage("DApp connection request: No active Kaspa wallet found.")
                    }
                    rejectCallback(callbackId, errorMsg)
                    return@launch
                }

                val currentUrl = withContext(Dispatchers.Main) {
                    webViewProvider()?.url ?: "Kaspa Web3 DApp"
                }

                withContext(Dispatchers.Main) {
                    val req = DAppApprovalRequest.Connect(
                        id = callbackId,
                        origin = currentUrl,
                        accountAddress = account.kaspaAddress,
                        balanceKas = viewModel.kaspaWalletState.value.balanceKas,
                        onApprove = {
                            scope.launch(Dispatchers.IO) {
                                val accountsArray = JSONArray().apply { put(account.kaspaAddress) }
                                withContext(Dispatchers.Main) {
                                    viewModel.setStatusMessage("DApp connected to Kaspa account: ${account.kaspaAddress.take(18)}...")
                                    viewModel.clearDAppRequest()
                                }
                                resolveCallbackJson(callbackId, accountsArray.toString())
                            }
                        },
                        onReject = { reason ->
                            scope.launch(Dispatchers.IO) {
                                withContext(Dispatchers.Main) {
                                    viewModel.setStatusMessage("DApp connection rejected")
                                    viewModel.clearDAppRequest()
                                }
                                rejectCallback(callbackId, reason)
                            }
                        }
                    )
                    viewModel.submitDAppRequest(req)
                }
            } catch (e: Exception) {
                Log.e(tag, "requestAccounts error: ${e.message}", e)
                rejectCallback(callbackId, e.message ?: "Failed to connect account")
            }
        }
    }

    @JavascriptInterface
    fun getAccounts(callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                val accountsArray = JSONArray()
                if (account != null) {
                    accountsArray.put(account.kaspaAddress)
                }
                resolveCallbackJson(callbackId, accountsArray.toString())
            } catch (e: Exception) {
                Log.e(tag, "getAccounts error: ${e.message}", e)
                rejectCallback(callbackId, e.message ?: "Failed to get accounts")
            }
        }
    }

    @JavascriptInterface
    fun getPublicKey(callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                if (account == null) {
                    rejectCallback(callbackId, "No active Kaspa account")
                    return@launch
                }
                resolveCallbackString(callbackId, account.publicKeyHex)
            } catch (e: Exception) {
                Log.e(tag, "getPublicKey error: ${e.message}", e)
                rejectCallback(callbackId, e.message ?: "Failed to get public key")
            }
        }
    }

    @JavascriptInterface
    fun getBalance(callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                if (account == null) {
                    rejectCallback(callbackId, "No active Kaspa account")
                    return@launch
                }
                val state = viewModel.kaspaWalletState.value
                val sompiBalance = state.balanceSompis
                val res = JSONObject().apply {
                    put("confirmed", sompiBalance)
                    put("unconfirmed", 0L)
                    put("total", sompiBalance)
                }
                resolveCallbackJson(callbackId, res.toString())
            } catch (e: Exception) {
                Log.e(tag, "getBalance error: ${e.message}", e)
                rejectCallback(callbackId, e.message ?: "Failed to get balance")
            }
        }
    }

    @JavascriptInterface
    fun getNetwork(callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                val isTestnet = account?.kaspaAddress?.startsWith("kaspatest:") == true
                val net = if (isTestnet) "kaspa_testnet_10" else "kaspa_mainnet"
                resolveCallbackString(callbackId, net)
            } catch (e: Exception) {
                Log.e(tag, "getNetwork error: ${e.message}", e)
                rejectCallback(callbackId, e.message ?: "Failed to get network")
            }
        }
    }

    @JavascriptInterface
    fun switchNetwork(networkName: String, callbackId: String) {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                viewModel.setStatusMessage("DApp switched network to $networkName")
                val script = "window.__kaswareSyncNetwork && window.__kaswareSyncNetwork('$networkName');"
                webViewProvider()?.evaluateJavascript(script, null)
            }
            resolveCallbackJson(callbackId, "true")
        }
    }

    @JavascriptInterface
    fun disconnect(origin: String, callbackId: String) {
        scope.launch(Dispatchers.IO) {
            resolveCallbackJson(callbackId, "true")
        }
    }

    @JavascriptInterface
    fun getKRC20Balance(ticker: String, callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                if (account == null) {
                    rejectCallback(callbackId, "No active Kaspa account")
                    return@launch
                }
                val isTestnet = account.kaspaAddress.startsWith("kaspatest:")
                val baseUrl = if (isTestnet) "https://tn10api.kasplex.org/v1/krc20" else "https://api.kasplex.org/v1/krc20"
                val cleanTicker = ticker.trim().uppercase()
                val url = "$baseUrl/address/${account.kaspaAddress}/token/$cleanTicker"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "KaspaBrowser/1.0 (Android; Mobile)")
                    .header("Accept", "application/json")
                    .build()

                val responseStr = okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                }

                if (responseStr != null) {
                    val json = JSONObject(responseStr)
                    val resultArr = json.optJSONArray("result")
                    if (resultArr != null && resultArr.length() > 0) {
                        val tokenObj = resultArr.getJSONObject(0)
                        val res = JSONObject().apply {
                            put("ticker", tokenObj.optString("ticker", cleanTicker))
                            put("balance", tokenObj.optString("balance", "0"))
                            put("locked", tokenObj.optString("locked", "0"))
                            put("dec", tokenObj.optString("dec", "8"))
                        }
                        resolveCallbackJson(callbackId, res.toString())
                        return@launch
                    }
                }
                val fallback = JSONObject().apply {
                    put("ticker", cleanTicker)
                    put("balance", "0")
                    put("locked", "0")
                    put("dec", "8")
                }
                resolveCallbackJson(callbackId, fallback.toString())
            } catch (e: Exception) {
                Log.e(tag, "getKRC20Balance error: ${e.message}", e)
                val fallback = JSONObject().apply {
                    put("ticker", ticker.uppercase())
                    put("balance", "0")
                    put("locked", "0")
                    put("dec", "8")
                }
                resolveCallbackJson(callbackId, fallback.toString())
            }
        }
    }

    @JavascriptInterface
    fun getKRC20TokenList(callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                if (account == null) {
                    resolveCallbackJson(callbackId, "[]")
                    return@launch
                }
                val isTestnet = account.kaspaAddress.startsWith("kaspatest:")
                val baseUrl = if (isTestnet) "https://tn10api.kasplex.org/v1/krc20" else "https://api.kasplex.org/v1/krc20"
                val url = "$baseUrl/address/${account.kaspaAddress}/tokenlist"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "KaspaBrowser/1.0 (Android; Mobile)")
                    .header("Accept", "application/json")
                    .build()

                val responseStr = okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                }

                if (responseStr != null) {
                    val json = JSONObject(responseStr)
                    val resultArr = json.optJSONArray("result")
                    if (resultArr != null) {
                        resolveCallbackJson(callbackId, resultArr.toString())
                        return@launch
                    }
                }
                resolveCallbackJson(callbackId, "[]")
            } catch (e: Exception) {
                Log.e(tag, "getKRC20TokenList error: ${e.message}", e)
                resolveCallbackJson(callbackId, "[]")
            }
        }
    }

    @JavascriptInterface
    fun getKRC20TokenInfo(ticker: String, callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                val isTestnet = account?.kaspaAddress?.startsWith("kaspatest:") == true
                val baseUrl = if (isTestnet) "https://tn10api.kasplex.org/v1/krc20" else "https://api.kasplex.org/v1/krc20"
                val cleanTicker = ticker.trim().uppercase()
                val url = "$baseUrl/token/$cleanTicker"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "KaspaBrowser/1.0 (Android; Mobile)")
                    .header("Accept", "application/json")
                    .build()

                val responseStr = okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                }

                if (responseStr != null) {
                    val json = JSONObject(responseStr)
                    val resultArr = json.optJSONArray("result")
                    if (resultArr != null && resultArr.length() > 0) {
                        resolveCallbackJson(callbackId, resultArr.getJSONObject(0).toString())
                        return@launch
                    }
                }
                rejectCallback(callbackId, "Token info not found for $cleanTicker")
            } catch (e: Exception) {
                Log.e(tag, "getKRC20TokenInfo error: ${e.message}", e)
                rejectCallback(callbackId, e.message ?: "Failed to fetch token info")
            }
        }
    }

    @JavascriptInterface
    fun getUtxoEntries(addressParam: String, callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                val addr = if (addressParam.isNotBlank()) addressParam else (account?.kaspaAddress ?: "")
                if (addr.isBlank()) {
                    resolveCallbackJson(callbackId, "[]")
                    return@launch
                }
                val isTestnet = addr.startsWith("kaspatest:")
                val baseUrl = if (isTestnet) "https://api-tn10.kaspa.org" else "https://api.kaspa.org"
                val url = "$baseUrl/addresses/$addr/utxos"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "KaspaBrowser/1.0 (Android; Mobile)")
                    .header("Accept", "application/json")
                    .build()

                val responseStr = okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                }

                if (responseStr != null) {
                    resolveCallbackJson(callbackId, responseStr)
                } else {
                    resolveCallbackJson(callbackId, "[]")
                }
            } catch (e: Exception) {
                Log.e(tag, "getUtxoEntries error: ${e.message}", e)
                resolveCallbackJson(callbackId, "[]")
            }
        }
    }

    /**
     * Signs transaction inputs (PSKT / Kaspa Transaction standard).
     * Strictly compatible with dotk.name, KasWare, Kaspire, and KCC-20 on L1 dApp requests.
     */
    @JavascriptInterface
    fun signPskt(requestJsonStr: String, callbackId: String) {
        signTransactionInternal(requestJsonStr, callbackId)
    }

    @JavascriptInterface
    fun signTransaction(requestJsonStr: String, callbackId: String) {
        signTransactionInternal(requestJsonStr, callbackId)
    }

    private fun signTransactionInternal(requestJsonStr: String, callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                if (account == null) {
                    val msg = "No active wallet account to sign transaction. Please create or unlock wallet."
                    withContext(Dispatchers.Main) { viewModel.setStatusMessage("Signing failed: $msg") }
                    rejectCallback(callbackId, msg)
                    return@launch
                }

                val seed = getSeedPhrase(account)
                if (seed.isNullOrBlank()) {
                    val msg = "Cannot decrypt wallet private key for signing."
                    withContext(Dispatchers.Main) { viewModel.setStatusMessage("Signing failed: $msg") }
                    rejectCallback(callbackId, msg)
                    return@launch
                }

                val keyPair = CryptoUtils.deriveKaspaKeyPair(seed)
                val privateKey = keyPair.privateKey

                // Parse the transaction request object
                val (rawTxJson, signInputIndices) = parseTransactionRequest(requestJsonStr)

                val tx = parseKaspaTransactionFromJson(rawTxJson)
                val liveUtxos: List<KaspaTransactionEngine.KaspaUtxo> = try {
                    viewModel.walletService.fetchLiveUtxos(account.kaspaAddress)
                } catch (_: Exception) {
                    emptyList()
                }

                // Prepare UTXO entries for each input
                val utxoEntries = mutableListOf<KaspaTransactionEngine.KaspaUtxoEntry>()
                val senderSpk = KaspaTransactionEngine.decodeAddressToScriptPublicKey(account.kaspaAddress)

                for (i in tx.inputs.indices) {
                    val input = tx.inputs[i]
                    val parsedEntry = parseUtxoEntryFromInput(rawTxJson, i)
                    if (parsedEntry != null) {
                        utxoEntries.add(parsedEntry)
                    } else {
                        // Find match in live UTXOs
                        val matched = liveUtxos.firstOrNull { u ->
                            u.outpoint.transactionId.equals(input.previousOutpoint.transactionId, ignoreCase = true) &&
                            u.outpoint.index == input.previousOutpoint.index
                        }
                        if (matched != null) {
                            utxoEntries.add(matched.utxoEntry)
                        } else {
                            // Fallback default UTXO entry matching sender
                            utxoEntries.add(
                                KaspaTransactionEngine.KaspaUtxoEntry(
                                    amount = 100_000_000L,
                                    scriptPublicKey = senderSpk,
                                    blockDaaScore = 0L,
                                    isCoinbase = false
                                )
                            )
                        }
                    }
                }

                // Perform Schnorr signature on requested inputs after approval prompt
                val indicesToSign = if (signInputIndices.isNotEmpty()) {
                    signInputIndices.filter { it in tx.inputs.indices }
                } else {
                    tx.inputs.indices.filter { tx.inputs[it].signatureScript.isEmpty() }
                }

                val currentUrl = withContext(Dispatchers.Main) {
                    webViewProvider()?.url ?: "Kaspa Web3 DApp"
                }

                val totalOutSompis = tx.outputs.sumOf { it.amount }
                val totalAmountKas = totalOutSompis / 100_000_000.0
                val primaryRecipient = tx.outputs.firstOrNull()?.let { out ->
                    try {
                        CryptoUtils.encodeScriptPublicKeyToAddress(out.scriptPublicKey.script) ?: (out.scriptPublicKey.script.take(18) + "...")
                    } catch (_: Exception) {
                        out.scriptPublicKey.script.take(18) + "..."
                    }
                } ?: account.kaspaAddress

                // Detect operation intent: SWAP, BUY/SELL, KCC-20 on L1, CONTRACT, TRANSFER
                val payloadLower = (rawTxJson.toString() + requestJsonStr).lowercase()
                val actionType = when {
                    payloadLower.contains("swap") || payloadLower.contains("pool") || payloadLower.contains("route") || payloadLower.contains("liquidity") -> "🔄 DEX SWAP"
                    payloadLower.contains("buy") || payloadLower.contains("sell") || payloadLower.contains("listing") || payloadLower.contains("order") -> "🛒 BUY / SELL"
                    payloadLower.contains("kcc-20") || payloadLower.contains("kcc20") || payloadLower.contains("krc-20") || payloadLower.contains("krc20") || payloadLower.contains("mint") || payloadLower.contains("deploy") -> "🪙 KCC-20 L1 OPERATION"
                    tx.outputs.size > 2 || tx.inputs.size > 3 -> "📜 PROTOCOL INTERACTION"
                    else -> "💸 SIGN TRANSACTION"
                }

                val details = buildString {
                    append("Inputs: ${tx.inputs.size} | Outputs: ${tx.outputs.size}")
                    if (indicesToSign.size != tx.inputs.size) {
                        append(" (Signing ${indicesToSign.size} inputs)")
                    }
                }

                val estimatedFeeKas = (KaspaTransactionEngine.calculateMass(tx) * 10L) / 100_000_000.0

                withContext(Dispatchers.Main) {
                    val req = DAppApprovalRequest.SignTransaction(
                        id = callbackId,
                        origin = currentUrl,
                        actionType = actionType,
                        recipientOrContract = primaryRecipient,
                        amountKas = totalAmountKas,
                        feeKas = estimatedFeeKas.coerceAtLeast(0.00013),
                        details = details,
                        payloadSummary = "Tx with ${tx.inputs.size} in, ${tx.outputs.size} out",
                        onApprove = {
                            com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                context = context,
                                title = "Authorize $actionType",
                                subtitle = "Confirm signing on Kaspa BlockDAG",
                                onSuccess = {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            for (idx in indicesToSign) {
                                                val sighash = KaspaTransactionEngine.calcSchnorrSignatureHash(
                                                    tx = tx,
                                                    inputIndex = idx,
                                                    sigHashType = KaspaTransactionEngine.SIGHASH_ALL,
                                                    utxoEntry = utxoEntries[idx]
                                                )
                                                val sig64Hex = CryptoUtils.signSchnorr(privateKey, sighash)
                                                tx.inputs[idx].signatureScript = "41" + sig64Hex + "01"
                                            }

                                            tx.mass = KaspaTransactionEngine.calculateMass(tx)
                                            val signedTxJsonObj = tx.toJson()
                                            val signedTxJsonString = signedTxJsonObj.toString()

                                            val resultObj = JSONObject().apply {
                                                put("txJsonString", signedTxJsonString)
                                                put("signedTx", signedTxJsonString)
                                                put("tx", signedTxJsonString)
                                                put("rawTx", signedTxJsonString)
                                            }

                                            withContext(Dispatchers.Main) {
                                                viewModel.setStatusMessage("Signed $actionType with ${indicesToSign.size} input(s) on-chain")
                                                viewModel.clearDAppRequest()
                                            }

                                            resolveCallbackJson(callbackId, resultObj.toString())
                                        } catch (e: Exception) {
                                            Log.e(tag, "Transaction signing error: ${e.message}", e)
                                            val errMsg = "Signing error: ${e.message ?: "Invalid transaction payload"}"
                                            withContext(Dispatchers.Main) { 
                                                viewModel.setStatusMessage(errMsg)
                                                viewModel.clearDAppRequest()
                                            }
                                            rejectCallback(callbackId, errMsg)
                                        }
                                    }
                                },
                                onError = { err ->
                                    val errMsg = "Transaction signing cancelled: $err"
                                    viewModel.setStatusMessage(errMsg)
                                    viewModel.clearDAppRequest()
                                    rejectCallback(callbackId, errMsg)
                                }
                            )
                        },
                        onReject = { reason ->
                            viewModel.setStatusMessage("Transaction rejected by user")
                            viewModel.clearDAppRequest()
                            rejectCallback(callbackId, reason)
                        }
                    )
                    viewModel.submitDAppRequest(req)
                }
            } catch (e: Exception) {
                Log.e(tag, "Transaction signing error: ${e.message}", e)
                val errMsg = "Signing error: ${e.message ?: "Invalid transaction payload"}"
                withContext(Dispatchers.Main) { viewModel.setStatusMessage(errMsg) }
                rejectCallback(callbackId, errMsg)
            }
        }
    }

    private fun parseTransactionRequest(requestJsonStr: String): Pair<JSONObject, List<Int>> {
        val trimmed = requestJsonStr.trim()
        val signInputIndices = mutableListOf<Int>()

        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            if (root.has("txJsonString")) {
                val txString = root.getString("txJsonString")
                val options = root.optJSONObject("options")
                val signInputsArr = options?.optJSONArray("signInputs")
                if (signInputsArr != null) {
                    for (k in 0 until signInputsArr.length()) {
                        val item = signInputsArr.optJSONObject(k)
                        if (item != null && item.has("index")) {
                            signInputIndices.add(item.getInt("index"))
                        } else {
                            val intVal = signInputsArr.optInt(k, -1)
                            if (intVal >= 0) signInputIndices.add(intVal)
                        }
                    }
                }
                return Pair(JSONObject(txString), signInputIndices)
            } else if (root.has("txJson")) {
                val txVal = root.get("txJson")
                val txObj = if (txVal is JSONObject) txVal else JSONObject(txVal.toString())
                val options = root.optJSONObject("options")
                val signInputsArr = options?.optJSONArray("signInputs")
                if (signInputsArr != null) {
                    for (k in 0 until signInputsArr.length()) {
                        val item = signInputsArr.optJSONObject(k)
                        if (item != null && item.has("index")) {
                            signInputIndices.add(item.getInt("index"))
                        }
                    }
                }
                return Pair(txObj, signInputIndices)
            } else if (root.has("transaction")) {
                return Pair(root.getJSONObject("transaction"), signInputIndices)
            } else {
                return Pair(root, signInputIndices)
            }
        }
        return Pair(JSONObject(trimmed), signInputIndices)
    }

    private fun parseKaspaTransactionFromJson(json: JSONObject): KaspaTransactionEngine.KaspaTransaction {
        val version = json.optInt("version", 0)
        val lockTime = json.optLong("lockTime", json.optLong("lock_time", 0L))
        val subnetworkId = json.optString("subnetworkId", json.optString("subnetwork_id", KaspaTransactionEngine.DEFAULT_SUBNETWORK_ID))
        val gas = json.optLong("gas", 0L)
        val payload = json.optString("payload", "")
        val mass = json.optLong("mass", 0L)

        val inputsArr = json.optJSONArray("inputs") ?: JSONArray()
        val inputs = mutableListOf<KaspaTransactionEngine.KaspaTransactionInput>()
        for (i in 0 until inputsArr.length()) {
            val inp = inputsArr.getJSONObject(i)
            val prevOutObj = inp.getJSONObject("previousOutpoint")
            val prevTxId = prevOutObj.optString("transactionId", prevOutObj.optString("transaction_id", ""))
            val prevIndex = prevOutObj.optLong("index", 0L)
            val sigScript = inp.optString("signatureScript", inp.optString("signature_script", ""))
            val seq = inp.optLong("sequence", 0L)
            val sigOpCount = inp.optInt("sigOpCount", inp.optInt("sig_op_count", 1))
            inputs.add(
                KaspaTransactionEngine.KaspaTransactionInput(
                    previousOutpoint = KaspaTransactionEngine.KaspaOutpoint(prevTxId, prevIndex),
                    signatureScript = sigScript,
                    sequence = seq,
                    sigOpCount = sigOpCount
                )
            )
        }

        val outputsArr = json.optJSONArray("outputs") ?: JSONArray()
        val outputs = mutableListOf<KaspaTransactionEngine.KaspaTransactionOutput>()
        for (i in 0 until outputsArr.length()) {
            val out = outputsArr.getJSONObject(i)
            val amount = out.optLong("amount", 0L)
            val spkObj = out.optJSONObject("scriptPublicKey") ?: out.optJSONObject("script_public_key")
            val spk = if (spkObj != null) {
                KaspaTransactionEngine.KaspaScriptPublicKey(
                    version = spkObj.optInt("version", 0),
                    script = spkObj.optString("script", "")
                )
            } else {
                KaspaTransactionEngine.KaspaScriptPublicKey(0, out.optString("scriptPublicKey", ""))
            }
            val covObj = out.optJSONObject("covenant")
            val cov = if (covObj != null) {
                KaspaTransactionEngine.KaspaCovenantBinding(
                    authorizingInput = covObj.optInt("authorizingInput", 0),
                    covenantId = covObj.optString("covenantId", "")
                )
            } else null

            outputs.add(KaspaTransactionEngine.KaspaTransactionOutput(amount, spk, cov))
        }

        return KaspaTransactionEngine.KaspaTransaction(
            version = version,
            inputs = inputs,
            outputs = outputs,
            lockTime = lockTime,
            subnetworkId = subnetworkId,
            gas = gas,
            payload = payload,
            mass = mass
        )
    }

    private fun parseUtxoEntryFromInput(root: JSONObject, inputIndex: Int): KaspaTransactionEngine.KaspaUtxoEntry? {
        try {
            val inputsArr = root.optJSONArray("inputs") ?: return null
            if (inputIndex !in 0 until inputsArr.length()) return null
            val inp = inputsArr.getJSONObject(inputIndex)
            val utxoObj = inp.optJSONObject("utxoEntry") ?: inp.optJSONObject("utxo_entry") ?: return null
            val amount = utxoObj.optLong("amount", 0L)
            val spkObj = utxoObj.optJSONObject("scriptPublicKey") ?: utxoObj.optJSONObject("script_public_key")
            val spk = if (spkObj != null) {
                KaspaTransactionEngine.KaspaScriptPublicKey(
                    version = spkObj.optInt("version", 0),
                    script = spkObj.optString("script", "")
                )
            } else {
                KaspaTransactionEngine.KaspaScriptPublicKey(0, utxoObj.optString("scriptPublicKey", ""))
            }
            val blockDaa = utxoObj.optLong("blockDaaScore", utxoObj.optLong("block_daa_score", 0L))
            val isCoinbase = utxoObj.optBoolean("isCoinbase", utxoObj.optBoolean("is_coinbase", false))
            return KaspaTransactionEngine.KaspaUtxoEntry(amount, spk, blockDaa, isCoinbase)
        } catch (_: Exception) {
            return null
        }
    }

    @JavascriptInterface
    fun sendKaspa(toAddress: String, sompiAmount: Double, callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                if (account == null) {
                    rejectCallback(callbackId, "No active Kaspa account in wallet")
                    return@launch
                }
                val seed = getSeedPhrase(account)
                if (seed.isNullOrBlank()) {
                    rejectCallback(callbackId, "Cannot decrypt wallet private key")
                    return@launch
                }

                val amountKas = sompiAmount / 100_000_000.0
                val currentUrl = withContext(Dispatchers.Main) {
                    webViewProvider()?.url ?: "Kaspa Web3 DApp"
                }

                withContext(Dispatchers.Main) {
                    val req = DAppApprovalRequest.SignTransaction(
                        id = callbackId,
                        origin = currentUrl,
                        actionType = "💸 TRANSFER KAS",
                        recipientOrContract = toAddress,
                        amountKas = amountKas,
                        feeKas = 0.00013,
                        details = "Send %.4f KAS to recipient".format(amountKas),
                        payloadSummary = "Standard KAS Payment",
                        onApprove = {
                            com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                context = context,
                                title = "Authorize Kaspa Transfer",
                                subtitle = "Send %.4f KAS to %s".format(amountKas, toAddress.take(18)),
                                onSuccess = {
                                    scope.launch(Dispatchers.IO) {
                                        val result = viewModel.walletService.sendKaspa(
                                            senderAddress = account.kaspaAddress,
                                            senderSeed = seed,
                                            recipientAddress = toAddress,
                                            amountKas = amountKas
                                        )

                                        if (result.isSuccess) {
                                            val txItem = result.getOrThrow()
                                            val res = JSONObject().apply {
                                                put("id", txItem.txId)
                                                put("txId", txItem.txId)
                                                put("transactionId", txItem.txId)
                                            }
                                            withContext(Dispatchers.Main) {
                                                viewModel.setStatusMessage("Sent $amountKas KAS on BlockDAG! Tx: ${txItem.txId.take(16)}...")
                                                viewModel.clearDAppRequest()
                                                viewModel.refreshKaspaWallet(account.kaspaAddress)
                                            }
                                            resolveCallbackJson(callbackId, res.toString())
                                        } else {
                                            val err = result.exceptionOrNull()?.message ?: "Transaction broadcast failed"
                                            withContext(Dispatchers.Main) {
                                                viewModel.setStatusMessage("Transaction failed: $err")
                                                viewModel.clearDAppRequest()
                                            }
                                            rejectCallback(callbackId, err)
                                        }
                                    }
                                },
                                onError = { err ->
                                    val errMsg = "Transfer rejected: $err"
                                    viewModel.setStatusMessage(errMsg)
                                    viewModel.clearDAppRequest()
                                    rejectCallback(callbackId, errMsg)
                                }
                            )
                        },
                        onReject = { reason ->
                            viewModel.setStatusMessage("Transfer rejected by user")
                            viewModel.clearDAppRequest()
                            rejectCallback(callbackId, reason)
                        }
                    )
                    viewModel.submitDAppRequest(req)
                }
            } catch (e: Exception) {
                Log.e(tag, "sendKaspa error: ${e.message}", e)
                rejectCallback(callbackId, e.message ?: "Failed to send Kaspa")
            }
        }
    }

    @JavascriptInterface
    fun signMessage(message: String, callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                if (account == null) {
                    rejectCallback(callbackId, "No active Kaspa account")
                    return@launch
                }
                val seed = getSeedPhrase(account)
                if (seed.isNullOrBlank()) {
                    rejectCallback(callbackId, "Cannot decrypt wallet private key")
                    return@launch
                }

                val currentUrl = withContext(Dispatchers.Main) {
                    webViewProvider()?.url ?: "Kaspa Web3 DApp"
                }

                withContext(Dispatchers.Main) {
                    val req = DAppApprovalRequest.SignMessage(
                        id = callbackId,
                        origin = currentUrl,
                        message = message,
                        onApprove = {
                            com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                context = context,
                                title = "Authorize Message Signature",
                                subtitle = "DApp requests signature on message",
                                onSuccess = {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val sig = CryptoUtils.signMessage(message, seed)
                                            withContext(Dispatchers.Main) {
                                                viewModel.setStatusMessage("Message signed successfully")
                                                viewModel.clearDAppRequest()
                                            }
                                            resolveCallbackString(callbackId, sig)
                                        } catch (e: Exception) {
                                            Log.e(tag, "signMessage error: ${e.message}", e)
                                            withContext(Dispatchers.Main) {
                                                viewModel.clearDAppRequest()
                                            }
                                            rejectCallback(callbackId, e.message ?: "Failed to sign message")
                                        }
                                    }
                                },
                                onError = { err ->
                                    val errMsg = "Message signature rejected: $err"
                                    viewModel.setStatusMessage(errMsg)
                                    viewModel.clearDAppRequest()
                                    rejectCallback(callbackId, errMsg)
                                }
                            )
                        },
                        onReject = { reason ->
                            viewModel.setStatusMessage("Message signature rejected by user")
                            viewModel.clearDAppRequest()
                            rejectCallback(callbackId, reason)
                        }
                    )
                    viewModel.submitDAppRequest(req)
                }
            } catch (e: Exception) {
                Log.e(tag, "signMessage error: ${e.message}", e)
                rejectCallback(callbackId, e.message ?: "Failed to sign message")
            }
        }
    }

    @JavascriptInterface
    fun pushTx(rawTxJsonStr: String, callbackId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                val addr = account?.kaspaAddress ?: "kaspa:qq"
                val tx = parseKaspaTransactionFromJson(JSONObject(rawTxJsonStr))
                val computedTxId = KaspaTransactionEngine.calcTransactionId(tx)

                val endpoint = if (addr.startsWith("kaspatest:")) {
                    "https://api-tn10.kaspa.org/transactions"
                } else {
                    "https://api.kaspa.org/transactions"
                }

                val payload = KaspaTransactionEngine.buildSubmitPayload(tx, allowOrphan = false)
                val mediaType = "application/json".toMediaType()
                val body = payload.toString().toRequestBody(mediaType)

                var broadcastOk = false
                var returnedTxId = computedTxId
                var lastErr = "Node unavailable"

                val fastClient = CronetClientFactory.buildClient(
                    OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                )

                for (attempt in 0 until 3) {
                    try {
                        val req = Request.Builder()
                            .url(endpoint)
                            .header("User-Agent", "KaspaBrowser/1.0 (Android; Mobile)")
                            .header("Accept", "application/json")
                            .post(body)
                            .build()
                        fastClient.newCall(req).execute().use { resp ->
                            val bodyStr = resp.body?.string() ?: ""
                            if (resp.isSuccessful) {
                                broadcastOk = true
                                if (bodyStr.startsWith("{")) {
                                    val j = JSONObject(bodyStr)
                                    val tid = j.optString("transactionId", j.optString("txid", ""))
                                    if (tid.isNotBlank()) returnedTxId = tid
                                }
                            } else {
                                val errDetail = try {
                                    if (bodyStr.startsWith("{")) {
                                        val j = JSONObject(bodyStr)
                                        j.optString("error", j.optString("message", bodyStr))
                                    } else bodyStr
                                } catch (_: Exception) { bodyStr }
                                lastErr = "Node returned HTTP ${resp.code}: $errDetail"
                            }
                        }
                        if (broadcastOk || lastErr.startsWith("Node returned HTTP 4")) break
                    } catch (e: Exception) {
                        lastErr = e.message ?: "Connection error"
                        if (attempt < 2) kotlinx.coroutines.delay(500L * (attempt + 1))
                    }
                }

                if (broadcastOk) {
                    val res = JSONObject().apply { put("txId", returnedTxId) }
                    resolveCallbackJson(callbackId, res.toString())
                } else {
                    rejectCallback(callbackId, "Broadcast failed: $lastErr")
                }
            } catch (e: Exception) {
                rejectCallback(callbackId, e.message ?: "Failed to push transaction")
            }
        }
    }

    private fun resolveCallbackJson(callbackId: String, resultJson: String) {
        val wv = webViewProvider() ?: return
        val script = "window.__kaswareResolve && window.__kaswareResolve('$callbackId', $resultJson, true);"
        wv.post { wv.evaluateJavascript(script, null) }
    }

    private fun resolveCallbackString(callbackId: String, resultString: String) {
        val wv = webViewProvider() ?: return
        val escaped = JSONObject.quote(resultString)
        val script = "window.__kaswareResolve && window.__kaswareResolve('$callbackId', $escaped, false);"
        wv.post { wv.evaluateJavascript(script, null) }
    }

    private fun rejectCallback(callbackId: String, errorMessage: String) {
        val wv = webViewProvider() ?: return
        val escaped = JSONObject.quote(errorMessage)
        val script = "window.__kaswareReject && window.__kaswareReject('$callbackId', $escaped);"
        wv.post { wv.evaluateJavascript(script, null) }
    }

    companion object {
        fun getInjectionScript(): String {
            return """
                (function() {
                    try {
                        if (window.__kaspaWalletInjected) return;
                        window.__kaspaWalletInjected = true;

                        try {
                            var patchEthersObj = function(obj) {
                                if (!obj) return;
                                try {
                                    if (obj.AbstractProvider && obj.AbstractProvider.prototype) {
                                        obj.AbstractProvider.prototype.getEnsAddress = function() { return Promise.resolve(null); };
                                        obj.AbstractProvider.prototype.resolveName = function() { return Promise.resolve(null); };
                                        obj.AbstractProvider.prototype.lookupAddress = function() { return Promise.resolve(null); };
                                    }
                                    if (obj.BrowserProvider && obj.BrowserProvider.prototype) {
                                        obj.BrowserProvider.prototype.getEnsAddress = function() { return Promise.resolve(null); };
                                        obj.BrowserProvider.prototype.resolveName = function() { return Promise.resolve(null); };
                                        obj.BrowserProvider.prototype.lookupAddress = function() { return Promise.resolve(null); };
                                    }
                                } catch(e) {}
                            };
                            if (window.ethers) patchEthersObj(window.ethers);
                            var _origEthers = window.ethers;
                            try {
                                Object.defineProperty(window, 'ethers', {
                                    get: function() { return _origEthers; },
                                    set: function(v) {
                                        _origEthers = v;
                                        patchEthersObj(v);
                                    },
                                    configurable: true
                                });
                            } catch(e) {}
                        } catch(e) {}

                        window.__kaswareCallbacks = window.__kaswareCallbacks || {};

                        var eventListeners = {};

                        var isInstalledFn = function() { return true; };
                        isInstalledFn.valueOf = function() { return true; };
                        isInstalledFn.toString = function() { return 'true'; };

                        function syncConnectedAddress(addr) {
                            if (!addr) return;
                            var accs = [addr];
                            [kaswareProvider, kastleProvider, kasperiaProvider, kaspireProvider, ethereumProvider, coinbaseProvider].forEach(function(p) {
                                if (p) {
                                    p.selectedAddress = addr;
                                    p.address = addr;
                                    p.account = addr;
                                    p.accounts = accs;
                                    if (p.emit) {
                                        p.emit('accountsChanged', accs);
                                        p.emit('connect', { chainId: p.chainId || 'kaspa-mainnet' });
                                    }
                                }
                            });
                            try {
                                window.dispatchEvent(new CustomEvent('kaspa#accountsChanged', { detail: accs }));
                                window.dispatchEvent(new CustomEvent('kastle#accountsChanged', { detail: accs }));
                                window.dispatchEvent(new CustomEvent('kasware#accountsChanged', { detail: accs }));
                                window.dispatchEvent(new CustomEvent('accountsChanged', { detail: accs }));
                                window.dispatchEvent(new CustomEvent('ethereum#accountsChanged', { detail: accs }));
                            } catch(e) {}
                        }

                        function syncNetwork(net, chainIdHex) {
                            if (!net) return;
                            var parsedChainId = chainIdHex;
                            if (!parsedChainId) {
                                if (typeof net === 'string' && net.indexOf('0x') === 0) {
                                    parsedChainId = net;
                                } else if (typeof net === 'object' && net.chainId) {
                                    parsedChainId = net.chainId;
                                } else if (typeof net === 'string' && net.toLowerCase().indexOf('igra') !== -1) {
                                    parsedChainId = '0x97B1';
                                } else {
                                    parsedChainId = 'kaspa-mainnet';
                                }
                            }
                            var netStr = typeof net === 'string' ? net : (net.chainName || net.chainId || 'Igra Mainnet');
                            [kaswareProvider, kastleProvider, kasperiaProvider, kaspireProvider, ethereumProvider, coinbaseProvider].forEach(function(p) {
                                if (p) {
                                    p.network = netStr;
                                    p.chainId = parsedChainId;
                                    if (p.emit) {
                                        p.emit('networkChanged', netStr);
                                        p.emit('chainChanged', parsedChainId);
                                    }
                                }
                            });
                            try {
                                window.dispatchEvent(new CustomEvent('kaspa#networkChanged', { detail: netStr }));
                                window.dispatchEvent(new CustomEvent('chainChanged', { detail: parsedChainId }));
                            } catch(e) {}
                        }

                        window.__kaswareSyncNetwork = function(net, chainIdHex) {
                            syncNetwork(net, chainIdHex);
                        };

                        window.__kaswareResolve = function(id, resData, isJson) {
                            var cb = window.__kaswareCallbacks[id];
                            if (cb) {
                                delete window.__kaswareCallbacks[id];
                                if (Array.isArray(resData) && resData.length > 0 && typeof resData[0] === 'string') {
                                    syncConnectedAddress(resData[0]);
                                }
                                if (resData === 'null' || resData === null) {
                                    cb.resolve(null);
                                } else {
                                    cb.resolve(resData);
                                }
                            }
                        };

                        window.__kaswareReject = function(id, err) {
                            var cb = window.__kaswareCallbacks[id];
                            if (cb) {
                                delete window.__kaswareCallbacks[id];
                                var error = new Error(err || 'Kaspa Wallet request failed');
                                cb.reject(error);
                            }
                        };

                        function executeKasware(method, args) {
                            return new Promise(function(resolve, reject) {
                                if (!window.KaspaWalletBridge) {
                                    return reject(new Error('Kaspa Wallet Bridge not available'));
                                }
                                var callId = 'kw_' + Math.random().toString(36).substr(2, 9) + '_' + Date.now();
                                window.__kaswareCallbacks[callId] = { resolve: resolve, reject: reject };
                                try {
                                    if (method === 'requestAccounts' || method === 'connect' || method === 'enable') {
                                        window.KaspaWalletBridge.requestAccounts(callId);
                                    } else if (method === 'getAccounts') {
                                        window.KaspaWalletBridge.getAccounts(callId);
                                    } else if (method === 'getPublicKey') {
                                        window.KaspaWalletBridge.getPublicKey(callId);
                                    } else if (method === 'getBalance') {
                                        window.KaspaWalletBridge.getBalance(callId);
                                    } else if (method === 'getNetwork') {
                                        window.KaspaWalletBridge.getNetwork(callId);
                                    } else if (method === 'switchNetwork' || method === 'wallet_switchEthereumChain' || method === 'wallet_addEthereumChain' || method === 'wallet_switchKaspaNetwork' || method === 'wallet_addKaspaNetwork') {
                                        var argObj = (args && args[0]) ? args[0] : {};
                                        var netName = typeof argObj === 'string' ? argObj : (argObj.chainName || argObj.chainId || 'Igra Mainnet');
                                        window.KaspaWalletBridge.switchNetwork(netName, callId);
                                    } else if (method === 'disconnect') {
                                        window.KaspaWalletBridge.disconnect(args[0] || '', callId);
                                    } else if (method === 'signPskt') {
                                        var pStr = typeof args[0] === 'string' ? args[0] : JSON.stringify(args[0]);
                                        window.KaspaWalletBridge.signPskt(pStr, callId);
                                    } else if (method === 'signTransaction') {
                                        var tStr = typeof args[0] === 'string' ? args[0] : JSON.stringify(args[0]);
                                        window.KaspaWalletBridge.signTransaction(tStr, callId);
                                    } else if (method === 'sendKaspa') {
                                        var toAddr = args[0] || '';
                                        var sompis = Number(args[1]) || 0;
                                        window.KaspaWalletBridge.sendKaspa(toAddr, sompis, callId);
                                    } else if (method === 'getKRC20Balance') {
                                        var tick = args[0] || '';
                                        window.KaspaWalletBridge.getKRC20Balance(tick, callId);
                                    } else if (method === 'getKRC20TokenList') {
                                        window.KaspaWalletBridge.getKRC20TokenList(callId);
                                    } else if (method === 'getKRC20TokenInfo') {
                                        var tick2 = args[0] || '';
                                        window.KaspaWalletBridge.getKRC20TokenInfo(tick2, callId);
                                    } else if (method === 'getUtxoEntries') {
                                        var uAddr = args[0] || '';
                                        window.KaspaWalletBridge.getUtxoEntries(uAddr, callId);
                                    } else if (method === 'signMessage') {
                                        window.KaspaWalletBridge.signMessage(args[0] || '', callId);
                                    } else if (method === 'pushTx') {
                                        var txStr = typeof args[0] === 'string' ? args[0] : JSON.stringify(args[0]);
                                        window.KaspaWalletBridge.pushTx(txStr, callId);
                                    } else {
                                        delete window.__kaswareCallbacks[callId];
                                        reject(new Error('Unknown wallet method: ' + method));
                                    }
                                } catch(e) {
                                    delete window.__kaswareCallbacks[callId];
                                    reject(e);
                                }
                            });
                        }

                        function handleRpcRequest(args) {
                            if (!args) return Promise.reject(new Error('Missing RPC payload'));
                            var method = typeof args === 'string' ? args : args.method;
                            var params = args.params || [];
                            if (typeof args === 'object' && !args.method && args.txJsonString) {
                                return executeKasware('signTransaction', [args]);
                            }
                            switch(method) {
                                case 'eth_requestAccounts':
                                case 'kas_requestAccounts':
                                case 'requestAccounts':
                                case 'wallet_requestPermissions':
                                case 'connect':
                                case 'enable':
                                    return executeKasware('requestAccounts', params);
                                case 'eth_accounts':
                                case 'kas_accounts':
                                case 'getAccounts':
                                case 'accounts':
                                    return executeKasware('getAccounts', params);
                                case 'wallet_getPermissions':
                                    return Promise.resolve([{ parentCapability: 'eth_accounts' }]);
                                case 'wallet_switchEthereumChain':
                                case 'wallet_switchKaspaNetwork':
                                    var switchObj = (params && params[0]) ? params[0] : {};
                                    var swChainId = typeof switchObj === 'string' ? switchObj : (switchObj.chainId || switchObj.chainName || '0x97B1');
                                    syncNetwork(swChainId, swChainId);
                                    return executeKasware('switchNetwork', [swChainId]);
                                case 'wallet_addEthereumChain':
                                case 'wallet_addKaspaNetwork':
                                    var addObj = (params && params[0]) ? params[0] : {};
                                    var addName = typeof addObj === 'string' ? addObj : (addObj.chainName || addObj.chainId || 'Igra Mainnet');
                                    var addChainId = typeof addObj === 'object' ? addObj.chainId : addName;
                                    if (typeof addObj === 'object' && addObj.rpcUrls && addObj.rpcUrls[0]) {
                                        window.__kaswareActiveRpc = addObj.rpcUrls[0];
                                    }
                                    syncNetwork(addName, addChainId);
                                    return executeKasware('switchNetwork', [addName]);
                                case 'eth_chainId':
                                    var cId = (ethereumProvider && ethereumProvider.chainId) || '0x97B1';
                                    return Promise.resolve(cId.indexOf('0x') === 0 ? cId : ('0x' + parseInt(cId, 10).toString(16)));
                                case 'net_version':
                                case 'eth_netVersion':
                                    var cId2 = (ethereumProvider && ethereumProvider.chainId) || '38833';
                                    var numId = cId2.indexOf('0x') === 0 ? parseInt(cId2, 16).toString() : cId2;
                                    return Promise.resolve(numId);
                                case 'eth_call':
                                case 'eth_blockNumber':
                                case 'eth_estimateGas':
                                case 'eth_gasPrice':
                                case 'eth_getCode':
                                case 'eth_getTransactionCount':
                                case 'eth_getTransactionByHash':
                                case 'eth_getTransactionReceipt':
                                    if (method === 'eth_call' && params && params[0] && params[0].to && params[0].to.toLowerCase() === '0x00000000000c2e074ec69a0dfb2997ba6c7d2e1e') {
                                        return Promise.resolve("0x0000000000000000000000000000000000000000000000000000000000000000");
                                    }
                                    var rpcUrl = window.__kaswareActiveRpc || 'https://rpc.igralabs.com:8545';
                                    return fetch(rpcUrl, {
                                        method: 'POST',
                                        headers: { 'Content-Type': 'application/json' },
                                        body: JSON.stringify({
                                            jsonrpc: '2.0',
                                            id: Math.floor(Math.random() * 1000000),
                                            method: method,
                                            params: params
                                        })
                                    }).then(function(r) { return r.json(); }).then(function(res) {
                                        if (res && res.error) {
                                            if (method === 'eth_call') {
                                                return "0x0000000000000000000000000000000000000000000000000000000000000000";
                                            }
                                            throw new Error(res.error.message || 'RPC Error');
                                        }
                                        return res ? res.result : null;
                                    }).catch(function(err) {
                                        if (method === 'eth_call') {
                                            return "0x0000000000000000000000000000000000000000000000000000000000000000";
                                        }
                                        throw err;
                                    });
                                case 'eth_ensAddress':
                                case 'getEnsAddress':
                                case 'ens_resolveName':
                                case 'ens_lookupAddress':
                                    return Promise.resolve('0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e');
                                case 'eth_getBalance':
                                case 'kas_getBalance':
                                case 'getBalance':
                                    return executeKasware('getBalance', params);
                                case 'kas_getKRC20Balance':
                                case 'getKRC20Balance':
                                case 'krc20_getBalance':
                                    return executeKasware('getKRC20Balance', params);
                                case 'kas_getKRC20TokenList':
                                case 'getKRC20TokenList':
                                case 'krc20_getTokenList':
                                    return executeKasware('getKRC20TokenList', params);
                                case 'kas_getKRC20TokenInfo':
                                case 'getKRC20TokenInfo':
                                case 'krc20_getTokenInfo':
                                    return executeKasware('getKRC20TokenInfo', params);
                                case 'kas_getUtxoEntries':
                                case 'getUtxoEntries':
                                case 'getUtxos':
                                    return executeKasware('getUtxoEntries', params);
                                case 'kas_getNetwork':
                                case 'getNetwork':
                                    return executeKasware('getNetwork', params);
                                case 'kas_getPublicKey':
                                case 'getPublicKey':
                                    return executeKasware('getPublicKey', params);
                                case 'kas_signPskt':
                                case 'signPskt':
                                    return executeKasware('signPskt', params);
                                case 'eth_sendTransaction':
                                case 'eth_signTransaction':
                                case 'kas_signTransaction':
                                case 'signTransaction':
                                    return executeKasware('signTransaction', params);
                                case 'personal_sign':
                                case 'eth_sign':
                                case 'kas_signMessage':
                                case 'signMessage':
                                    return executeKasware('signMessage', params);
                                case 'kas_sendKaspa':
                                case 'sendKaspa':
                                    return executeKasware('sendKaspa', params);
                                case 'kas_pushTx':
                                case 'pushTx':
                                    return executeKasware('pushTx', params);
                                case 'kas_disconnect':
                                case 'disconnect':
                                    return executeKasware('disconnect', params);
                                default:
                                    return executeKasware(method, params);
                            }
                        }

                        var baseProviderMethods = {
                            isInstalled: isInstalledFn,
                            installed: true,
                            version: '1.3.2',
                            selectedAddress: null,
                            accounts: [],
                            network: 'kaspa_mainnet',
                            chainId: 'kaspa-mainnet',
                            ensAddress: '0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e',
                            getEnsAddress: function() { return Promise.resolve(null); },
                            resolveName: function() { return Promise.resolve(null); },
                            lookupAddress: function() { return Promise.resolve(null); },
                            request: function(args) { return handleRpcRequest(args); },
                            connect: function() { return executeKasware('requestAccounts', []); },
                            enable: function() { return executeKasware('requestAccounts', []); },
                            requestAccounts: function() { return executeKasware('requestAccounts', []); },
                            getAccounts: function() { return executeKasware('getAccounts', []); },
                            getPublicKey: function() { return executeKasware('getPublicKey', []); },
                            getBalance: function() { return executeKasware('getBalance', []); },
                            getKRC20Balance: function(ticker) { return executeKasware('getKRC20Balance', [ticker]); },
                            getKRC20TokenList: function() { return executeKasware('getKRC20TokenList', []); },
                            getKRC20TokenInfo: function(ticker) { return executeKasware('getKRC20TokenInfo', [ticker]); },
                            getUtxoEntries: function(addr) { return executeKasware('getUtxoEntries', [addr]); },
                            getNetwork: function() { return executeKasware('getNetwork', []); },
                            switchNetwork: function(net) { return executeKasware('switchNetwork', [net]); },
                            disconnect: function(origin) { return executeKasware('disconnect', [origin]); },
                            signPskt: function(param) { return executeKasware('signPskt', [param]); },
                            signTransaction: function(param, options) {
                                if (options && typeof param === 'object' && !param.options) {
                                    param = { txJsonString: JSON.stringify(param), options: options };
                                }
                                return executeKasware('signTransaction', [param]);
                            },
                            signKCC20Transaction: function(json, type, dest, pFee) {
                                return executeKasware('signTransaction', [{ txJsonString: typeof json === 'string' ? json : JSON.stringify(json) }]);
                            },
                            signKRC20Transaction: function(json, type, dest, pFee) {
                                return executeKasware('signTransaction', [{ txJsonString: typeof json === 'string' ? json : JSON.stringify(json) }]);
                            },
                            submitCommitReveal: function(param) {
                                return executeKasware('signTransaction', [param]);
                            },
                            sendKaspa: function(toAddress, sompiAmount, options) {
                                return executeKasware('sendKaspa', [toAddress, sompiAmount]);
                            },
                            signMessage: function(msg, type) { return executeKasware('signMessage', [msg, type]); },
                            pushTx: function(param) { return executeKasware('pushTx', [param]); },
                            on: function(evt, handler) {
                                eventListeners[evt] = eventListeners[evt] || [];
                                eventListeners[evt].push(handler);
                                return this;
                            },
                            removeListener: function(evt, handler) {
                                if (!eventListeners[evt]) return this;
                                eventListeners[evt] = eventListeners[evt].filter(function(h) { return h !== handler; });
                                return this;
                            },
                            off: function(evt, handler) { return this.removeListener(evt, handler); },
                            addListener: function(evt, handler) { return this.on(evt, handler); },
                            emit: function(evt, data) {
                                if (eventListeners[evt]) {
                                    eventListeners[evt].forEach(function(h) { try { h(data); } catch(e){} });
                                }
                            }
                        };

                        var kaswareProvider = Object.assign({}, baseProviderMethods, {
                            name: 'KasWare',
                            isKasWare: true,
                            isKaspa: true
                        });

                        var kastleProvider = Object.assign({}, baseProviderMethods, {
                            name: 'Kastle',
                            isKastle: true,
                            isKaspa: true
                        });

                        var kasperiaProvider = Object.assign({}, baseProviderMethods, {
                            name: 'Kasperia',
                            isKasperia: true,
                            isKaspa: true
                        });

                        var kaspireProvider = Object.assign({}, baseProviderMethods, {
                            name: 'Kaspire',
                            isKaspire: true,
                            isKaspa: true
                        });

                        var ethereumProvider = Object.assign({}, baseProviderMethods, {
                            name: 'MetaMask',
                            isMetaMask: true,
                            isCoinbaseWallet: true,
                            chainId: '0x1',
                            networkVersion: '1',
                            send: function(method, params) {
                                if (typeof method === 'string') {
                                    return handleRpcRequest({ method: method, params: params });
                                }
                                return handleRpcRequest(method);
                            },
                            sendAsync: function(payload, cb) {
                                handleRpcRequest(payload)
                                    .then(function(res) { if (cb) cb(null, { id: payload.id, jsonrpc: '2.0', result: res }); })
                                    .catch(function(err) { if (cb) cb(err, null); });
                            }
                        });

                        var coinbaseProvider = Object.assign({}, ethereumProvider, {
                            name: 'Coinbase Wallet',
                            isCoinbaseWallet: true
                        });

                        window.kasware = kaswareProvider;
                        window.kaspa = kaswareProvider;
                        window.kastle = kastleProvider;
                        window.kasperia = kasperiaProvider;
                        window.kaspire = kaspireProvider;
                        window.ethereum = ethereumProvider;
                        window.coinbaseWalletExtension = coinbaseProvider;

                        function announceEip6963() {
                            try {
                                var providers = [
                                    { uuid: 'kastle-wallet', name: 'Kastle', rdns: 'io.kastle', prov: kastleProvider },
                                    { uuid: 'kasperia-wallet', name: 'Kasperia', rdns: 'io.kasperia', prov: kasperiaProvider },
                                    { uuid: 'kasware-wallet', name: 'KasWare', rdns: 'com.kasware', prov: kaswareProvider },
                                    { uuid: 'kaspire-wallet', name: 'Kaspire', rdns: 'io.kaspire', prov: kaspireProvider }
                                ];
                                providers.forEach(function(item) {
                                    var detail = Object.freeze({
                                        info: {
                                            uuid: item.uuid,
                                            name: item.name,
                                            icon: 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><rect width="32" height="32" rx="8" fill="%2370c7ba"/><path d="M8 8h16v16H8z" fill="white"/></svg>',
                                            rdns: item.rdns
                                        },
                                        provider: item.prov
                                    });
                                    window.dispatchEvent(new CustomEvent('eip6963:announceProvider', { detail: detail }));
                                });
                            } catch(e) {}
                        }

                        window.addEventListener('eip6963:requestProvider', announceEip6963);
                        document.addEventListener('eip6963:requestProvider', announceEip6963);

                        try {
                            window.dispatchEvent(new Event('kastle#initialized'));
                            window.dispatchEvent(new Event('kastle:ready'));
                            document.dispatchEvent(new Event('kastle:ready'));
                            window.dispatchEvent(new Event('kasperia#initialized'));
                            window.dispatchEvent(new Event('kasperia:ready'));
                            document.dispatchEvent(new Event('kasperia:ready'));
                            window.dispatchEvent(new Event('kasware#initialized'));
                            window.dispatchEvent(new Event('kasware:ready'));
                            document.dispatchEvent(new Event('kasware:ready'));
                            window.dispatchEvent(new Event('kaspire#initialized'));
                            window.dispatchEvent(new Event('kaspire:ready'));
                            document.dispatchEvent(new Event('kaspire:ready'));
                            window.dispatchEvent(new Event('kaspa#initialized'));
                            window.dispatchEvent(new Event('kaspa:ready'));
                            window.dispatchEvent(new CustomEvent('ethereum#initialized'));
                            announceEip6963();
                        } catch(e) {}

                    } catch(e) {
                        console.warn('Kaspa wallet bridge initialization error:', e);
                    }
                })();
            """.trimIndent()
        }
    }
}

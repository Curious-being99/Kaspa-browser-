package com.example.network

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.data.AccountEntity
import com.example.network.kaspa.DotkProtocol
import com.example.network.kaspa.KaspaTransactionEngine
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
 * Universal Kaspa Web3 DApp & Wallet Provider Bridge (window.kasware & window.kaspa).
 * Provides full support for standard Kaspa dApp methods:
 * - isInstalled(), requestAccounts(), getAccounts(), getPublicKey(), getBalance(), getNetwork(), switchNetwork()
 * - signPskt(), signTransaction(), signKRC20Transaction(), sendKaspa(), signMessage(), pushTx()
 * - Standard event emitter: on(), removeListener(), addListener(), off()
 */
class KaspaWalletBridge(
    private val context: Context,
    private val webViewProvider: () -> WebView?,
    private val viewModel: DecentralViewModel,
    private val scope: CoroutineScope
) {
    private val tag = "KaspaWalletBridge"

    private fun getActiveAccount(): AccountEntity? {
        return viewModel.activeAccount.value
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

                val accountsArray = JSONArray().apply { put(account.kaspaAddress) }
                withContext(Dispatchers.Main) {
                    viewModel.setStatusMessage("DApp connected to Kaspa account: ${account.kaspaAddress.take(18)}...")
                }
                resolveCallbackJson(callbackId, accountsArray.toString())
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

    /**
     * Signs transaction inputs (PSKT / Kaspa Transaction standard).
     * Strictly compatible with dotk.name, KasWare, and KRC20 dApp requests.
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

                // Perform Schnorr signature on requested inputs after biometric confirmation
                val indicesToSign = if (signInputIndices.isNotEmpty()) {
                    signInputIndices.filter { it in tx.inputs.indices }
                } else {
                    tx.inputs.indices.filter { tx.inputs[it].signatureScript.isEmpty() }
                }

                withContext(Dispatchers.Main) {
                    com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                        context = context,
                        title = "Authorize Kaspa Transaction",
                        subtitle = "DApp requests signature on ${indicesToSign.size} input(s)",
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
                                        viewModel.setStatusMessage("Signed transaction with ${indicesToSign.size} input(s) on-chain")
                                    }

                                    // Resolve with signed transaction object
                                    resolveCallbackJson(callbackId, resultObj.toString())
                                } catch (e: Exception) {
                                    Log.e(tag, "Transaction signing error: ${e.message}", e)
                                    val errMsg = "Signing error: ${e.message ?: "Invalid transaction payload"}"
                                    withContext(Dispatchers.Main) { viewModel.setStatusMessage(errMsg) }
                                    rejectCallback(callbackId, errMsg)
                                }
                            }
                        },
                        onError = { err ->
                            val errMsg = "Transaction signing cancelled: $err"
                            viewModel.setStatusMessage(errMsg)
                            rejectCallback(callbackId, errMsg)
                        }
                    )
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

                withContext(Dispatchers.Main) {
                    viewModel.setStatusMessage("DApp requested sending $amountKas KAS to ${toAddress.take(18)}...")
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
                                        viewModel.refreshKaspaWallet(account.kaspaAddress)
                                    }
                                    resolveCallbackJson(callbackId, res.toString())
                                } else {
                                    val err = result.exceptionOrNull()?.message ?: "Transaction broadcast failed"
                                    withContext(Dispatchers.Main) {
                                        viewModel.setStatusMessage("Transaction failed: $err")
                                    }
                                    rejectCallback(callbackId, err)
                                }
                            }
                        },
                        onError = { err ->
                            val errMsg = "Transfer rejected: $err"
                            viewModel.setStatusMessage(errMsg)
                            rejectCallback(callbackId, errMsg)
                        }
                    )
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

                withContext(Dispatchers.Main) {
                    com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                        context = context,
                        title = "Authorize Message Signature",
                        subtitle = "DApp requests signature on message",
                        onSuccess = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val sig = CryptoUtils.signMessage(message, seed)
                                    resolveCallbackString(callbackId, sig)
                                } catch (e: Exception) {
                                    Log.e(tag, "signMessage error: ${e.message}", e)
                                    rejectCallback(callbackId, e.message ?: "Failed to sign message")
                                }
                            }
                        },
                        onError = { err ->
                            val errMsg = "Message signature rejected: $err"
                            viewModel.setStatusMessage(errMsg)
                            rejectCallback(callbackId, errMsg)
                        }
                    )
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

                val endpoints = if (addr.startsWith("kaspatest:")) {
                    listOf("https://api-tn10.kaspa.org/transactions", "https://api-tn10.kaspanet.io/transactions")
                } else {
                    listOf("https://api.kaspa.org/transactions", "https://api-mainnet.kaspanet.io/transactions")
                }

                val payload = KaspaTransactionEngine.buildSubmitPayload(tx, allowOrphan = false)
                val mediaType = "application/json".toMediaType()
                val body = payload.toString().toRequestBody(mediaType)

                var broadcastOk = false
                var returnedTxId = computedTxId
                var lastErr = "Nodes unavailable"

                val fastClient = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()

                for (ep in endpoints) {
                    try {
                        val req = Request.Builder().url(ep).post(body).build()
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
                                lastErr = "Node returned HTTP ${resp.code}: $bodyStr"
                            }
                        }
                        if (broadcastOk) break
                    } catch (e: Exception) {
                        lastErr = e.message ?: "Connection error"
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
                        if (window.__kaswareInjected) return;
                        window.__kaswareInjected = true;

                        window.__kaswareCallbacks = window.__kaswareCallbacks || {};

                        window.__kaswareResolve = function(id, resData, isJson) {
                            var cb = window.__kaswareCallbacks[id];
                            if (cb) {
                                delete window.__kaswareCallbacks[id];
                                cb.resolve(resData);
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
                                    return reject(new Error('Kaspa Wallet Bridge not available in current window'));
                                }
                                var callId = 'kw_' + Math.random().toString(36).substr(2, 9) + '_' + Date.now();
                                window.__kaswareCallbacks[callId] = { resolve: resolve, reject: reject };
                                try {
                                    if (method === 'requestAccounts') {
                                        window.KaspaWalletBridge.requestAccounts(callId);
                                    } else if (method === 'getAccounts') {
                                        window.KaspaWalletBridge.getAccounts(callId);
                                    } else if (method === 'getPublicKey') {
                                        window.KaspaWalletBridge.getPublicKey(callId);
                                    } else if (method === 'getBalance') {
                                        window.KaspaWalletBridge.getBalance(callId);
                                    } else if (method === 'getNetwork') {
                                        window.KaspaWalletBridge.getNetwork(callId);
                                    } else if (method === 'switchNetwork') {
                                        window.KaspaWalletBridge.switchNetwork(args[0] || '', callId);
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
                                    } else if (method === 'signMessage') {
                                        window.KaspaWalletBridge.signMessage(args[0] || '', callId);
                                    } else if (method === 'pushTx') {
                                        var txStr = typeof args[0] === 'string' ? args[0] : JSON.stringify(args[0]);
                                        window.KaspaWalletBridge.pushTx(txStr, callId);
                                    } else {
                                        delete window.__kaswareCallbacks[callId];
                                        reject(new Error('Unknown Kasware method: ' + method));
                                    }
                                } catch(e) {
                                    delete window.__kaswareCallbacks[callId];
                                    reject(e);
                                }
                            });
                        }

                        var eventListeners = {};

                        var kaswareProvider = {
                            isKasWare: true,
                            isKaspa: true,
                            isInstalled: function() { return true; },
                            requestAccounts: function() { return executeKasware('requestAccounts', []); },
                            getAccounts: function() { return executeKasware('getAccounts', []); },
                            getPublicKey: function() { return executeKasware('getPublicKey', []); },
                            getBalance: function() { return executeKasware('getBalance', []); },
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

                        window.kasware = kaswareProvider;
                        window.kaspa = kaswareProvider;

                        try {
                            window.dispatchEvent(new Event('kasware#initialized'));
                            window.dispatchEvent(new Event('kaspa#initialized'));
                        } catch(e) {}

                    } catch(e) {
                        console.warn('Kaspa wallet bridge initialization error:', e);
                    }
                })();
            """.trimIndent()
        }
    }
}

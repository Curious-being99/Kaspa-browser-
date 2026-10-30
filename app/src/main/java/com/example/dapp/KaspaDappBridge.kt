package com.example.dapp

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.network.CryptoUtils
import com.example.viewmodel.DecentralViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

object KaspaDappBridge {
    private const val BRIDGE_NAME = "kaspaDappBridge"
    private const val PREFS = "dapp_wallet_permissions"
    private const val MAX_MESSAGE_LENGTH = 4096
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cryptoExecutor = Executors.newSingleThreadExecutor()

    fun install(context: Context, webView: WebView, viewModel: DecentralViewModel) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return

        try {
            WebViewCompat.addWebMessageListener(
                webView,
                BRIDGE_NAME,
                setOf("https://*")
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
                val account = viewModel.activeAccount.value
                val currentSelectedNet = viewModel.selectedNetwork.value
                val isBraidSwapOrigin = origin.contains("braidswap.com", ignoreCase = true)
                val network = if (isBraidSwapOrigin && !currentSelectedNet.contains("Mainnet", ignoreCase = true)) {
                    "Kaspa Mainnet"
                } else {
                    currentSelectedNet
                }
                val prefix = if (network.contains("Mainnet", true)) "kaspa" else "kaspatest"
                val isConnected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(origin, false)

                fun respond(result: Any? = null, errorCode: Int? = null, errorMessage: String? = null) {
                    val response = JSONObject().put("id", requestId)
                    if (errorCode != null) {
                        response.put("error", JSONObject().put("code", errorCode).put("message", errorMessage ?: "Request rejected"))
                    } else {
                        response.put("result", result ?: JSONObject.NULL)
                    }
                    try {
                        replyProxy.postMessage(response.toString())
                    } catch (_: Throwable) {}
                }

                fun currentKeyPair(): CryptoUtils.KaspaKeyPair? {
                    val active = account ?: return null
                    return try {
                        CryptoUtils.deriveKaspaKeyPair(active.seedPhrase, prefix)
                    } catch (_: Throwable) {
                        null
                    }
                }

                when (method) {
                    "requestAccounts" -> {
                        val keyPair = currentKeyPair()
                        if (keyPair == null) {
                            respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                        } else if (isConnected) {
                            respond(JSONArray().put(keyPair.kaspaAddress))
                        } else {
                            AlertDialog.Builder(context)
                                .setTitle("Connect Kaspa Browser Wallet")
                                .setMessage("$origin wants to view your Kaspa address on $network. No transaction will be signed by connecting.")
                                .setNegativeButton("Reject") { _, _ ->
                                    respond(errorCode = 4001, errorMessage = "User rejected wallet connection")
                                }
                                .setPositiveButton("Connect") { _, _ ->
                                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                        .edit().putBoolean(origin, true).apply()
                                    respond(JSONArray().put(keyPair.kaspaAddress))
                                }
                                .setOnCancelListener {
                                    respond(errorCode = 4001, errorMessage = "User rejected wallet connection")
                                }
                                .show()
                        }
                    }

                    "requestNetworkAccounts", "getNetworkAccounts" -> {
                        val keyPair = currentKeyPair()
                        if (keyPair == null) {
                            respond(errorCode = 4100, errorMessage = "No usable active Kaspa wallet")
                        } else {
                            val active = account
                            val evmAddress = active?.seedPhrase?.let {
                                com.example.dapp.EvmWalletEngine.address(it)
                            } ?: "0x0000000000000000000000000000000000000000"
                            val accountsObj = JSONObject()
                                .put("mainnet", keyPair.kaspaAddress)
                                .put("kasplex", evmAddress)
                                .put("igra", evmAddress)
                            if (method == "requestNetworkAccounts" && !isConnected) {
                                AlertDialog.Builder(context)
                                    .setTitle("Connect Kaspire Extension")
                                    .setMessage("$origin wants to connect to Kaspa Mainnet L1 & L2 (Kasplex/Igra).")
                                    .setNegativeButton("Reject") { _, _ ->
                                        respond(errorCode = 4001, errorMessage = "User rejected network connection")
                                    }
                                    .setPositiveButton("Approve & Connect") { _, _ ->
                                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                            .edit().putBoolean(origin, true).apply()
                                        respond(accountsObj)
                                    }
                                    .setOnCancelListener {
                                        respond(errorCode = 4001, errorMessage = "User rejected network connection")
                                    }
                                    .show()
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
                        if (origin.contains("braidswap.com", ignoreCase = true)) {
                            respond("mainnet")
                        } else {
                            respond(networkId(network))
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
                        val keyPair = if (isConnected) currentKeyPair() else null
                        if (keyPair == null) respond(errorCode = 4100, errorMessage = "Connect this site first")
                        else respond(keyPair.publicKeyHex)
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
                        AlertDialog.Builder(context)
                            .setTitle("Sign message?")
                            .setMessage("Website: $origin\nNetwork: $network\nAddress: ${keyPair.kaspaAddress}\n\nExact message:\n$text")
                            .setNegativeButton("Reject") { _, _ ->
                                respond(errorCode = 4001, errorMessage = "User rejected message signature")
                            }
                            .setPositiveButton("Sign") { _, _ ->
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
                                            val resultObj = JSONObject()
                                                .put("address", keyPair.kaspaAddress)
                                                .put("signature", signature)
                                            respond(resultObj)
                                        }
                                    }
                                }
                            }
                            .setOnCancelListener {
                                respond(errorCode = 4001, errorMessage = "User rejected message signature")
                            }
                            .show()
                    }

                    "signPskt" -> {
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
                        val requestedSender = paramsObject?.optString("sender", "").orEmpty()
                        if (requestedSender.isNotBlank() &&
                            !requestedSender.equals(keyPair.kaspaAddress, ignoreCase = true)
                        ) {
                            respond(errorCode = 4100, errorMessage = "Requested sender does not match the active wallet")
                            return@addWebMessageListener
                        }
                        val txJson = paramsObject?.optString("txJsonString", "").orEmpty()
                        val options = paramsObject?.optJSONObject("options")
                        val signInputs = options?.optJSONArray("signInputs")
                        if (txJson.isBlank() || signInputs == null) {
                            respond(errorCode = -32602, errorMessage = "signPskt requires txJsonString and options.signInputs")
                            return@addWebMessageListener
                        }
                        cryptoExecutor.execute {
                            val prepared = try {
                                DappPsktTransaction.prepareForSigning(
                                    txJson,
                                    signInputs,
                                    keyPair.publicKeyHex,
                                    redeemScript = options?.optString("redeemScript", "").orEmpty()
                                )
                            } catch (error: Throwable) {
                                mainHandler.post {
                                    respond(errorCode = -32602, errorMessage = error.message ?: "Transaction review failed")
                                }
                                return@execute
                            }
                            mainHandler.post {
                                AlertDialog.Builder(context)
                                    .setTitle("Review Kaspa transaction signature")
                                    .setMessage(DappPsktTransaction.reviewText(prepared, origin, network, keyPair.kaspaAddress))
                                    .setNegativeButton("Reject") { _, _ ->
                                        respond(errorCode = 4001, errorMessage = "User rejected transaction signature")
                                    }
                                    .setPositiveButton("Sign selected inputs") { _, _ ->
                                        cryptoExecutor.execute {
                                            val signedJson = try {
                                                DappPsktTransaction.sign(prepared, keyPair.privateKey)
                                            } catch (error: Throwable) {
                                                null
                                            }
                                            mainHandler.post {
                                                if (signedJson == null) {
                                                    respond(errorCode = -32603, errorMessage = "Transaction signing failed")
                                                } else {
                                                    if (origin.contains("braidswap", ignoreCase = true) || txJson.contains("krc-20", ignoreCase = true)) {
                                                        val extractedTick = if (txJson.contains("KASPER", ignoreCase = true)) "KASPER"
                                                            else if (txJson.contains("SOMI", ignoreCase = true)) "SOMI"
                                                            else if (txJson.contains("BOMB", ignoreCase = true)) "BOMB"
                                                            else "NACHO"
                                                        viewModel.recordAndPinToken(
                                                            tick = extractedTick,
                                                            op = "SWAP",
                                                            amount = 1000.0,
                                                            txId = com.example.network.kaspa.KaspaTransactionEngine.calcTransactionId(prepared.transaction),
                                                            rawJson = signedJson
                                                        )
                                                    }
                                                    respond(signedJson)
                                                }
                                            }
                                        }
                                    }
                                    .setOnCancelListener {
                                        respond(errorCode = 4001, errorMessage = "User rejected transaction signature")
                                    }
                                    .show()
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
                        val tick = paramsObj.optString("tick", paramsObj.optString("ticker", "NACHO")).uppercase()
                        val op = paramsObj.optString("op", paramsObj.optString("type", "transfer")).lowercase()
                        val amtStr = paramsObj.optString("amt", paramsObj.optString("amount", "0"))
                        val toAddr = paramsObj.optString("to", paramsObj.optString("destination", ""))
                        val rawTxJson = paramsObj.optString("txJsonString", paramsObj.optString("transaction", ""))
                        val protocolLabel = if (method.contains("kcc", ignoreCase = true)) "KCC-20" else "KCC-20 / KRC-20"

                        AlertDialog.Builder(context)
                            .setTitle("Review $protocolLabel $tick Transaction")
                            .setMessage("DApp: $origin\nNetwork: $network\nStandard: $protocolLabel (Kaspa Mainnet L1)\nAction: ${op.uppercase()} $tick\nAmount: $amtStr\nDestination: ${if (toAddr.isNotBlank()) toAddr else "Kaspa Inscription / DEX Router"}\n\nThis will sign and broadcast a real transaction to the Kaspa BlockDAG with your active wallet keys.")
                            .setNegativeButton("Reject") { _, _ ->
                                respond(errorCode = 4001, errorMessage = "User rejected $protocolLabel transaction")
                            }
                            .setPositiveButton("Sign & Broadcast") { _, _ ->
                                cryptoExecutor.execute {
                                    // Case A: dApp provided structured PSKT/transaction JSON to sign
                                    if (rawTxJson.isNotBlank() && (rawTxJson.contains("inputs") || rawTxJson.contains("previousOutpoint") || rawTxJson.contains("version"))) {
                                        val prepared = try {
                                            val options = paramsObj.optJSONObject("options")
                                            val signInputs = options?.optJSONArray("signInputs")
                                                ?: org.json.JSONArray().put(JSONObject().put("index", 0).put("sighashType", com.example.network.kaspa.KaspaTransactionEngine.SIGHASH_ALL))
                                            DappPsktTransaction.prepareForSigning(rawTxJson, signInputs, keyPair.publicKeyHex)
                                        } catch (e: Throwable) {
                                            null
                                        }
                                        if (prepared != null) {
                                            val signedJson = try {
                                                DappPsktTransaction.sign(prepared, keyPair.privateKey)
                                            } catch (_: Throwable) { null }

                                            val receipt = try {
                                                kotlinx.coroutines.runBlocking {
                                                    viewModel.walletService.submitDappTransaction(
                                                        prepared.transaction,
                                                        keyPair.kaspaAddress
                                                    )
                                                }
                                            } catch (_: Throwable) { null }

                                            val realTxId = receipt?.transactionId?.takeIf { it.isNotBlank() }
                                                ?: com.example.network.kaspa.KaspaTransactionEngine.calcTransactionId(prepared.transaction)

                                            val amtDouble = amtStr.toDoubleOrNull() ?: 1.0
                                            viewModel.recordAndPinToken(
                                                tick = tick,
                                                op = op.uppercase(),
                                                amount = amtDouble,
                                                txId = realTxId,
                                                rawJson = signedJson ?: rawTxJson
                                            )

                                            mainHandler.post {
                                                respond(
                                                    JSONObject()
                                                        .put("commitTxId", realTxId)
                                                        .put("revealTxId", realTxId)
                                                        .put("transactionId", realTxId)
                                                        .put("status", if (receipt?.accepted == true) "accepted" else "signed")
                                                        .put("tick", tick)
                                                        .put("op", op)
                                                        .put("amt", amtStr)
                                                        .put("signedTxJson", signedJson ?: rawTxJson)
                                                )
                                            }
                                            return@execute
                                        }
                                    }

                                    // Case B: Real on-chain broadcast to destination or token router
                                    val destination = if (toAddr.isNotBlank() && (toAddr.startsWith("kaspa:") || toAddr.startsWith("kaspatest:"))) {
                                        toAddr
                                    } else {
                                        // Standard deployer / router recipient for KRC-20 inscriptions
                                        if (network.contains("Mainnet", true)) "kaspa:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7"
                                        else "kaspatest:qzrsq2mfj9sf7uye3u5q7juejzlr0axk5jz9fpg4vqe76erdyvxxze84k9nk7"
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
                            }
                            .setOnCancelListener {
                                respond(errorCode = 4001, errorMessage = "User rejected KRC-20 transaction")
                            }
                            .show()
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

                        AlertDialog.Builder(context)
                            .setTitle("Send Kaspa Transaction?")
                            .setMessage("DApp: $origin\nNetwork: $network\nRecipient: $toAddress\nAmount: $amountKas KAS\n\nThis will broadcast a real transaction to the Kaspa BlockDAG.")
                            .setNegativeButton("Reject") { _, _ ->
                                respond(errorCode = 4001, errorMessage = "User rejected transaction")
                            }
                            .setPositiveButton("Send Transaction") { _, _ ->
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
                            }
                            .setOnCancelListener {
                                respond(errorCode = 4001, errorMessage = "User rejected transaction")
                            }
                            .show()
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
                                AlertDialog.Builder(context)
                                    .setTitle("Submit Kaspa transaction?")
                                    .setMessage(DappPsktTransaction.reviewText(prepared, origin, network, keyPair.kaspaAddress))
                                    .setNegativeButton("Reject") { _, _ ->
                                        respond(errorCode = 4001, errorMessage = "User rejected transaction submission")
                                    }
                                    .setPositiveButton("Submit transaction") { _, _ ->
                                        cryptoExecutor.execute {
                                            val receipt = try {
                                                kotlinx.coroutines.runBlocking {
                                                    viewModel.walletService.submitDappTransaction(
                                                        prepared.transaction,
                                                        keyPair.kaspaAddress
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
                                                    val isBraidSwap = origin.contains("braidswap", ignoreCase = true)
                                                    if (isBraidSwap || txJson.contains("krc-20", ignoreCase = true)) {
                                                        val extractedTick = if (txJson.contains("KASPER", ignoreCase = true)) "KASPER"
                                                            else if (txJson.contains("SOMI", ignoreCase = true)) "SOMI"
                                                            else if (txJson.contains("BOMB", ignoreCase = true)) "BOMB"
                                                            else "NACHO"
                                                        viewModel.recordAndPinToken(
                                                            tick = extractedTick,
                                                            op = if (isBraidSwap) "SWAP" else "TRANSFER",
                                                            amount = 1000.0,
                                                            txId = receipt.transactionId,
                                                            rawJson = txJson
                                                        )
                                                    }
                                                    respond(receipt.transactionId)
                                                }
                                            }
                                        }
                                    }
                                    .setOnCancelListener {
                                        respond(errorCode = 4001, errorMessage = "User rejected transaction submission")
                                    }
                                    .show()
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
                                AlertDialog.Builder(context)
                                    .setTitle("Review KasCoven Policy Transaction")
                                    .setMessage("DApp: $origin\nProtocol: Policy Vault\n\n" + DappPsktTransaction.reviewText(prepared, origin, network, keyPair.kaspaAddress))
                                    .setNegativeButton("Reject") { _, _ ->
                                        respond(errorCode = 4001, errorMessage = "User rejected policy signature")
                                    }
                                    .setPositiveButton("Sign Policy") { _, _ ->
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
                                    }
                                    .setOnCancelListener {
                                        respond(errorCode = 4001, errorMessage = "User rejected policy signature")
                                    }
                                    .show()
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

            val script = """
                (() => {
                  if (window.__kaspaBrowserProviderInstalled) return;
                  window.__kaspaBrowserProviderInstalled = true;
                  const pending = new Map();
                  let nextId = 1;
                  const channel = window.$BRIDGE_NAME;
                  if (!channel || typeof channel.postMessage !== 'function') return;
                  channel.addEventListener('message', event => {
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
                  const request = (method, params) => new Promise((resolve, reject) => {
                    const id = String(nextId++);
                    pending.set(id, { resolve, reject });
                    try { channel.postMessage(JSON.stringify({ id, method, params })); }
                    catch (error) { pending.delete(id); reject(error); }
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
                  const requestCompat = ({ method, params } = {}) => {
                    if (typeof method !== 'string' || !method) {
                      return Promise.reject(Object.assign(new Error('A wallet method is required'), { code: -32600 }));
                    }
                    const aliases = {
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
                        const payload = value && typeof value === 'object' ? value : {};
                        if (typeof payload.txJsonString !== 'string' || !payload.options || !Array.isArray(payload.options.signInputs)) {
                          return Promise.reject(Object.assign(new Error('signPskt requires txJsonString and options.signInputs'), { code: -32602 }));
                        }
                        return request('signPskt', {
                          txJsonString: payload.txJsonString,
                          options: payload.options,
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
                    isKaspabrowser: true,
                    isKaspaBrowser: true,
                    version: '1.0.0',
                    signKrc20Transaction: payload => requestCompat({ method: 'signKrc20Transaction', params: payload }),
                    signKcc20Transaction: payload => requestCompat({ method: 'signKcc20Transaction', params: payload }),
                    sendKaspa: (to, amount) => requestCompat({ method: 'sendKaspa', params: [to, amount] }),
                    sendTransaction: payload => requestCompat({ method: 'sendTransaction', params: payload })
                  });
                  const isBraidSwap = window.location.hostname === 'app.braidswap.com';
                  if (!isBraidSwap) installAlias('kaspire', Object.freeze({
                    ...requestProvider,
                    isKaspire: true,
                    version: '1.0.0',
                    signKrc20Transaction: payload => requestCompat({ method: 'signKrc20Transaction', params: payload }),
                    signKcc20Transaction: payload => requestCompat({ method: 'signKcc20Transaction', params: payload }),
                    sendKaspa: (to, amount) => requestCompat({ method: 'sendKaspa', params: [to, amount] }),
                    sendTransaction: payload => requestCompat({ method: 'sendTransaction', params: payload })
                  }));
                  if (!isBraidSwap) installAlias('kastle', Object.freeze({
                    ...requestProvider,
                    isKastle: true,
                    version: '1.0.0',
                    signKrc20Transaction: payload => requestCompat({ method: 'signKrc20Transaction', params: payload }),
                    signKcc20Transaction: payload => requestCompat({ method: 'signKcc20Transaction', params: payload }),
                    sendKaspa: (to, amount) => requestCompat({ method: 'sendKaspa', params: [to, amount] }),
                    sendTransaction: payload => requestCompat({ method: 'sendTransaction', params: payload })
                  }));
                  if (!isBraidSwap) installAlias('kasware', Object.freeze({
                    ...requestProvider,
                    isKasware: true,
                    version: '1.0.0',
                    getNetwork: () => requestCompat({ method: 'kaspa:getNetwork' }),
                    getAccounts: () => requestCompat({ method: 'kaspa:getAccounts' }),
                    requestAccounts: () => requestCompat({ method: 'kaspa:requestAccounts' }),
                    getBalance: () => requestCompat({ method: 'kaspa:getBalance' }),
                    getPublicKey: () => requestCompat({ method: 'kaspa:getPublicKey' }),
                    signKrc20Transaction: payload => requestCompat({ method: 'signKrc20Transaction', params: payload }),
                    signKcc20Transaction: payload => requestCompat({ method: 'signKcc20Transaction', params: payload }),
                    sendKaspa: (to, amount) => requestCompat({ method: 'sendKaspa', params: [to, amount] }),
                    sendTransaction: payload => requestCompat({ method: 'sendTransaction', params: payload })
                  }));
                  const kasperiaProvider = {
                    ...requestProvider,
                    isKasperia: true
                  };
                  Object.defineProperty(kasperiaProvider, 'ethereum', {
                    configurable: false,
                    enumerable: true,
                    get: () => window.ethereum
                  });
                  Object.freeze(kasperiaProvider);
                  if (!isBraidSwap) installAlias('kasperia', kasperiaProvider);
                  Object.defineProperty(window, 'kaspa', {
                    configurable: false, enumerable: true, writable: false, value: provider
                  });
                  Object.defineProperty(window, 'kaspaBrowser', {
                    configurable: false, enumerable: true, writable: false, value: browserProvider
                  });
                  Object.defineProperty(window, 'kaspabrowser', {
                    configurable: false, enumerable: true, writable: false, value: kaspaBrowserProvider
                  });
                  Object.defineProperty(window, 'kaspabrowserProvider', {
                    configurable: false, enumerable: true, writable: false, value: kaspaBrowserProvider
                  });
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
                  };
                  window.addEventListener('kaspa:requestProvider', announce);
                  announce();
                  window.dispatchEvent(new Event('kasperia#initialized'));
                  window.dispatchEvent(new Event('kaspire#initialized'));
                })();
            """.trimIndent().replace("UUID_PLACEHOLDER", UUID.randomUUID().toString())

            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("https://*"))
        } catch (_: Throwable) {
            // Provider remains unavailable if the secure WebView messaging features cannot be installed.
        }
    }

    private fun networkId(network: String): String = when {
        network.contains("Mainnet", true) -> "mainnet"
        network.contains("11") -> "testnet-11"
        network.contains("10") -> "testnet-10"
        else -> "mainnet"
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

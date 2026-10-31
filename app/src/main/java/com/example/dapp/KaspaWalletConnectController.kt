package com.example.dapp

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import com.example.network.CryptoUtils
import com.example.viewmodel.DecentralViewModel
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.concurrent.Executors

object KaspaWalletConnectController : WalletKit.WalletDelegate {
    private const val CHAIN = "kaspa:mainnet"
    private const val TAG = "KaspaWalletConnect"
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val supportedMethods = setOf(
        "kaspa_getAccounts",
        "kaspa_getNetwork",
        "kaspa_getBalance",
        "kaspa_getPublicKey",
        "kaspa_signPersonal",
        "kaspa_signPskt",
        "kaspa_sendTransaction"
    )
    @Volatile private var activity: Activity? = null
    @Volatile private var viewModel: DecentralViewModel? = null

    fun attach(activity: Activity, viewModel: DecentralViewModel) {
        this.activity = activity
        this.viewModel = viewModel
    }

    fun detach(activity: Activity) {
        if (this.activity === activity) {
            this.activity = null
            this.viewModel = null
        }
    }

    fun pair(uri: String, onError: (String) -> Unit = {}) {
        if (!uri.startsWith("wc:") || uri.length > 4096) {
            onError("Invalid WalletConnect pairing URI")
            return
        }
        WalletKit.pair(Wallet.Params.Pair(uri), onError = { onError(it.throwable.message ?: "Pairing failed") })
    }

    override fun onSessionProposal(
        sessionProposal: Wallet.Model.SessionProposal,
        verifyContext: Wallet.Model.VerifyContext
    ) {
        val vm = viewModel
        val currentActivity = activity
        if (vm == null || currentActivity == null || currentActivity.isFinishing) {
            reject(sessionProposal, "Wallet is not available")
            return
        }
        if (vm.selectedNetwork.value.contains("Kasplex", true) ||
            vm.selectedNetwork.value.contains("Igra", true) ||
            !vm.selectedNetwork.value.contains("Mainnet", true)
        ) {
            reject(sessionProposal, "Switch the wallet to Kaspa Mainnet before connecting")
            return
        }
        val proposal = sessionProposal.requiredNamespaces["kaspa"] ?: sessionProposal.optionalNamespaces["kaspa"]
        if (proposal == null || proposal.chains.orEmpty().isNotEmpty() && CHAIN !in proposal.chains.orEmpty()) {
            reject(sessionProposal, "This proposal does not request kaspa:mainnet")
            return
        }
        val unsupportedRequired = proposal.methods.filterNot { it in supportedMethods }
        val unsupportedEvents = proposal.events.filterNot { it == "accountsChanged" }
        if (unsupportedRequired.isNotEmpty() || unsupportedEvents.isNotEmpty()) {
            reject(
                sessionProposal,
                "Unsupported required Kaspa capabilities: ${(unsupportedRequired + unsupportedEvents).joinToString()}"
            )
            return
        }
        val keyPair = deriveActiveKey(vm) ?: run {
            reject(sessionProposal, "No usable active Kaspa Mainnet account")
            return
        }
        val allowed = proposal.methods.filter { it in supportedMethods }.toSet()
        val peer = sessionProposal.name.ifBlank { sessionProposal.url }
        main.post {
            if (activity !== currentActivity || currentActivity.isFinishing) {
                reject(sessionProposal, "Wallet is not available")
                return@post
            }
            AlertDialog.Builder(currentActivity)
                .setTitle("Connect to $peer?")
                .setMessage(
                    "Kaspa Mainnet (L1)\nAccount: ${keyPair.kaspaAddress}\n\n" +
                    "Requested permissions:\n${allowed.joinToString("\n")}\n\n" +
                    "Connecting only shares the address and approved read methods. " +
                    "Every signature and payment will require a separate confirmation."
                )
                .setNegativeButton("Reject") { _, _ -> reject(sessionProposal, "User rejected connection") }
                .setPositiveButton("Connect") { _, _ ->
                    val namespace = Wallet.Model.Namespace.Session(
                        chains = listOf(CHAIN),
                        accounts = listOf("$CHAIN:${keyPair.kaspaAddress}"),
                        methods = allowed.toList(),
                        events = proposal.events.filter { it == "accountsChanged" }
                    )
                    WalletKit.approveSession(
                        Wallet.Params.SessionApprove(
                            proposerPublicKey = sessionProposal.proposerPublicKey,
                            namespaces = mapOf("kaspa" to namespace),
                            relayProtocol = sessionProposal.relayProtocol
                        ),
                        onError = { android.util.Log.e(TAG, "Session approval failed", it.throwable) }
                    )
                }
                .setOnCancelListener { reject(sessionProposal, "User rejected connection") }
                .show()
        }
    }

    private fun reject(proposal: Wallet.Model.SessionProposal, reason: String) {
        WalletKit.rejectSession(
            Wallet.Params.SessionReject(proposal.proposerPublicKey, reason),
            onError = { android.util.Log.w(TAG, "Session rejection failed", it.throwable) }
        )
    }

    override fun onSessionRequest(
        sessionRequest: Wallet.Model.SessionRequest,
        verifyContext: Wallet.Model.VerifyContext
    ) {
        val vm = viewModel
        val currentActivity = activity
        if (vm == null || currentActivity == null || currentActivity.isFinishing) {
            respondError(sessionRequest, 4100, "Wallet is not available")
            return
        }
        if (sessionRequest.chainId != CHAIN) {
            respondError(sessionRequest, 4901, "Unsupported chain; only kaspa:mainnet is enabled")
            return
        }
        val session = try {
            WalletKit.getListOfActiveSessions().firstOrNull { it.topic == sessionRequest.topic }
        } catch (_: Throwable) { null }
        val namespace = session?.namespaces?.get("kaspa")
        val keyPair = deriveActiveKey(vm)
        if (namespace == null || keyPair == null ||
            "$CHAIN:${keyPair.kaspaAddress}" !in namespace.accounts ||
            sessionRequest.request.method !in namespace.methods
        ) {
            respondError(sessionRequest, 4100, "Session account or method is not authorized")
            return
        }
        val params = parseParams(sessionRequest.request.params)
        when (sessionRequest.request.method) {
            "kaspa_getAccounts" -> respondResult(sessionRequest, JSONArray().put(keyPair.kaspaAddress).toString())
            "kaspa_getNetwork" -> respondResult(sessionRequest, JSONObject.quote("mainnet"))
            "kaspa_getPublicKey" -> respondResult(sessionRequest, JSONObject.quote(keyPair.publicKeyHex))
            "kaspa_getBalance" -> executor.execute {
                try {
                    val balance = kotlinx.coroutines.runBlocking {
                        vm.walletService.fetchDappBalance(keyPair.kaspaAddress, "Kaspa Mainnet")
                    }
                    respondResult(sessionRequest, balance.toString())
                } catch (error: Throwable) {
                    respondError(sessionRequest, -32000, error.message ?: "Live balance query failed")
                }
            }
            "kaspa_signPersonal" -> {
                val message = params.optString("message", params.optString("msg"))
                if (message.isBlank() || message.length > 4096) {
                    respondError(sessionRequest, -32602, "A message of 1-4096 characters is required")
                } else {
                    confirm(currentActivity, "Sign Kaspa message?", "DApp: ${sessionRequest.peerMetaData?.name ?: "Unknown"}\nAccount: ${keyPair.kaspaAddress}\n\nExact message:\n$message", {
                        executor.execute {
                            try {
                                if (deriveActiveKey(vm)?.kaspaAddress != keyPair.kaspaAddress) {
                                    throw IllegalStateException("Active wallet changed; request cancelled")
                                }
                                val signature = CryptoUtils.signKaspaPersonalMessage(
                                    keyPair.privateKey, message.toByteArray(Charsets.UTF_8)
                                )
                                respondResult(sessionRequest, JSONObject()
                                    .put("address", keyPair.kaspaAddress)
                                    .put("signature", signature).toString())
                            } catch (error: Throwable) {
                                respondError(sessionRequest, -32603, error.message ?: "Message signing failed")
                            }
                        }
                    }, { respondError(sessionRequest, 4001, "User rejected message signature") })
                }
            }
            "kaspa_signPskt" -> {
                val txJson = params.optString("txJsonString")
                val options = params.optJSONObject("options")
                val signInputs = options?.optJSONArray("signInputs")
                if (txJson.isBlank() || signInputs == null) {
                    respondError(sessionRequest, -32602, "signPskt requires txJsonString and options.signInputs")
                } else executor.execute {
                    val prepared = try {
                        DappPsktTransaction.prepareForSigning(
                            txJson, signInputs, keyPair.publicKeyHex,
                            redeemScript = options?.optString("redeemScript", "").orEmpty()
                        )
                    } catch (error: Throwable) {
                        respondError(sessionRequest, -32602, error.message ?: "PSKT validation failed")
                        return@execute
                    }
                    main.post {
                        confirm(
                            currentActivity,
                            "Review Kaspa PSKT signature",
                            DappPsktTransaction.reviewText(
                                prepared,
                                sessionRequest.peerMetaData?.url ?: sessionRequest.peerMetaData?.name ?: "Connected dApp",
                                "Kaspa Mainnet",
                                keyPair.kaspaAddress
                            ),
                            {
                                executor.execute {
                                    try {
                                        if (deriveActiveKey(vm)?.kaspaAddress != keyPair.kaspaAddress) {
                                            throw IllegalStateException("Active wallet changed; request cancelled")
                                        }
                                        val signed = DappPsktTransaction.signAndFormat(prepared, keyPair.privateKey)
                                        respondResult(sessionRequest, signed.toString())
                                    } catch (error: Throwable) {
                                        respondError(sessionRequest, -32603, error.message ?: "PSKT signing failed")
                                    }
                                }
                            },
                            { respondError(sessionRequest, 4001, "User rejected PSKT signature") }
                        )
                    }
                }
            }
            "kaspa_sendTransaction" -> {
                val recipient = params.optString("to", params.optString("address")).trim()
                val amount = params.optString("amount", params.optString("amountKas")).toBigDecimalOrNull()
                if (!recipient.startsWith("kaspa:") || recipient.length > 128 ||
                    amount == null || amount <= BigDecimal.ZERO || amount.scale() > 8 ||
                    amount > BigDecimal("1000000000")) {
                    respondError(sessionRequest, -32602, "Valid recipient and KAS amount are required")
                } else {
                    val summary = "DApp: ${sessionRequest.peerMetaData?.name ?: "Unknown"}\n" +
                        "Network: Kaspa Mainnet\nFrom: ${keyPair.kaspaAddress}\nTo: $recipient\nAmount: $amount KAS\n\n" +
                        "This will broadcast a real Kaspa L1 transaction."
                    confirm(currentActivity, "Review Kaspa payment", summary, {
                        executor.execute {
                            try {
                                if (deriveActiveKey(vm)?.kaspaAddress != keyPair.kaspaAddress) {
                                    throw IllegalStateException("Active wallet changed; payment cancelled")
                                }
                                val receipt = kotlinx.coroutines.runBlocking {
                                    vm.walletService.sendKaspa(
                                        senderAddress = keyPair.kaspaAddress,
                                        senderSeed = vm.activeAccount.value?.seedPhrase
                                            ?: throw IllegalStateException("Active account changed"),
                                        recipientAddress = recipient,
                                        amountKas = amount.toDouble()
                                    )
                                }
                                if (receipt.isSuccess) {
                                    respondResult(sessionRequest, JSONObject.quote(receipt.getOrThrow().txId))
                                } else {
                                    respondError(sessionRequest, -32000, receipt.exceptionOrNull()?.message ?: "Kaspa broadcast failed")
                                }
                            } catch (error: Throwable) {
                                respondError(sessionRequest, -32000, error.message ?: "Kaspa broadcast failed")
                            }
                        }
                    }, { respondError(sessionRequest, 4001, "User rejected payment") })
                }
            }
            else -> respondError(sessionRequest, 4200, "Unsupported Kaspa method")
        }
    }

    private fun deriveActiveKey(vm: DecentralViewModel): CryptoUtils.KaspaKeyPair? {
        if (vm.isWalletLocked.value) return null
        if (!vm.selectedNetwork.value.contains("Mainnet", true) ||
            vm.selectedNetwork.value.contains("Kasplex", true) ||
            vm.selectedNetwork.value.contains("Igra", true)
        ) return null
        val seed = vm.activeAccount.value?.seedPhrase ?: return null
        return try {
            CryptoUtils.deriveKaspaKeyPair(CryptoUtils.getDecryptedSeed(seed), "kaspa")
        } catch (_: Throwable) { null }
    }

    private fun parseParams(raw: String): JSONObject = try {
        when {
            raw.startsWith("{") -> JSONObject(raw)
            raw.startsWith("[") -> JSONObject().put("value", JSONArray(raw))
            else -> JSONObject()
        }
    } catch (_: Throwable) { JSONObject() }

    private fun confirm(
        activity: Activity,
        title: String,
        message: String,
        approve: () -> Unit,
        reject: () -> Unit
    ) {
        main.post {
            if (activity.isFinishing || activity.isDestroyed) {
                reject()
                return@post
            }
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Reject") { _, _ -> reject() }
                .setPositiveButton("Continue to biometric approval") { _, _ ->
                    com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                        activity,
                        title = "Authorize Kaspa request",
                        subtitle = "Confirm with fingerprint, face, or device PIN",
                        onSuccess = approve,
                        onError = { reject() }
                    )
                }
                .setOnCancelListener { reject() }
                .show()
        }
    }

    private fun respondResult(request: Wallet.Model.SessionRequest, result: String) {
        WalletKit.respondSessionRequest(
            Wallet.Params.SessionRequestResponse(
                request.topic,
                Wallet.Model.JsonRpcResponse.JsonRpcResult(request.request.id, result)
            ),
            onError = { android.util.Log.e(TAG, "Request response failed", it.throwable) }
        )
    }

    private fun respondError(request: Wallet.Model.SessionRequest, code: Int, message: String) {
        WalletKit.respondSessionRequest(
            Wallet.Params.SessionRequestResponse(
                request.topic,
                Wallet.Model.JsonRpcResponse.JsonRpcError(request.request.id, code, message)
            ),
            onError = { android.util.Log.e(TAG, "Error response failed", it.throwable) }
        )
    }

    override fun onSessionDelete(sessionDelete: Wallet.Model.SessionDelete) {}
    override fun onSessionExtend(session: Wallet.Model.Session) {}
    override fun onSessionSettleResponse(settleSessionResponse: Wallet.Model.SettledSessionResponse) {}
    override fun onSessionUpdateResponse(sessionUpdateResponse: Wallet.Model.SessionUpdateResponse) {}
    override fun onConnectionStateChange(state: Wallet.Model.ConnectionState) {}
    override fun onError(error: Wallet.Model.Error) {
        android.util.Log.e(TAG, "WalletConnect error", error.throwable)
    }
}

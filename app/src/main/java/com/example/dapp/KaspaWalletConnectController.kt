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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
        "kaspa_switchNetwork",
        "kaspa_signPersonal",
        "kaspa_signAuth",
        "kaspa_signPskt",
        "kaspa_signVaultTransaction",
        "kaspa_sendTransaction",
        "kaspa_sendKcc20"
    )
    private val evmMethods = setOf(
        "eth_accounts", "eth_requestAccounts", "eth_chainId", "net_version",
        "eth_blockNumber", "eth_getBalance", "eth_getCode", "eth_call",
        "eth_estimateGas", "eth_gasPrice", "eth_maxPriorityFeePerGas",
        "eth_getTransactionCount", "eth_getBlockByNumber", "eth_getBlockByHash",
        "eth_getTransactionByHash", "eth_getTransactionReceipt", "eth_getLogs",
        "eth_feeHistory", "eth_getStorageAt", "web3_clientVersion",
        "wallet_switchEthereumChain", "personal_sign", "eth_sign",
        "eth_signTypedData_v4", "eth_signTypedData_v3", "eth_signTypedData",
        "eth_sendTransaction", "eth_signTransaction"
    )
    private val evmReadMethods = setOf(
        "eth_blockNumber", "eth_getBalance", "eth_getCode", "eth_call",
        "eth_estimateGas", "eth_gasPrice", "eth_maxPriorityFeePerGas",
        "eth_getTransactionCount", "eth_getBlockByNumber", "eth_getBlockByHash",
        "eth_getTransactionByHash", "eth_getTransactionReceipt", "eth_getLogs",
        "eth_feeHistory", "eth_getStorageAt", "eth_chainId", "net_version",
        "web3_clientVersion"
    )
    private const val KB_COVENANT_ID = "b75a6e90f569965bdeec23c460ebd59e0f0445d8aa8f5d4330fe15c66a47b4d8"
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var accountObserver: Job? = null
    private data class AccountSnapshot(
        val kaspaAddress: String?,
        val evmAddress: String?,
        val evmChainId: Long?
    )
    private data class EvmAccountContext(
        val accountId: String,
        val seedPhrase: String,
        val passphrase: String,
        val address: String
    )
    @Volatile private var activity: Activity? = null
    @Volatile private var viewModel: DecentralViewModel? = null
    @Volatile private var walletKitReady = false
    private val pairingLock = Any()
    private val pendingPairings = mutableListOf<Pair<String, (String) -> Unit>>()

    fun attach(activity: Activity, viewModel: DecentralViewModel) {
        this.activity = activity
        this.viewModel = viewModel
        accountObserver?.cancel()
        accountObserver = observerScope.launch {
            var previous: AccountSnapshot? = null
            combine(viewModel.activeAccount, viewModel.selectedNetwork, viewModel.isWalletLocked) { account, network, locked ->
                val kaspaAddress = if (account != null && !locked) {
                    runCatching {
                        val seed = CryptoUtils.getDecryptedSeed(account.seedPhrase)
                        val key = CryptoUtils.deriveKaspaKeyPair(seed, "kaspa")
                        try { key.kaspaAddress } finally { key.wipe() }
                    }.getOrNull()
                } else null
                val evmAddress = if (account != null && !locked) {
                    runCatching {
                        val accountId = account.did.takeIf { it.isNotBlank() } ?: account.handle
                        val passphrase = EvmPassphraseVault.load(activity, accountId)
                        val standard = EvmWalletEngine.isStandardMnemonic(account.seedPhrase)
                        val address = EvmWalletEngine.address(account.seedPhrase, passphrase)
                        if (!standard && !EvmPassphraseVault.isLegacyPhraseConfirmed(activity, accountId, address)) {
                            null
                        } else address
                    }.getOrNull()
                } else null
                val chainId = if (network.contains("Kasplex", true) || network.contains("Igra", true)) {
                    runCatching { viewModel.getEvmChainIdForNetwork(network) }.getOrNull()
                } else null
                AccountSnapshot(kaspaAddress, evmAddress, chainId)
            }.collect { current ->
                val old = previous
                if (old != null && old.kaspaAddress != current.kaspaAddress && current.kaspaAddress != null) {
                    publishKaspaAccountsChanged(current.kaspaAddress)
                }
                if (old != null && old.evmAddress != current.evmAddress && current.evmAddress != null) {
                    publishEvmAccountsChanged(current.evmAddress)
                }
                if (old != null && old.evmChainId != current.evmChainId && current.evmChainId != null) {
                    publishEvmChainChanged(current.evmChainId)
                }
                previous = current
            }
        }
    }

    fun detach(activity: Activity) {
        if (this.activity === activity) {
            accountObserver?.cancel()
            accountObserver = null
            this.activity = null
            this.viewModel = null
        }
    }

    private fun publishKaspaAccountsChanged(address: String) {
        executor.execute {
            runCatching {
                WalletKit.getListOfActiveSessions().forEach { session ->
                    val namespace = session.namespaces["kaspa"] ?: return@forEach
                    if (CHAIN !in namespace.chains.orEmpty() || "accountsChanged" !in namespace.events) return@forEach
                    val updated = session.namespaces.toMutableMap().apply {
                        put("kaspa", namespace.copy(accounts = listOf("$CHAIN:$address")))
                    }
                    WalletKit.updateSession(
                        Wallet.Params.SessionUpdate(session.topic, updated),
                        onSuccess = {
                            WalletKit.emitSessionEvent(
                                Wallet.Params.SessionEmit(
                                    session.topic,
                                    Wallet.Model.SessionEvent("accountsChanged", JSONArray().put(address).toString()),
                                    CHAIN
                                ),
                                onError = { android.util.Log.w(TAG, "Kaspa accountsChanged event failed", it.throwable) }
                            )
                        },
                        onError = { android.util.Log.w(TAG, "Kaspa session account update failed", it.throwable) }
                    )
                }
            }.onFailure { android.util.Log.w(TAG, "Kaspa account-change propagation failed", it) }
        }
    }

    private fun publishEvmAccountsChanged(address: String) {
        executor.execute {
            runCatching {
                WalletKit.getListOfActiveSessions().forEach { session ->
                    val namespace = session.namespaces["eip155"] ?: return@forEach
                    if ("accountsChanged" !in namespace.events) return@forEach
                    val chains = namespace.chains.orEmpty().filter { it == "eip155:202555" || it == "eip155:38833" }
                    if (chains.isEmpty()) return@forEach
                    val updated = session.namespaces.toMutableMap().apply {
                        put("eip155", namespace.copy(accounts = chains.map { "$it:$address" }))
                    }
                    WalletKit.updateSession(
                        Wallet.Params.SessionUpdate(session.topic, updated),
                        onSuccess = {
                            chains.forEach { chain ->
                                WalletKit.emitSessionEvent(
                                    Wallet.Params.SessionEmit(
                                        session.topic,
                                        Wallet.Model.SessionEvent("accountsChanged", JSONArray().put(address).toString()),
                                        chain
                                    ),
                                    onError = { android.util.Log.w(TAG, "EVM accountsChanged event failed", it.throwable) }
                                )
                            }
                        },
                        onError = { android.util.Log.w(TAG, "EVM session account update failed", it.throwable) }
                    )
                }
            }.onFailure { android.util.Log.w(TAG, "EVM account-change propagation failed", it) }
        }
    }

    private fun publishEvmChainChanged(chainId: Long) {
        val chain = "eip155:$chainId"
        val hex = EvmWalletEngine.network(chainId).chainIdHex
        executor.execute {
            runCatching {
                WalletKit.getListOfActiveSessions().forEach { session ->
                    val namespace = session.namespaces["eip155"] ?: return@forEach
                    if (chain !in namespace.chains.orEmpty() || "chainChanged" !in namespace.events) return@forEach
                    WalletKit.emitSessionEvent(
                        Wallet.Params.SessionEmit(
                            session.topic,
                            Wallet.Model.SessionEvent("chainChanged", JSONObject.quote(hex)),
                            chain
                        ),
                        onError = { android.util.Log.w(TAG, "EVM chainChanged event failed", it.throwable) }
                    )
                }
            }.onFailure { android.util.Log.w(TAG, "EVM chain-change propagation failed", it) }
        }
    }

    fun onWalletKitInitialized() {
        val pending = synchronized(pairingLock) {
            walletKitReady = true
            pendingPairings.toList().also { pendingPairings.clear() }
        }
        pending.forEach { (uri, onError) -> pairNow(uri, onError) }
    }

    fun onWalletKitInitializationFailed(reason: String) {
        val pending = synchronized(pairingLock) {
            walletKitReady = false
            pendingPairings.toList().also { pendingPairings.clear() }
        }
        pending.forEach { (_, onError) -> onError(reason) }
    }

    fun pair(uri: String, onError: (String) -> Unit = {}) {
        if (!uri.startsWith("wc:") || uri.length > 4096) {
            onError("Invalid WalletConnect pairing URI")
            return
        }
        val shouldQueue = synchronized(pairingLock) {
            if (walletKitReady) false else {
                if (pendingPairings.size >= 5) {
                    onError("Too many WalletConnect pairing requests are pending")
                    return
                }
                pendingPairings.add(uri to onError)
                true
            }
        }
        if (!shouldQueue) pairNow(uri, onError)
    }

    private fun pairNow(uri: String, onError: (String) -> Unit) {
        try {
            WalletKit.pair(
                Wallet.Params.Pair(uri),
                onError = { onError("WalletConnect pairing failed; check the connection and try again") }
            )
        } catch (_: Throwable) {
            onError("WalletConnect is not ready; try pairing again")
        }
    }

    private fun evmAccountContext(vm: DecentralViewModel, currentActivity: Activity): EvmAccountContext? {
        if (vm.isWalletLocked.value) return null
        val account = vm.activeAccount.value ?: return null
        val accountId = account.did.takeIf { it.isNotBlank() } ?: account.handle
        return try {
            val passphrase = EvmPassphraseVault.load(currentActivity, accountId)
            val standardMnemonic = EvmWalletEngine.isStandardMnemonic(account.seedPhrase)
            val address = EvmWalletEngine.address(account.seedPhrase, passphrase)
            if (!standardMnemonic &&
                !EvmPassphraseVault.isLegacyPhraseConfirmed(currentActivity, accountId, address)
            ) return null
            EvmAccountContext(accountId, account.seedPhrase, passphrase, address)
        } catch (_: Throwable) {
            null
        }
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
        val supportedNamespaces = setOf("kaspa", "eip155")
        val unsupportedRequiredNamespaces = sessionProposal.requiredNamespaces.keys.filterNot { it in supportedNamespaces }
        if (unsupportedRequiredNamespaces.isNotEmpty()) {
            reject(sessionProposal, "Unsupported required namespaces: ${unsupportedRequiredNamespaces.joinToString()}")
            return
        }

        val approved = linkedMapOf<String, Wallet.Model.Namespace.Session>()
        val reviewLines = mutableListOf<String>()

        val kaspaProposal = sessionProposal.requiredNamespaces["kaspa"]
            ?: sessionProposal.optionalNamespaces["kaspa"]
        if (kaspaProposal != null) {
            val requestedChains = kaspaProposal.chains.orEmpty().ifEmpty { listOf(CHAIN) }
            val requiredKaspa = "kaspa" in sessionProposal.requiredNamespaces
            val validChains = requestedChains.filter { it == CHAIN }
            val unsupportedChains = requestedChains - validChains.toSet()
            val unsupportedMethods = kaspaProposal.methods.filterNot { it in supportedMethods }
            val unsupportedEvents = kaspaProposal.events.filterNot { it == "accountsChanged" }
            if (requiredKaspa && (unsupportedChains.isNotEmpty() || unsupportedMethods.isNotEmpty() || unsupportedEvents.isNotEmpty())) {
                reject(
                    sessionProposal,
                    "Unsupported required Kaspa capabilities: ${(unsupportedChains + unsupportedMethods + unsupportedEvents).joinToString()}"
                )
                return
            }
            if (validChains.isNotEmpty()) {
                val keyPair = deriveActiveKey(vm)
                if (keyPair == null) {
                    if (requiredKaspa) {
                        reject(sessionProposal, "Unlock the wallet and select Kaspa Mainnet before connecting")
                        return
                    }
                } else {
                    val address = keyPair.kaspaAddress
                    keyPair.wipe()
                    val methods = kaspaProposal.methods.filter { it in supportedMethods }
                    val events = kaspaProposal.events.filter { it == "accountsChanged" }
                    approved["kaspa"] = Wallet.Model.Namespace.Session(
                        chains = validChains,
                        accounts = validChains.map { "$it:$address" },
                        methods = methods,
                        events = events
                    )
                    reviewLines += "Kaspa Mainnet L1: $address"
                    reviewLines += "Kaspa methods: ${methods.joinToString()}"
                }
            }
        }

        val evmProposal = sessionProposal.requiredNamespaces["eip155"]
            ?: sessionProposal.optionalNamespaces["eip155"]
        if (evmProposal != null) {
            val requestedChains = evmProposal.chains.orEmpty()
            val validChains = requestedChains.filter { it in setOf("eip155:202555", "eip155:38833") }
            val unsupportedChains = requestedChains - validChains.toSet()
            val unsupportedMethods = evmProposal.methods.filterNot { it in evmMethods }
            val unsupportedEvents = evmProposal.events.filterNot { it in setOf("accountsChanged", "chainChanged") }
            val requiredEvm = "eip155" in sessionProposal.requiredNamespaces
            if (requiredEvm && (validChains.isEmpty() || unsupportedChains.isNotEmpty() ||
                    unsupportedMethods.isNotEmpty() || unsupportedEvents.isNotEmpty())
            ) {
                reject(
                    sessionProposal,
                    "Unsupported required EVM capabilities: ${(unsupportedChains + unsupportedMethods + unsupportedEvents).joinToString()}"
                )
                return
            }
            if (validChains.isNotEmpty()) {
                val evmAccount = evmAccountContext(vm, currentActivity)
                if (evmAccount == null) {
                    if (requiredEvm) {
                        reject(sessionProposal, "The active account has no verified EVM derivation or its saved passphrase is unavailable")
                        return
                    }
                } else {
                    val methods = evmProposal.methods.filter { it in evmMethods }
                    val events = evmProposal.events.filter { it in setOf("accountsChanged", "chainChanged") }
                    approved["eip155"] = Wallet.Model.Namespace.Session(
                        chains = validChains,
                        accounts = validChains.map { "$it:${evmAccount.address}" },
                        methods = methods,
                        events = events
                    )
                    reviewLines += "Kasplex / Igra EVM: ${evmAccount.address}"
                    reviewLines += "EVM chains: ${validChains.joinToString()}"
                    reviewLines += "EVM methods: ${methods.joinToString()}"
                }
            }
        }

        if (approved.isEmpty()) {
            reject(sessionProposal, "No supported, authorized namespace was requested")
            return
        }
        val peer = sessionProposal.name.ifBlank { sessionProposal.url }
        val verification = when (verifyContext.validation) {
            Wallet.Model.Validation.VALID -> "Verified origin"
            Wallet.Model.Validation.INVALID -> "Origin verification failed"
            Wallet.Model.Validation.UNKNOWN -> "Origin not verified"
        }
        val scamWarning = if (verifyContext.isScam == true) "\\nWarning: Reown has flagged this origin as suspicious" else ""
        main.post {
            if (activity !== currentActivity || currentActivity.isFinishing) {
                reject(sessionProposal, "Wallet is not available")
                return@post
            }
            AlertDialog.Builder(currentActivity)
                .setTitle("Connect to $peer?")
                .setMessage(
                    "Origin: ${sessionProposal.url}\\n$verification$scamWarning\\n\\n" +
                    reviewLines.joinToString("\\n") +
                    "\\n\\nEvery signature and payment requires a separate review and biometric/device approval."
                )
                .setNegativeButton("Reject") { _, _ -> reject(sessionProposal, "User rejected connection") }
                .setPositiveButton("Connect") { _, _ ->
                    WalletKit.approveSession(
                        Wallet.Params.SessionApprove(
                            proposerPublicKey = sessionProposal.proposerPublicKey,
                            namespaces = approved,
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
        val requestChain = sessionRequest.chainId.orEmpty()
        if (requestChain.startsWith("eip155:")) {
            handleEvmSessionRequest(vm, currentActivity, sessionRequest, requestChain)
            return
        }
        if (requestChain != CHAIN) {
            respondError(sessionRequest, 4901, "Unsupported chain; only approved Kaspa Mainnet and Kasplex/Igra chains are enabled")
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
            keyPair?.wipe()
            respondError(sessionRequest, 4100, "Session account or method is not authorized")
            return
        }
        val params = parseParams(sessionRequest.request.params)
        when (sessionRequest.request.method) {
            "kaspa_getAccounts" -> {
                respondResult(sessionRequest, JSONArray().put(keyPair.kaspaAddress).toString())
                keyPair.wipe()
            }
            "kaspa_getNetwork" -> {
                respondResult(sessionRequest, JSONObject.quote("mainnet"))
                keyPair.wipe()
            }
            "kaspa_getPublicKey" -> {
                respondResult(sessionRequest, JSONObject.quote(keyPair.publicKeyHex))
                keyPair.wipe()
            }
            "kaspa_getBalance" -> executor.execute {
                try {
                    val balance = kotlinx.coroutines.runBlocking {
                        vm.walletService.fetchDappBalance(keyPair.kaspaAddress, "Kaspa Mainnet")
                    }
                    val balanceSompi = balance.optString("balance").toBigDecimalOrNull()
                        ?: throw IllegalStateException("Live Kaspa balance is malformed")
                    val balanceKas = balanceSompi.movePointLeft(8).stripTrailingZeros().toPlainString()
                    respondResult(sessionRequest, JSONObject.quote(balanceKas))
                } catch (error: Throwable) {
                    respondError(sessionRequest, -32000, error.message ?: "Live balance query failed")
                } finally {
                    keyPair.wipe()
                }
            }
            "kaspa_signPersonal" -> {
                val requestedAddress = params.optString("address", keyPair.kaspaAddress).trim().lowercase()
                val message = params.optString("message", params.optString("msg"))
                if (requestedAddress != keyPair.kaspaAddress.lowercase() ||
                    message.isBlank() || message.length > 4096) {
                    keyPair.wipe()
                    respondError(sessionRequest, -32602, "The active account address and a message of 1-4096 characters are required")
                } else {
                    confirm(currentActivity, "Sign Kaspa message?", "DApp: ${sessionRequest.peerMetaData?.name ?: "Unknown"}\nAccount: ${keyPair.kaspaAddress}\n\nExact message:\n$message", {
                        executor.execute {
                            try {
                                if (activeAddress(vm) != keyPair.kaspaAddress) {
                                    throw IllegalStateException("Active wallet changed; request cancelled")
                                }
                                val signature = CryptoUtils.signKaspaPersonalMessage(
                                    keyPair.privateKey, message.toByteArray(Charsets.UTF_8)
                                )
                                respondResult(sessionRequest, JSONObject.quote(signature))
                            } catch (error: Throwable) {
                                respondError(sessionRequest, -32603, error.message ?: "Message signing failed")
                            } finally {
                                keyPair.wipe()
                            }
                        }
                    }, {
                        keyPair.wipe()
                        respondError(sessionRequest, 4001, "User rejected message signature")
                    })
                }
            }
            "kaspa_signPskt" -> {
                val txJson = params.optString("txJsonString")
                val options = params.optJSONObject("options")
                val signInputs = options?.optJSONArray("signInputs")
                if (txJson.isBlank() || signInputs == null) {
                    keyPair.wipe()
                    respondError(sessionRequest, -32602, "signPskt requires txJsonString and options.signInputs")
                } else executor.execute {
                    val prepared = try {
                        DappPsktTransaction.prepareForSigning(
                            txJson, signInputs, keyPair.publicKeyHex,
                            redeemScript = options?.optString("redeemScript", "").orEmpty()
                        )
                    } catch (error: Throwable) {
                        keyPair.wipe()
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
                                        if (activeAddress(vm) != keyPair.kaspaAddress) {
                                            throw IllegalStateException("Active wallet changed; request cancelled")
                                        }
                                        val signed = DappPsktTransaction.signAndFormat(prepared, keyPair.privateKey)
                                        respondResult(sessionRequest, signed.toString())
                                    } catch (error: Throwable) {
                                        respondError(sessionRequest, -32603, error.message ?: "PSKT signing failed")
                                    } finally {
                                        keyPair.wipe()
                                    }
                                }
                            },
                            {
                                keyPair.wipe()
                                respondError(sessionRequest, 4001, "User rejected PSKT signature")
                            }
                        )
                    }
                }
            }
            "kaspa_sendTransaction" -> {
                val requestedFrom = params.optString("from").trim().lowercase()
                val recipient = params.optString("to").trim().lowercase()
                val amountSompi = params.optString("amountSompi").toLongOrNull()
                if (requestedFrom != keyPair.kaspaAddress.lowercase() ||
                    !CryptoUtils.isValidKaspaAddress(recipient, "kaspa") ||
                    amountSompi == null || amountSompi <= 0L ||
                    amountSompi > 2_900_000_000_000_000L) {
                    keyPair.wipe()
                    respondError(sessionRequest, -32602, "A matching active from address, valid Kaspa Mainnet destination, and positive integer amountSompi are required")
                } else {
                    val summary = "DApp: ${sessionRequest.peerMetaData?.name ?: "Unknown"}\n" +
                        "Network: Kaspa Mainnet\nFrom: ${keyPair.kaspaAddress}\nTo: $recipient\nAmount: ${BigDecimal.valueOf(amountSompi, 8)} KAS ($amountSompi sompi)\n\n" +
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
                                        amountKas = amountSompi.toDouble() / 100_000_000.0
                                    )
                                }
                                if (receipt.isSuccess) {
                                    respondResult(sessionRequest, JSONObject.quote(receipt.getOrThrow().txId))
                                } else {
                                    respondError(sessionRequest, -32000, receipt.exceptionOrNull()?.message ?: "Kaspa broadcast failed")
                                }
                            } catch (error: Throwable) {
                                respondError(sessionRequest, -32000, error.message ?: "Kaspa broadcast failed")
                            } finally {
                                keyPair.wipe()
                            }
                        }
                    }, {
                        keyPair.wipe()
                        respondError(sessionRequest, 4001, "User rejected payment")
                    })
                }
            }
            else -> {
                keyPair.wipe()
                respondError(sessionRequest, 4200, "Unsupported Kaspa method")
            }
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

    private fun activeAddress(vm: DecentralViewModel): String? {
        if (!vm.selectedNetwork.value.contains("Mainnet", true) ||
            vm.selectedNetwork.value.contains("Kasplex", true) ||
            vm.selectedNetwork.value.contains("Igra", true)
        ) return null
        val encryptedSeed = vm.activeAccount.value?.seedPhrase ?: return null
        val keyPair = try {
            CryptoUtils.deriveKaspaKeyPair(encryptedSeed, "kaspa")
        } catch (_: Throwable) { return null }
        return try { keyPair.kaspaAddress } finally { keyPair.wipe() }
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

    private fun decodeJsonRpcValue(value: Any?): Any? {
        if (value === JSONObject.NULL) return null
        return when (value) {
            is JSONObject -> value.keys().asSequence().associateWith { key ->
                decodeJsonRpcValue(value.get(key))
            }
            is JSONArray -> (0 until value.length()).map { index ->
                decodeJsonRpcValue(value.get(index))
            }
            else -> value
        }
    }

    private fun respondResult(request: Wallet.Model.SessionRequest, result: String) {
        try {
            val typedResult = decodeJsonRpcValue(JSONTokener(result).nextValue())
            SignClient.respond(
                Sign.Params.Response(
                    request.topic,
                    Sign.Model.JsonRpcResponse.JsonRpcResult(request.request.id, typedResult)
                ),
                onError = { android.util.Log.e(TAG, "Request response failed", it.throwable) }
            )
        } catch (error: Throwable) {
            respondError(request, -32603, error.message ?: "Unable to encode JSON-RPC result")
        }
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

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
                val network = viewModel.selectedNetwork.value
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

                    "getAccounts" -> {
                        val keyPair = if (isConnected) currentKeyPair() else null
                        respond(if (keyPair == null) JSONArray() else JSONArray().put(keyPair.kaspaAddress))
                    }

                    "getNetwork" -> respond(networkId(network))

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
                                        else respond(signature)
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
                                DappPsktTransaction.prepareForSigning(txJson, signInputs, keyPair.publicKeyHex)
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
                                                if (signedJson == null) respond(errorCode = -32603, errorMessage = "Transaction signing failed")
                                                else respond(signedJson)
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

                    "submitTransaction" -> {
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
                            respond(errorCode = -32602, errorMessage = "submitTransaction requires a signed transaction JSON string")
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
                                                    respond(JSONObject().put("transactionId", receipt.transactionId).put("status", "accepted"))
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

                    "disconnect" -> {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().remove(origin).apply()
                        respond(JSONObject().put("disconnected", true))
                    }

                    else -> respond(errorCode = -32601, errorMessage = "Unsupported Kaspa wallet method")
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
                  const provider = Object.freeze({
                    requestAccounts: () => request('requestAccounts'),
                    getAccounts: () => request('getAccounts'),
                    getNetwork: () => request('getNetwork'),
                    getPublicKey: () => request('getPublicKey'),
                    signMessage: message => request('signMessage', message),
                    signPskt: ({ txJsonString, options }) => request('signPskt', { txJsonString, options }),
                    submitTransaction: txJsonString => request('submitTransaction', txJsonString),
                    disconnect: () => request('disconnect')
                  });
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
                      kaspa_getPublicKey: 'getPublicKey',
                      kaspa_signMessage: 'signMessage',
                      kaspa_signPskt: 'signPskt',
                      kaspa_submitTransaction: 'submitTransaction',
                      kasperia_requestAccounts: 'requestAccounts',
                      kasperia_accounts: 'getAccounts',
                      kasperia_getNetwork: 'getNetwork',
                      kasperia_getPublicKey: 'getPublicKey',
                      kasperia_signMessage: 'signMessage',
                      kasperia_signPskt: 'signPskt',
                      kasperia_submitTransaction: 'submitTransaction',
                      kasperia_disconnect: 'disconnect',
                      pushTx: 'submitTransaction'
                    };
                    const normalized = aliases[method] || method;
                    const value = Array.isArray(params) ? params[0] : params;
                    switch (normalized) {
                      case 'requestAccounts': return request('requestAccounts');
                      case 'getAccounts': return request('getAccounts');
                      case 'getNetwork': return request('getNetwork');
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
                      case 'disconnect': return request('disconnect');
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
                        return Promise.reject(Object.assign(new Error('Unsupported Kaspa Browser wallet method: ' + method), { code: 4200 }));
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
                  installAlias('kaspire', requestProvider);
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
                  installAlias('kasperia', kasperiaProvider);
                  Object.defineProperty(window, 'kaspa', {
                    configurable: false, enumerable: true, writable: false, value: provider
                  });
                  Object.defineProperty(window, 'kaspaBrowser', {
                    configurable: false, enumerable: true, writable: false, value: browserProvider
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
                    methods: ['kaspa:requestAccounts', 'kaspa:getAccounts', 'kaspa:getNetwork', 'kaspa:getPublicKey', 'kaspa:signMessage', 'kaspa:signPskt', 'kaspa:submitTransaction'],
                    uuid: 'UUID_PLACEHOLDER',
                    rdns: 'org.kaspa.browser'
                  });
                  const announce = () => {
                    const detail = Object.freeze({ info, provider: requestProvider });
                    window.dispatchEvent(new CustomEvent('kaspa:provider', { detail }));
                  };
                  window.addEventListener('kaspa:requestProvider', announce);
                  announce();
                  window.dispatchEvent(new Event('kasperia#initialized'));
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
        else -> "testnet-10"
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

package com.example.dapp

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.viewmodel.DecentralViewModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object EvmDappBridge {
    private const val BRIDGE = "kaspaBrowserEvmBridge"
    private const val PREFS = "evm_dapp_permissions"
    private const val CHAIN_PREFS = "evm_dapp_chains"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val readMethods = setOf(
        "eth_blockNumber", "eth_getBalance", "eth_getCode", "eth_call",
        "eth_estimateGas", "eth_gasPrice", "eth_maxPriorityFeePerGas",
        "eth_getTransactionCount", "eth_getBlockByNumber", "eth_getBlockByHash",
        "eth_getTransactionByHash", "eth_getTransactionReceipt", "eth_getLogs",
        "eth_feeHistory", "eth_getStorageAt", "eth_chainId", "net_version",
        "web3_clientVersion"
    )

    fun install(context: Context, webView: WebView, viewModel: DecentralViewModel) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return
        try {
            WebViewCompat.addWebMessageListener(webView, BRIDGE, setOf("https://*")) { view, message, sourceOrigin, isMainFrame, replyProxy ->
                val origin = normalizeOrigin(sourceOrigin.toString())
                if (!isMainFrame || origin == null || origin != normalizeOrigin(view.url.orEmpty())) return@addWebMessageListener
                val request = try {
                    val data = message.data ?: return@addWebMessageListener
                    if (data.length > 512 * 1024) return@addWebMessageListener
                    JSONObject(data)
                } catch (_: Exception) { return@addWebMessageListener }
                val requestId = request.opt("id") ?: JSONObject.NULL
                val method = request.optString("method")
                val params = request.optJSONArray("params") ?: JSONArray()
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val connected = prefs.getBoolean(origin, false)
                val account = viewModel.activeAccount.value
                val seedPhrase = account?.seedPhrase.orEmpty()
                val chainPrefs = context.getSharedPreferences(CHAIN_PREFS, Context.MODE_PRIVATE)
                val chain = try {
                    EvmWalletEngine.network(chainPrefs.getLong(origin, 202555L))
                } catch (_: Exception) {
                    EvmWalletEngine.network(202555L)
                }

                fun respond(result: Any? = null, code: Int? = null, error: String? = null) {
                    val envelope = JSONObject().put("id", requestId)
                    if (code != null) envelope.put("error", JSONObject().put("code", code).put("message", error ?: "Request rejected"))
                    else envelope.put("result", result ?: JSONObject.NULL)
                    try { replyProxy.postMessage(envelope.toString()) } catch (_: Throwable) {}
                }

                fun rpc(network: EvmWalletEngine.Network, rpcMethod: String, rpcParams: JSONArray): Any? {
                    val response = EvmWalletEngine.rpc(network, rpcMethod, rpcParams)
                    return response.opt("result")
                }

                fun requestConnection() {
                    if (seedPhrase.isBlank()) {
                        respond(code = 4100, error = "No active wallet is available")
                        return
                    }
                    val evmAddress = try { EvmWalletEngine.address(seedPhrase) } catch (e: Throwable) {
                        respond(code = 4100, error = e.message ?: "EVM account derivation failed")
                        return
                    }
                    if (connected) {
                        respond(JSONArray().put(evmAddress))
                        return
                    }
                    mainHandler.post {
                        AlertDialog.Builder(context)
                            .setTitle("Connect EVM wallet?")
                            .setMessage("$origin wants to view your EVM address on ${chain.name}. Connecting does not authorize signing or spending.")
                            .setNegativeButton("Reject") { _, _ -> respond(code = 4001, error = "User rejected wallet connection") }
                            .setPositiveButton("Connect") { _, _ ->
                                prefs.edit().putBoolean(origin, true).apply()
                                respond(JSONArray().put(evmAddress))
                            }
                            .setOnCancelListener { respond(code = 4001, error = "User rejected wallet connection") }
                            .show()
                    }
                }

                when (method) {
                    "eth_requestAccounts" -> requestConnection()
                    "eth_accounts" -> {
                        if (!connected || seedPhrase.isBlank()) respond(JSONArray())
                        else try { respond(JSONArray().put(EvmWalletEngine.address(seedPhrase))) }
                        catch (e: Throwable) { respond(code = 4100, error = e.message ?: "EVM account unavailable") }
                    }
                    "eth_chainId" -> respond(chain.chainIdHex)
                    "net_version" -> respond(chain.chainId.toString())
                    "wallet_switchEthereumChain" -> {
                        val first = params.optJSONObject(0)
                        val requested = first?.optString("chainId").orEmpty()
                        val target = try { EvmWalletEngine.networkFromHex(requested) } catch (_: Throwable) {
                            respond(code = 4902, error = "Only Kasplex Mainnet and Igra Mainnet are supported")
                            return@addWebMessageListener
                        }
                        mainHandler.post {
                            AlertDialog.Builder(context)
                                .setTitle("Switch EVM network?")
                                .setMessage("Allow $origin to switch to ${target.name} (chain ID ${target.chainId})?")
                                .setNegativeButton("Reject") { _, _ -> respond(code = 4001, error = "User rejected network switch") }
                                .setPositiveButton("Switch") { _, _ ->
                                    chainPrefs.edit().putLong(origin, target.chainId).apply()
                                    respond(JSONObject.NULL)
                                    try {
                                        replyProxy.postMessage(JSONObject().put("event", "chainChanged").put("chainId", target.chainIdHex).toString())
                                    } catch (_: Throwable) {}
                                }
                                .setOnCancelListener { respond(code = 4001, error = "User rejected network switch") }
                                .show()
                        }
                    }
                    "wallet_addEthereumChain" -> {
                        val requested = params.optJSONObject(0)?.optString("chainId").orEmpty()
                        val target = try { EvmWalletEngine.networkFromHex(requested) } catch (_: Throwable) {
                            respond(code = 4902, error = "Custom EVM networks are disabled")
                            return@addWebMessageListener
                        }
                        respond(code = 4902, error = "${target.name} is built in; use wallet_switchEthereumChain")
                    }
                    "personal_sign" -> {
                        if (!connected) {
                            respond(code = 4100, error = "Connect this site first")
                            return@addWebMessageListener
                        }
                        val messageHex = params.optString(0)
                        val requestedAddress = params.optString(1).lowercase(Locale.ROOT)
                        val address = try { EvmWalletEngine.address(seedPhrase) } catch (e: Throwable) {
                            respond(code = 4100, error = e.message ?: "EVM account unavailable")
                            return@addWebMessageListener
                        }
                        if (requestedAddress != address.lowercase(Locale.ROOT) || !messageHex.matches(Regex("0x(?:[0-9a-fA-F]{2})*"))) {
                            respond(code = -32602, error = "Invalid address or message encoding")
                            return@addWebMessageListener
                        }
                        val messageBytes = try { org.web3j.utils.Numeric.hexStringToByteArray(messageHex) } catch (_: Throwable) {
                            respond(code = -32602, error = "Invalid message encoding")
                            return@addWebMessageListener
                        }
                        val messageText = String(messageBytes, Charsets.UTF_8)
                        mainHandler.post {
                            AlertDialog.Builder(context)
                                .setTitle("Sign EVM message?")
                                .setMessage("Website: $origin\nNetwork: ${chain.name}\nAccount: $address\n\nMessage:\n$messageText")
                                .setNegativeButton("Reject") { _, _ -> respond(code = 4001, error = "User rejected message signature") }
                                .setPositiveButton("Sign message") { _, _ ->
                                    executor.execute {
                                        try { val sig = EvmWalletEngine.signPersonalMessage(seedPhrase, messageText); mainHandler.post { respond(sig) } }
                                        catch (e: Throwable) { mainHandler.post { respond(code = -32603, error = e.message ?: "Signing failed") } }
                                    }
                                }
                                .setOnCancelListener { respond(code = 4001, error = "User rejected message signature") }
                                .show()
                        }
                    }
                    "eth_sendTransaction" -> {
                        if (!connected) {
                            respond(code = 4100, error = "Connect this site first")
                            return@addWebMessageListener
                        }
                        val txInput = params.optJSONObject(0)
                        if (txInput == null) {
                            respond(code = -32602, error = "Transaction object is required")
                            return@addWebMessageListener
                        }
                        executor.execute {
                            try {
                                val address = EvmWalletEngine.address(seedPhrase)
                                val tx = JSONObject(txInput.toString())
                                val from = tx.optString("from", address).lowercase(Locale.ROOT)
                                require(from == address.lowercase(Locale.ROOT)) { "Transaction sender does not match the active EVM account" }
                                tx.put("from", address)
                                if (!tx.has("nonce")) tx.put("nonce", rpc(chain, "eth_getTransactionCount", JSONArray().put(address).put("pending")).toString())
                                if (!tx.has("gas") && !tx.has("gasLimit")) tx.put("gas", rpc(chain, "eth_estimateGas", JSONArray().put(tx).put("latest")).toString())
                                if (!tx.has("gas") && tx.has("gasLimit")) tx.put("gas", tx.get("gasLimit"))
                                if (!tx.has("gasPrice") && !tx.has("maxFeePerGas")) tx.put("gasPrice", rpc(chain, "eth_gasPrice", JSONArray()).toString())
                                val gas = tx.optString("gas", tx.optString("gasLimit", "unknown"))
                                val value = tx.optString("value", "0x0")
                                val to = tx.optString("to", "")
                                mainHandler.post {
                                    AlertDialog.Builder(context)
                                        .setTitle("Review EVM transaction")
                                        .setMessage("Website: $origin\nNetwork: ${chain.name}\nAccount: $address\nTo: $to\nValue (wei): $value\nGas limit: $gas\n\nContract calls can transfer tokens or NFTs and may be irreversible.")
                                        .setNegativeButton("Reject") { _, _ -> respond(code = 4001, error = "User rejected transaction") }
                                        .setPositiveButton("Sign & send") { _, _ ->
                                            executor.execute {
                                                try {
                                                    val raw = EvmWalletEngine.signTransaction(seedPhrase, chain, tx)
                                                    val hash = rpc(chain, "eth_sendRawTransaction", JSONArray().put(raw))
                                                    mainHandler.post { respond(hash) }
                                                } catch (e: Throwable) {
                                                    mainHandler.post { respond(code = -32603, error = e.message ?: "Transaction signing or submission failed") }
                                                }
                                            }
                                        }
                                        .setOnCancelListener { respond(code = 4001, error = "User rejected transaction") }
                                        .show()
                                }
                            } catch (e: Throwable) {
                                mainHandler.post { respond(code = -32602, error = e.message ?: "Transaction preparation failed") }
                            }
                        }
                    }
                    "eth_sendRawTransaction", "eth_sign", "eth_signTransaction", "eth_signTypedData", "eth_signTypedData_v4" ->
                        respond(code = 4200, error = "This EVM signing method is not supported")
                    else -> {
                        if (method !in readMethods) {
                            respond(code = -32601, error = "Unsupported EVM provider method")
                            return@addWebMessageListener
                        }
                        executor.execute {
                            try { val result = rpc(chain, method, params); mainHandler.post { respond(result) } }
                            catch (e: Throwable) { mainHandler.post { respond(code = -32603, error = e.message ?: "EVM RPC request failed") } }
                        }
                    }
                }
            }

            val script = """
                (() => {
                  if (window.__kaspaBrowserEthereumInstalled) return;
                  window.__kaspaBrowserEthereumInstalled = true;
                  const pending = new Map();
                  let nextId = 1;
                  let currentChainId = null;
                  const channel = window.$BRIDGE;
                  if (!channel || typeof channel.postMessage !== 'function') return;
                  const emit = (name, value) => window.dispatchEvent(new CustomEvent(name, { detail: value }));
                  channel.addEventListener('message', event => {
                    let response;
                    try { response = JSON.parse(event.data); } catch (_) { return; }
                    if (response.event === 'chainChanged') {
                      currentChainId = response.chainId;
                      emit('chainChanged', currentChainId);
                      return;
                    }
                    const entry = pending.get(String(response.id));
                    if (!entry) return;
                    pending.delete(String(response.id));
                    if (response.error) {
                      const error = new Error(response.error.message || 'Wallet request failed');
                      error.code = response.error.code;
                      entry.reject(error);
                    } else {
                      if (entry.method === 'wallet_switchEthereumChain') {
                        currentChainId = entry.chainId;
                        emit('chainChanged', currentChainId);
                      }
                      if (entry.method === 'eth_requestAccounts') emit('connect', { chainId: currentChainId || '0x3173b' });
                      entry.resolve(response.result);
                    }
                  });
                  const request = ({method, params = []}) => new Promise((resolve, reject) => {
                    const id = String(nextId++);
                    const entry = {resolve, reject, method};
                    if (method === 'wallet_switchEthereumChain' && params[0]) entry.chainId = params[0].chainId;
                    pending.set(id, entry);
                    try { channel.postMessage(JSON.stringify({id, method, params})); }
                    catch (error) { pending.delete(id); reject(error); }
                  });
                  const ethereum = {
                    isKaspaBrowser: true,
                    isMetaMask: false,
                    selectedAddress: null,
                    chainId: null,
                    networkVersion: null,
                    request,
                    enable: () => request({method: 'eth_requestAccounts'}),
                    sendAsync: (payload, callback) => request(payload).then(result => callback(null, {id: payload.id, jsonrpc: '2.0', result})).catch(error => callback(error, null)),
                    send: (methodOrPayload, params) => typeof methodOrPayload === 'string' ? request({method: methodOrPayload, params: params || []}) : request(methodOrPayload),
                    on: (event, listener) => { window.addEventListener(event, e => listener(e.detail)); return ethereum; },
                    removeListener: (event, listener) => { window.removeEventListener(event, listener); return ethereum; }
                  };
                  Object.defineProperty(window, 'ethereum', {configurable: false, enumerable: true, writable: false, value: ethereum});
                  window.dispatchEvent(new Event('ethereum#initialized'));
                })();
            """.trimIndent()
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("https://*"))
        } catch (_: Throwable) {
            // EVM provider stays unavailable if secure WebView messaging is unsupported.
        }
    }

    private fun normalizeOrigin(value: String): String? {
        val uri = try { URI(value) } catch (_: Exception) { return null }
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null) return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        if (uri.port == 0 || uri.port > 65535 || uri.port < -1) return null
        return if (uri.port == -1 || uri.port == 443) "https://${host.lowercase(Locale.ROOT)}"
        else "https://${host.lowercase(Locale.ROOT)}:${uri.port}"
    }
}

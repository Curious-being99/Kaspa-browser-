package com.example.network.kaspa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class Kcc20NodeRpcClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    data class NodeUtxo(
        val transactionId: String,
        val outputIndex: Int,
        val address: String,
        val amount: Long,
        val scriptPublicKeyVersion: Int,
        val scriptPublicKeyHex: String,
        val daaScore: Long,
        val isCoinbase: Boolean,
        val covenantId: String?
    )

    private val requestIds = AtomicLong(1L)

    suspend fun requireNetwork(endpoint: String, expectedNetwork: String) = withContext(Dispatchers.IO) {
        require(expectedNetwork == "mainnet" || expectedNetwork == "testnet-10") {
            "Reference KCC20 supports only mainnet and testnet-10"
        }
        val info = request(endpoint, "getInfo", JSONObject())
        val actual = info.optString("networkId", info.optString("network_id", "")).trim().lowercase()
        require(actual == expectedNetwork) {
            "Configured Kaspa node is on ${actual.ifBlank { "an unknown network" }}, expected $expectedNetwork"
        }
    }

    suspend fun getUtxosByAddress(endpoint: String, address: String): List<NodeUtxo> =
        getUtxosByAddresses(endpoint, listOf(address))

    suspend fun getUtxosByAddresses(endpoint: String, addresses: List<String>): List<NodeUtxo> =
        withContext(Dispatchers.IO) {
            require(addresses.isNotEmpty() && addresses.all { it.isNotBlank() }) {
                "At least one valid Kaspa address is required for node UTXO lookup"
            }
            val addressArray = JSONArray().apply { addresses.distinct().forEach { put(it) } }
            val result = request(
                endpoint,
                "getUtxosByAddresses",
                JSONObject().put("addresses", addressArray)
            )
            val entries = result.optJSONArray("entries")
                ?: throw IllegalStateException("Node getUtxosByAddresses response has no entries array")
            (0 until entries.length()).map { index ->
                val wrapper = entries.getJSONObject(index)
                val entry = wrapper.optJSONObject("entry") ?: wrapper
                val outpoint = wrapper.optJSONObject("outpoint")
                    ?: entry.optJSONObject("outpoint")
                    ?: throw IllegalStateException("Node UTXO is missing its outpoint")
                val txid = outpoint.optString(
                    "transactionId",
                    outpoint.optString("transaction_id", "")
                ).lowercase()
                val outputIndex = outpoint.optInt("index", outpoint.optInt("outputIndex", -1))
                val amount = runCatching { parseLong(entry, "amount") }
                    .getOrElse { parseLong(wrapper, "amount") }
                val scriptValue = entry.opt("scriptPublicKey")
                    ?: entry.opt("script_public_key")
                    ?: wrapper.opt("scriptPublicKey")
                    ?: wrapper.opt("script_public_key")
                    ?: throw IllegalStateException("Node UTXO is missing its script public key")
                val scriptObject = scriptValue as? JSONObject
                val script = if (scriptObject != null) {
                    KaspaTransactionEngine.parseFlatScriptPublicKey(
                        scriptObject.optString(
                            "scriptPublicKey",
                            scriptObject.optString("script", "")
                        ),
                        scriptObject.optInt("version", 0)
                    )
                } else {
                    KaspaTransactionEngine.parseFlatScriptPublicKey(
                        scriptValue.toString(),
                        entry.optInt("scriptPublicKeyVersion", entry.optInt("script_public_key_version", 0))
                            .takeIf { entry.has("scriptPublicKeyVersion") || entry.has("script_public_key_version") }
                    )
                }
                val covenantId = sequenceOf("covenantId", "covenant_id")
                    .map { key -> entry.optString(key, wrapper.optString(key, "")).trim().lowercase() }
                    .firstOrNull { it.isNotBlank() && it != "null" }
                require(txid.matches(Regex("[0-9a-f]{64}")) && outputIndex >= 0 && amount > 0L) {
                    "Node returned a malformed UTXO outpoint or amount"
                }
                NodeUtxo(
                    transactionId = txid,
                    outputIndex = outputIndex,
                    address = wrapper.optString("address", entry.optString("address", "")),
                    amount = amount,
                    scriptPublicKeyVersion = script.version,
                    scriptPublicKeyHex = script.script,
                    daaScore = runCatching { parseLong(entry, "blockDaaScore", "block_daa_score", "daaScore") }
                        .getOrElse { parseLong(wrapper, "blockDaaScore", "block_daa_score", "daaScore") },
                    isCoinbase = entry.optBoolean(
                        "isCoinbase",
                        entry.optBoolean("is_coinbase", wrapper.optBoolean("isCoinbase", wrapper.optBoolean("is_coinbase", false)))
                    ),
                    covenantId = covenantId
                )
            }
        }

    suspend fun submitTransaction(
        endpoint: String,
        transaction: KaspaTransactionEngine.KaspaTransaction
    ): String = withContext(Dispatchers.IO) {
        require(transaction.version == 1) { "KCC20 transaction submission requires version 1" }
        val result = request(
            endpoint,
            "submitTransaction",
            JSONObject()
                .put("transaction", transaction.toJson())
                .put("allowOrphan", false)
        )
        val txId = sequenceOf("transactionId", "transaction_id", "txid")
            .map { result.optString(it, "").trim() }
            .firstOrNull { it.isNotBlank() }
            ?: result.optString("result", "")
        require(txId.matches(Regex("[0-9a-fA-F]{64}"))) {
            "Kaspa node did not return an accepted transaction ID: $result"
        }
        txId.lowercase()
    }

    private fun request(
        endpoint: String,
        method: String,
        params: JSONObject
    ): JSONObject {
        val url = endpoint.trim()
        val parsed = java.net.URI(url)
        require(parsed.scheme == "wss" || (
            parsed.scheme == "ws" && (parsed.host == "localhost" || parsed.host == "127.0.0.1")
        )) { "Configure a wss:// Kaspa node RPC endpoint (ws:// is allowed only for localhost)" }
        require(!parsed.host.isNullOrBlank()) { "Kaspa node RPC endpoint has no host" }

        val id = requestIds.getAndIncrement()
        val response = AtomicReference<JSONObject?>()
        val failure = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, handshake: Response) {
                    val sent = webSocket.send(
                        JSONObject()
                            .put("id", id)
                            .put("method", method)
                            .put("params", params)
                            .toString()
                    )
                    if (!sent) {
                        failure.set(IllegalStateException("Failed to send $method to the Kaspa node"))
                        latch.countDown()
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val message = JSONObject(text)
                        if (message.optLong("id", -1L) != id) return
                        if (message.has("error") && !message.isNull("error")) {
                            failure.set(IllegalStateException("$method rejected by node: ${message.opt("error")}"))
                        } else {
                            response.set(message.optJSONObject("result")
                                ?: JSONObject().put("result", message.opt("result")))
                        }
                    } catch (error: Throwable) {
                        failure.set(error)
                    } finally {
                        latch.countDown()
                        webSocket.close(1000, "request complete")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    failure.set(t)
                    latch.countDown()
                }
            }
        )
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                socket.cancel()
                throw java.net.SocketTimeoutException("Kaspa node RPC $method timed out")
            }
            failure.get()?.let { throw IllegalStateException(it.message ?: "$method failed", it) }
            return response.get() ?: throw IllegalStateException("Kaspa node returned no $method response")
        } finally {
            socket.cancel()
        }
    }

    private fun parseLong(json: JSONObject, vararg keys: String): Long {
        for (key in keys) {
            if (!json.has(key) || json.isNull(key)) continue
            val value = when (val raw = json.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
            if (value != null && value >= 0L) return value
        }
        throw IllegalStateException("Node UTXO is missing a valid ${keys.first()} value")
    }
}

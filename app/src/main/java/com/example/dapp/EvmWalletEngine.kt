package com.example.dapp

import com.example.network.CryptoUtils
import org.json.JSONObject
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal object EvmWalletEngine {
    data class Network(val chainId: Long, val name: String, val rpcUrl: String, val currency: String) {
        val chainIdHex: String get() = "0x" + chainId.toString(16)
    }

    val networks = listOf(
        Network(202555L, "Kasplex Mainnet", "https://evmrpc.kasplex.org", "KAS"),
        Network(38833L, "Igra Mainnet", "https://rpc.igralabs.com:8545", "iKAS")
    )

    const val ACCOUNT_PATH = "m/44'/60'/0'/0/0"
    const val WALLET_NETWORK_PREFS = "evm_wallet_network"
    const val WALLET_NETWORK_KEY = "selected_chain_id"

    private val requestIds = AtomicLong(1)

    fun network(chainId: Long): Network = networks.firstOrNull { it.chainId == chainId }
        ?: throw IllegalArgumentException("Unsupported EVM chain ID")

    fun networkFromHex(value: String): Network {
        val id = try { BigInteger(value.removePrefix("0x").removePrefix("0X"), 16).longValueExact() }
        catch (_: Exception) { throw IllegalArgumentException("Invalid EVM chain ID") }
        return network(id)
    }

    fun credentials(seedPhrase: String): Credentials {
        val mnemonic = CryptoUtils.getDecryptedSeed(seedPhrase)
            .trim().replace("\\s+".toRegex(), " ").lowercase(Locale.ROOT)
        require(mnemonic.isNotBlank()) { "Wallet recovery phrase is unavailable" }
        require(MnemonicUtils.validateMnemonic(mnemonic)) {
            "This wallet recovery phrase is not a valid BIP-39 phrase; EVM derivation is unavailable for this wallet"
        }
        val seed = MnemonicUtils.generateSeed(mnemonic, "")
        val derived = try {
            val master = Bip32ECKeyPair.generateKeyPair(seed)
            val path = intArrayOf(
                44 or Int.MIN_VALUE,
                60 or Int.MIN_VALUE,
                Int.MIN_VALUE,
                0,
                0
            )
            Bip32ECKeyPair.deriveKeyPair(master, path)
        } finally {
            java.util.Arrays.fill(seed, 0.toByte())
        }
        return Credentials.create(derived)
    }

    fun address(seedPhrase: String): String = credentials(seedPhrase).address.lowercase(Locale.ROOT)

    fun signPersonalMessage(seedPhrase: String, message: String): String {
        require(message.length <= 8192) { "Message is too long" }
        val signed = Sign.signPrefixedMessage(message.toByteArray(Charsets.UTF_8), credentials(seedPhrase).ecKeyPair)
        return "0x" + Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signed.r), 64) +
            Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signed.s), 64) +
            String.format(Locale.ROOT, "%02x", signed.v[0].toInt() and 0xff)
    }

    fun signTypedData(seedPhrase: String, typedDataJson: String): String {
        require(typedDataJson.length in 1..32768) { "Typed-data request is empty or too large" }
        val signature = Sign.signTypedData(typedDataJson, credentials(seedPhrase).ecKeyPair)
        return "0x" + Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signature.r), 64) +
            Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signature.s), 64) +
            String.format(Locale.ROOT, "%02x", signature.v[0].toInt() and 0xff)
    }

    fun signTransaction(seedPhrase: String, network: Network, tx: JSONObject): String {
        val credentials = credentials(seedPhrase)
        val from = tx.optString("from", "").lowercase(Locale.ROOT)
        require(from.isBlank() || from == credentials.address.lowercase(Locale.ROOT)) {
            "Transaction sender does not match the active EVM account"
        }
        if (tx.has("chainId")) {
            val requestedChain = tx.optString("chainId")
            val requestedId = if (requestedChain.startsWith("0x", true)) {
                BigInteger(requestedChain.substring(2), 16).longValueExact()
            } else {
                requestedChain.toLong()
            }
            require(requestedId == network.chainId) { "Transaction chain ID does not match the selected network" }
        }
        val to = tx.optString("to", "")
        require(to.matches(Regex("0x[0-9a-fA-F]{40}"))) { "Only standard EVM destination addresses are supported" }
        val nonce = quantity(tx, "nonce")
        val gasLimit = quantity(tx, "gas")
        val value = optionalQuantity(tx, "value")
        val data = tx.optString("data", tx.optString("input", "0x"))
        require(data.matches(Regex("0x(?:[0-9a-fA-F]{2})*"))) { "Invalid transaction data" }
        require(gasLimit > BigInteger.ZERO) { "Transaction gas limit must be positive" }
        val raw = if (tx.has("maxFeePerGas") || tx.has("maxPriorityFeePerGas")) {
            require(tx.has("maxFeePerGas") && tx.has("maxPriorityFeePerGas")) {
                "Both EIP-1559 fee fields are required"
            }
            val maxFee = quantity(tx, "maxFeePerGas")
            val priority = quantity(tx, "maxPriorityFeePerGas")
            require(maxFee >= priority && priority >= BigInteger.ZERO) { "Invalid EIP-1559 fee values" }
            require(!tx.has("type") || optionalQuantity(tx, "type").toInt() == 2) {
                "Only EIP-1559 type-2 transactions are supported"
            }
            RawTransaction.createTransaction(
                network.chainId, nonce, gasLimit, to, value, data, priority, maxFee
            )
        } else {
            require(tx.has("gasPrice")) { "Transaction must include gasPrice or EIP-1559 fee fields" }
            RawTransaction.createTransaction(nonce, quantity(tx, "gasPrice"), gasLimit, to, value, data)
        }
        val signed = TransactionEncoder.signMessage(raw, network.chainId, credentials)
        return Numeric.toHexString(signed)
    }

    fun rpc(network: Network, method: String, params: org.json.JSONArray): JSONObject {
        require(method.matches(Regex("eth_[A-Za-z0-9]+|net_version|web3_clientVersion"))) { "Unsupported JSON-RPC method" }
        val id = requestIds.getAndIncrement()
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        val client = okhttp3.OkHttpClient.Builder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = okhttp3.Request.Builder()
            .url(network.rpcUrl)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "EVM RPC endpoint returned HTTP ${response.code}" }
            val body = response.body?.string().orEmpty()
            require(body.length <= 2 * 1024 * 1024) { "EVM RPC response is too large" }
            val result = JSONObject(body)
            if (result.has("error")) {
                val error = result.optJSONObject("error")
                throw IllegalStateException(error?.optString("message") ?: "EVM RPC request failed")
            }
            return JSONObject().put("result", result.opt("result") ?: JSONObject.NULL)
        }
    }

    private fun quantity(tx: JSONObject, key: String): BigInteger {
        require(tx.has(key)) { "Missing transaction field: $key" }
        return parseQuantity(tx.optString(key))
    }

    private fun optionalQuantity(tx: JSONObject, key: String, default: BigInteger = BigInteger.ZERO): BigInteger =
        if (tx.has(key)) parseQuantity(tx.optString(key)) else default

    private fun parseQuantity(value: String): BigInteger {
        require(value.matches(Regex("0x[0-9a-fA-F]+|[0-9]+"))) { "Invalid EVM quantity" }
        return if (value.startsWith("0x", true)) BigInteger(value.substring(2), 16) else BigInteger(value)
    }

    private fun String.toRequestBody(mediaType: okhttp3.MediaType) =
        okhttp3.RequestBody.create(mediaType, this)

    private fun String.toMediaType() = okhttp3.MediaType.parse(this)!!
}

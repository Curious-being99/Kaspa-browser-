package com.example.dapp

import com.example.network.CryptoUtils
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
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
    data class Network(
        val chainId: Long,
        val name: String,
        val rpcUrl: String,
        val currency: String,
        val explorerUrl: String = ""
    ) {
        val chainIdHex: String get() = "0x" + chainId.toString(16)
    }

    val networks = listOf(
        Network(202555L, "Kasplex Mainnet", "https://evmrpc.kasplex.org", "KAS", "https://explorer.kasplex.org"),
        Network(38833L, "Igra Mainnet", "https://rpc.igralabs.com:8545", "iKAS", "https://tidx.igralabs.com/explore"),
        Network(167012L, "Kasplex Testnet", "https://rpc.kasplextest.xyz", "KAS", "https://testnet.kasplex.org"),
        Network(38836L, "Igra Galleon Testnet", "https://galleon-testnet.igralabs.com:8545", "iKAS", "https://galleon.igralabs.com")
    )

    const val ACCOUNT_PATH = "m/44'/60'/0'/0/0"
    const val WALLET_NETWORK_PREFS = "evm_wallet_network"
    const val WALLET_NETWORK_KEY = "selected_chain_id"

    private val requestIds = AtomicLong(1)
    private val rpcClient = okhttp3.OkHttpClient.Builder()
        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun network(chainId: Long): Network = networks.firstOrNull { it.chainId == chainId }
        ?: throw IllegalArgumentException("Unsupported EVM chain ID")

    fun networkFromHex(value: String): Network {
        val id = try { BigInteger(value.removePrefix("0x").removePrefix("0X"), 16).longValueExact() }
        catch (_: Exception) { throw IllegalArgumentException("Invalid EVM chain ID") }
        return network(id)
    }

    fun credentials(seedPhrase: String, passphrase: String = ""): Credentials {
        val mnemonic = CryptoUtils.getDecryptedSeed(seedPhrase)
            .trim().replace("\\s+".toRegex(), " ").lowercase(Locale.ROOT)
        require(mnemonic.isNotBlank()) { "Wallet recovery phrase is unavailable" }
        require(passphrase.length <= 256) { "BIP-39 passphrase is too long" }
        val seed = CryptoUtils.mnemonicToSeed(mnemonic, passphrase)
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

    fun isStandardMnemonic(seedPhrase: String): Boolean {
        val mnemonic = CryptoUtils.getDecryptedSeed(seedPhrase)
            .trim().replace("\\s+".toRegex(), " ").lowercase(Locale.ROOT)
        if (mnemonic.isBlank()) return false
        return try {
            MnemonicUtils.validateMnemonic(mnemonic)
        } catch (_: Throwable) {
            val words = mnemonic.split(" ")
            words.size in listOf(12, 15, 18, 21, 24)
        }
    }

    fun address(seedPhrase: String, passphrase: String = ""): String = credentials(seedPhrase, passphrase).address.lowercase(Locale.ROOT)

    fun signPersonalMessage(seedPhrase: String, message: String, passphrase: String = ""): String {
        require(message.length <= 8192) { "Message is too long" }
        val signed = Sign.signPrefixedMessage(message.toByteArray(Charsets.UTF_8), credentials(seedPhrase, passphrase).ecKeyPair)
        return "0x" + Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signed.r), 64) +
            Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signed.s), 64) +
            String.format(Locale.ROOT, "%02x", signed.v[0].toInt() and 0xff)
    }

    fun signPersonalMessageHex(seedPhrase: String, messageHex: String, passphrase: String = ""): String {
        require(messageHex.matches(Regex("0x(?:[0-9a-fA-F]{2}){0,8192}"))) { "Invalid personal_sign message encoding" }
        val message = Numeric.hexStringToByteArray(messageHex)
        val signature = Sign.signPrefixedMessage(message, credentials(seedPhrase, passphrase).ecKeyPair)
        return "0x" + Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signature.r), 64) +
            Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signature.s), 64) +
            String.format(Locale.ROOT, "%02x", signature.v[0].toInt() and 0xff)
    }

    fun signTypedData(seedPhrase: String, typedDataJson: String, passphrase: String = ""): String {
        require(typedDataJson.length in 1..32768) { "Typed-data request is empty or too large" }
        val signature = Sign.signTypedData(typedDataJson, credentials(seedPhrase, passphrase).ecKeyPair)
        return "0x" + Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signature.r), 64) +
            Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signature.s), 64) +
            String.format(Locale.ROOT, "%02x", signature.v[0].toInt() and 0xff)
    }

    fun signEthMessage(seedPhrase: String, messageHex: String, passphrase: String = ""): String {
        require(messageHex.matches(Regex("0x(?:[0-9a-fA-F]{2}){1,8192}"))) { "Invalid eth_sign message encoding" }
        val message = Numeric.hexStringToByteArray(messageHex)
        val hash = org.web3j.crypto.Hash.sha3(message)
        val signature = Sign.signMessage(hash, credentials(seedPhrase, passphrase).ecKeyPair, false)
        return "0x" + Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signature.r), 64) +
            Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, signature.s), 64) +
            String.format(Locale.ROOT, "%02x", signature.v[0].toInt() and 0xff)
    }

    fun signTransaction(seedPhrase: String, network: Network, tx: JSONObject, passphrase: String = ""): String {
        val credentials = credentials(seedPhrase, passphrase)
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
        val hasEip1559Fees = tx.has("maxFeePerGas") || tx.has("maxPriorityFeePerGas")
        if (tx.has("type")) {
            val transactionType = optionalQuantity(tx, "type").intValueExact()
            require(
                if (hasEip1559Fees) transactionType == 2 else transactionType == 0
            ) { "Only legacy type-0 and EIP-1559 type-2 transactions are supported" }
        }
        val raw = if (hasEip1559Fees) {
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

    fun sendEtherTransaction(
        seedPhrase: String,
        network: Network,
        toAddress: String,
        amountTokens: Double,
        passphrase: String = ""
    ): String {
        val targetTo = toAddress.trim()
        require(targetTo.matches(Regex("0x[0-9a-fA-F]{40}"))) { "Invalid recipient EVM address" }
        require(amountTokens > 0) { "Amount must be greater than 0" }
        val credentials = credentials(seedPhrase, passphrase)

        // 1. Fetch current transaction count (nonce)
        val nonceResp = rpc(network, "eth_getTransactionCount", org.json.JSONArray().put(credentials.address).put("pending"))
        val nonceHex = nonceResp.optString("result", "0x0").removePrefix("0x")
        val nonce = if (nonceHex.isBlank()) BigInteger.ZERO else BigInteger(nonceHex, 16)

        // 2. Fetch network gas price
        val gasPrice = try {
            val gasResp = rpc(network, "eth_gasPrice", org.json.JSONArray())
            val gasHex = gasResp.optString("result", "0x4a817c800").removePrefix("0x")
            BigInteger(gasHex.ifBlank { "4a817c800" }, 16)
        } catch (_: Exception) {
            BigInteger.valueOf(20_000_000_000L) // 20 Gwei fallback
        }

        val gasLimit = BigInteger.valueOf(21_000L)
        val valueWei = java.math.BigDecimal(amountTokens.toString())
            .multiply(java.math.BigDecimal.TEN.pow(18))
            .toBigInteger()

        val rawTx = RawTransaction.createEtherTransaction(
            nonce,
            gasPrice,
            gasLimit,
            targetTo,
            valueWei
        )
        val signedMessage = TransactionEncoder.signMessage(rawTx, network.chainId, credentials)
        val hexValue = Numeric.toHexString(signedMessage)

        // 3. Broadcast to network RPC
        val broadcastResp = rpc(network, "eth_sendRawTransaction", org.json.JSONArray().put(hexValue))
        val txHash = broadcastResp.optString("result", "")
        if (txHash.isBlank()) {
            throw IllegalStateException("Failed to broadcast transaction to ${network.name}")
        }
        return txHash
    }

    fun verifyNetwork(network: Network) {
        val actual = rpc(network, "eth_chainId", org.json.JSONArray()).optString("result")
        val actualId = try {
            BigInteger(actual.removePrefix("0x").removePrefix("0X"), 16).longValueExact()
        } catch (_: Exception) {
            throw IllegalStateException("${network.name} RPC returned an invalid chain ID")
        }
        require(actualId == network.chainId) {
            "${network.name} RPC chain ID mismatch: expected ${network.chainId}, received $actualId"
        }
    }

    fun rpc(network: Network, method: String, params: org.json.JSONArray): JSONObject {
        require(method.matches(Regex("eth_[A-Za-z0-9]+|net_version|web3_clientVersion"))) { "Unsupported JSON-RPC method" }
        val id = requestIds.getAndIncrement()
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        val request = okhttp3.Request.Builder()
            .url(network.rpcUrl)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()
        rpcClient.newCall(request).execute().use { response ->
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

}

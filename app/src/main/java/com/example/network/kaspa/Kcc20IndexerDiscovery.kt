package com.example.network.kaspa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.digests.Blake3Digest
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

enum class Kcc20Launchpad {
    KRON,
    KASPAROCKET,
    KASSEAL
}

data class Kcc20DiscoveredHolding(
    val network: String,
    val launchpad: Kcc20Launchpad,
    val ticker: String,
    val name: String,
    val covenantId: String,
    val rawBalance: BigInteger,
    val decimals: Int,
    val verifiedUtxoCount: Int
)

data class Kcc20VerifiedUtxo(
    val transactionId: String,
    val outputIndex: Int,
    val tokenAmount: BigInteger,
    val redeemScriptHex: String
)

/**
 * Read-only KCC-20 discovery through launchpad indexers.
 *
 * KRON is enabled against its documented typed-indexer API. KaspaRocket and KasSeal are
 * deliberately reported as unsupported until their own network-specific indexer response
 * and compiled-template verification adapters are implemented.
 */
class Kcc20IndexerDiscovery(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000
) {
    companion object {
        private const val KRON_MAINNET = "https://idx.kron.technology/v1/kcc20"
        private const val KRON_TESTNET10 = "https://idx.krontest.xyz/v1/kcc20"
        private val TOKEN_ID = Regex("[0-9a-f]{64}")
        private val TX_ID = Regex("[0-9a-f]{64}")
    }

    suspend fun discover(
        walletAddress: String,
        publicKeyHex: String,
        network: String,
        launchpad: Kcc20Launchpad
    ): Result<List<Kcc20DiscoveredHolding>> = withContext(Dispatchers.IO) {
        val canonicalNetwork = normalizeNetwork(network)
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported KCC-20 network: $network"))
        val publicKey = decodeHex(publicKeyHex)
            ?: return@withContext Result.failure(IllegalArgumentException("Wallet public key must be 32-byte x-only hex"))
        if (publicKey.size != 32) {
            return@withContext Result.failure(IllegalArgumentException("Wallet public key must be 32-byte x-only hex"))
        }
        if (walletAddress.isBlank() ||
            (canonicalNetwork == "mainnet" && !walletAddress.startsWith("kaspa:")) ||
            (canonicalNetwork == "testnet-10" && !walletAddress.startsWith("kaspatest:"))
        ) {
            return@withContext Result.failure(IllegalArgumentException("Wallet address does not match $canonicalNetwork"))
        }
        if (launchpad != Kcc20Launchpad.KRON) {
            return@withContext Result.failure(
                UnsupportedOperationException("$launchpad discovery is not enabled until its own indexer and compiled-template checks are implemented")
            )
        }

        runCatching {
            val base = if (canonicalNetwork == "mainnet") KRON_MAINNET else KRON_TESTNET10
            val info = getJson("$base/info")
            val infoResult = info.optJSONObject("result")
                ?: throw IOException("KRON indexer returned no info result")
            if (!infoResult.optBoolean("synced", false)) {
                throw IOException("KRON $canonicalNetwork indexer is not synced")
            }
            if (infoResult.optString("network").isNotBlank() &&
                !infoResult.optString("network").equals(canonicalNetwork, ignoreCase = true)
            ) {
                throw IOException("KRON indexer network does not match requested network")
            }

            val tokenList = getResultArray(
                "$base/address/${encodePath(walletAddress)}/tokenlist"
            )
            val holdings = mutableListOf<Kcc20DiscoveredHolding>()
            for (balanceRow in tokenList) {
                val ticker = balanceRow.optString("tick").trim()
                if (ticker.isBlank()) continue

                val tokenResult = getResultObject("$base/token/${encodePath(ticker)}")
                val token = if (tokenResult.has("token") && tokenResult.optJSONObject("token") != null) {
                    tokenResult.getJSONObject("token")
                } else {
                    tokenResult
                }
                val covenantId = token.optString("covenantId").lowercase()
                if (!TOKEN_ID.matches(covenantId)) {
                    throw IOException("KRON token metadata for $ticker has no valid covenantId")
                }
                val decimals = token.optInt("dec", -1)
                if (decimals !in 0..18) {
                    throw IOException("KRON token metadata for $ticker has invalid decimals")
                }

                val utxoRows = getResultArray(
                    "$base/token/${encodePath(ticker)}/address/${encodePath(walletAddress)}/utxos"
                )
                val verified = utxoRows.map { row ->
                    verifyUtxo(row, publicKey)
                }
                val rawTotal = verified.fold(BigInteger.ZERO) { sum, utxo ->
                    sum.add(utxo.tokenAmount)
                }
                val indexerRaw = balanceRow.optString("balance")
                    .takeIf { it.isNotBlank() }
                    ?: throw IOException("KRON returned no raw balance for $ticker")
                val parsedIndexerBalance = indexerRaw.toBigIntegerOrNull()
                    ?: throw IOException("KRON returned a non-integer raw balance for $ticker")
                if (parsedIndexerBalance != rawTotal) {
                    throw IOException("KRON balance/UTXO mismatch for $ticker; refusing to display an unverified balance")
                }
                if (rawTotal.signum() > 0) {
                    holdings += Kcc20DiscoveredHolding(
                        network = canonicalNetwork,
                        launchpad = launchpad,
                        ticker = ticker,
                        name = token.optString("name").ifBlank { ticker },
                        covenantId = covenantId,
                        rawBalance = rawTotal,
                        decimals = decimals,
                        verifiedUtxoCount = verified.size
                    )
                }
            }
            holdings
        }
    }

    private fun verifyUtxo(
        row: JSONObject,
        walletPublicKey: ByteArray
    ): Kcc20VerifiedUtxo {
        val outpoint = row.optJSONObject("outpoint")
            ?: throw IOException("KRON UTXO is missing its outpoint")
        val transactionId = outpoint.optString("transactionId").lowercase()
        val outputIndex = outpoint.optInt("index", -1)
        if (!TX_ID.matches(transactionId) || outputIndex < 0) {
            throw IOException("KRON UTXO has an invalid outpoint")
        }
        val redeemHex = row.optString("redeemScriptHex").lowercase()
        val redeem = decodeHex(redeemHex)
            ?: throw IOException("KRON UTXO has malformed redeemScriptHex")
        val state = decodeReferenceTokenState(redeem)
        if (state.ownerScheme != 0 && state.ownerScheme != 1) {
            throw IOException("KRON UTXO uses unsupported holder owner scheme ${state.ownerScheme}")
        }
        val expectedOwner = if (state.ownerScheme == 0) {
            walletPublicKey
        } else {
            blake3(walletPublicKey)
        }
        if (!state.owner.contentEquals(expectedOwner)) {
            throw IOException("KRON UTXO redeem-script owner does not match the connected wallet")
        }

        val amountField = row.optString("amount")
        val tokenAmount = amountField.toBigIntegerOrNull()
            ?: throw IOException("KRON UTXO has an invalid token amount")
        if (tokenAmount != state.amount || tokenAmount.signum() < 0) {
            throw IOException("KRON UTXO amount does not match its redeem-script state")
        }
        return Kcc20VerifiedUtxo(
            transactionId = transactionId,
            outputIndex = outputIndex,
            tokenAmount = tokenAmount,
            redeemScriptHex = redeemHex
        )
    }

    private data class ReferenceState(
        val templateHash: String,
        val amount: BigInteger,
        val owner: ByteArray,
        val ownerScheme: Int
    )

    private fun decodeReferenceTokenState(redeem: ByteArray): ReferenceState {
        val offset = 1
        val stateLength = 145
        if (redeem.size < offset + stateLength) {
            throw IOException("KCC-20 redeem script is shorter than the reference state")
        }
        val state = redeem.copyOfRange(offset, offset + stateLength)
        val framing = intArrayOf(0x20, 0x08, 0x20, 0x01, 0x01, 0x20, 0x20)
        val positions = intArrayOf(0, 33, 42, 75, 77, 79, 112)
        for (i in framing.indices) {
            if ((state[positions[i]].toInt() and 0xff) != framing[i]) {
                throw IOException("KCC-20 redeem state has invalid field framing")
            }
        }
        val amountBytes = state.copyOfRange(34, 42)
        val negative = (amountBytes[7].toInt() and 0x80) != 0
        if (negative) throw IOException("KCC-20 redeem state has a negative token amount")
        val amount = BigInteger(1, amountBytes.reversedArray())
        val ownerScheme = state[76].toInt() and 0xff
        if (ownerScheme !in 0..4) throw IOException("KCC-20 redeem state has an unknown owner scheme")
        return ReferenceState(
            templateHash = state.copyOfRange(1, 33).toHex(),
            amount = amount,
            owner = state.copyOfRange(43, 75),
            ownerScheme = ownerScheme
        )
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("KCC-20 indexer HTTP ${connection.responseCode}")
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun getResultArray(url: String): List<JSONObject> {
        val envelope = getJson(url)
        val result = envelope.optJSONArray("result")
            ?: throw IOException("KCC-20 indexer response has no result array")
        return result.mapNotNull { it as? JSONObject }
    }

    private fun getResultObject(url: String): JSONObject {
        val envelope = getJson(url)
        return envelope.optJSONObject("result")
            ?: throw IOException("KCC-20 indexer response has no result object")
    }

    private fun normalizeNetwork(network: String): String? = when (
        network.trim().lowercase().replace('_', '-').replace(' ', '-')
    ) {
        "mainnet", "kaspa-mainnet" -> "mainnet"
        "testnet-10", "testnet10", "kaspa-testnet-10", "kaspatest" -> "testnet-10"
        else -> null
    }

    private fun encodePath(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun decodeHex(value: String): ByteArray? {
        val clean = value.trim().lowercase().removePrefix("0x")
        if (clean.length % 2 != 0 || !clean.matches(Regex("[0-9a-f]*"))) return null
        return runCatching {
            ByteArray(clean.length / 2) { index ->
                clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun blake3(input: ByteArray): ByteArray {
        val digest = Blake3Digest(256)
        digest.update(input, 0, input.size)
        return ByteArray(32).also { digest.doFinal(it, 0) }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

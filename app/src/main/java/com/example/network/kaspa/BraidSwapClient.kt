package com.example.network.kaspa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Separate BraidSwap read-only API client.
 *
 * This client deliberately does not build, sign, or submit deposits. Those operations
 * must go through BraidSwapNativeVerifier after the full reference verifier is ported.
 */
internal class BraidSwapClient(
    private val network: Network = Network.MAINNET,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    enum class Network(
        val apiBase: String,
        val nodeBase: String,
        val networkName: String,
        val currentRouterPin: String
    ) {
        MAINNET(
            "https://app.braidswap.com/api/mainnet/router",
            "https://api.kaspa.org",
            "kaspa-mainnet",
            "f994470f9bd84d2ca2578a5d8294cc403bb699f19f7e73922c51d04cbb17027d"
        ),
        TN10(
            "https://tn10-aggregator.braidswap.com/api/venues/router",
            "https://api-tn10.kaspa.org",
            "kaspa-testnet-10",
            "905df466072298313e1cc9e86132ba61f26e2a359f7a7dbd5028fa1963872646"
        )
    }

    data class SupportedToken(
        val symbol: String,
        val covenantId: String,
        val lineage: String,
        val network: Network
    )

    data class VerifiedRouterStatus(
        val network: Network,
        val pin: String,
        val enabled: Boolean,
        val canary: Boolean
    )

    companion object {
        private fun token(network: Network, symbol: String, id: String, lineage: String) =
            SupportedToken(symbol, id, lineage, network)

        val SUPPORTED_TOKENS: List<SupportedToken> = listOf(
            token(Network.MAINNET, "THEN", "d8c4356d20712463bbf2da82a5411c3aea18fec024a2b9c014bba1b4524cc8c2", "kr"),
            token(Network.MAINNET, "KRON", "7a98370c0d6b037c28089d7b77cd8229120804cb416ef592ddb99e23820544e4", "kron"),
            token(Network.MAINNET, "PEPE", "a73cdef004099b191759d320de970451be0e10423a7eb15b07d5e51d050b47cd", "kron"),
            token(Network.MAINNET, "IFWEN", "b4301f4177a3c33b85855c0415dcbe3ccfc4f36a4cd5afc105f1177cda97a8af", "kron"),
            token(Network.MAINNET, "ANSEM", "3cb4d9625e8ce0c54c928cce7f380cecb9ae7bb957a559be017b78218696f79d", "kron"),
            token(Network.MAINNET, "GUZA", "3cb7bd371ea2014af95f3471d2995ede8ffbde454d3a37e3b566880daf011c5a", "kr"),
            token(Network.MAINNET, "KASCOV", "c58c826d0aa9cee62f93208718c674883f5c89a8aca4933dc41fb0391539abe2", "kron"),
            token(Network.TN10, "ICE", "b20f807bed621ca3ea3aaf1f605c6d16d3e3efc766aedefd089db019ceb30526", "ref"),
            token(Network.TN10, "ROCKET", "edca1f844ae0b252d0c11ab98ff850879afad1f04e19aff2754c9eb232b59734", "kr"),
            token(Network.TN10, "MKRON", "86e3e4d1d1d2f41848d82fb732cb0bf3058cf7ee12bb78ba957ce5cc53941bb6", "kron"),
            token(Network.TN10, "MKPEPE", "7ca64292dbdd9672303788da5219f51277a3653c60dfddc1fc7696f842088e8c", "kron"),
            token(Network.TN10, "FIRE", "a83d58c3a497efe86176939a0ed9d2df13441cb1897c48f89c5851edfad7f61e", "ref"),
            token(Network.TN10, "KAHA20", "0282cf6d53023ec08e241919c150d742f54b6f61bac2f1b8f81b7e6343845b9f", "q4"),
            token(Network.TN10, "KRKT20", "7a1568354b2c70e5919e400856e72f45e3621b674465739c33f0f7ed53fbd9d6", "q4"),
            token(Network.TN10, "KZEN20", "4a4eff44e065b60ccc9ea8175d818c4e17b2f12d15db50cd570c61f57f1dfb7d", "q4")
        )

        fun supportedTokens(network: Network): List<SupportedToken> =
            SUPPORTED_TOKENS.filter { it.network == network }

        fun isSupported(network: Network, symbol: String): Boolean =
            supportedTokens(network).any { it.symbol.equals(symbol.trim(), ignoreCase = true) }
    }

    suspend fun verifyRouterStatus(): VerifiedRouterStatus = withContext(Dispatchers.IO) {
        val info = getJson("${network.apiBase}/info")
        val pin = info.optString("pin", "").lowercase()
        require(pin == network.currentRouterPin && info.optBoolean("pinOk", false)) {
            "BraidSwap router fingerprint is not the pinned current release"
        }
        require(info.optBoolean("enabled", false)) {
            "BraidSwap router is not enabled; swaps are unavailable"
        }
        val statusNetwork = info.optString("networkName", network.networkName)
        require(statusNetwork == network.networkName) {
            "BraidSwap API returned the wrong network"
        }
        VerifiedRouterStatus(
            network = network,
            pin = pin,
            enabled = true,
            canary = info.optBoolean("canary", false)
        )
    }

    suspend fun getQuote(
        from: String,
        to: String,
        amountIn: String? = null,
        payTotal: String? = null,
        slippageBps: Int = 100,
        userPublicKey: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        require(slippageBps in 1..2000) { "Slippage must be between 1 and 2000 bps" }
        require((amountIn != null) xor (payTotal != null)) {
            "Provide exactly one of token amountIn or KAS payTotal"
        }
        val fromSymbol = from.trim().uppercase()
        val toSymbol = to.trim().uppercase()
        require(fromSymbol == "KAS" || isSupported(network, fromSymbol)) {
            "Unsupported BraidSwap input token on ${network.name}: $fromSymbol"
        }
        require(toSymbol == "KAS" || isSupported(network, toSymbol)) {
            "Unsupported BraidSwap output token on ${network.name}: $toSymbol"
        }
        require(fromSymbol != toSymbol) { "Choose two different assets" }
        amountIn?.let { require(it.matches(Regex("[1-9][0-9]{0,18}"))) { "Invalid token base-unit amount" } }
        payTotal?.let { require(it.matches(Regex("[1-9][0-9]{0,18}"))) { "Invalid KAS sompi amount" } }
        userPublicKey?.let { require(it.matches(Regex("(?i)[0-9a-f]{64}"))) { "Invalid x-only public key" } }

        verifyRouterStatus()
        val query = buildList {
            add("from" to fromSymbol)
            add("to" to toSymbol)
            add("slippageBps" to slippageBps.toString())
            if (amountIn != null) add("amountIn" to amountIn)
            if (payTotal != null) add("payTotal" to payTotal)
            if (userPublicKey != null) add("userPk" to userPublicKey.lowercase())
        }.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, "UTF-8")}=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }
        val quote = getJson("${network.apiBase}/quote?$query")
        require(!quote.has("error")) {
            "BraidSwap quote refused: ${quote.optString("error", "unknown error")}"
        }
        require(quote.optString("from", "").equals(fromSymbol, true)) {
            "BraidSwap quote input does not match the requested asset"
        }
        require(quote.optString("to", "").equals(toSymbol, true)) {
            "BraidSwap quote output does not match the requested asset"
        }
        require(quote.optString("quoteId").isNotBlank()) { "BraidSwap quote has no quote ID" }
        require(quote.optString("amountOut").matches(Regex("[1-9][0-9]{0,18}"))) {
            "BraidSwap quote has an invalid output amount"
        }
        require(quote.optString("minOut").matches(Regex("[1-9][0-9]{0,18}"))) {
            "BraidSwap quote has an invalid minimum output"
        }
        quote
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IOException("BraidSwap returned an empty response")
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                throw IOException("BraidSwap returned malformed JSON (HTTP ${response.code})")
            }
            if (!response.isSuccessful) {
                throw IOException("BraidSwap request failed (HTTP ${response.code}): ${json.optString("error", "request failed")}")
            }
            return json
        }
    }
}

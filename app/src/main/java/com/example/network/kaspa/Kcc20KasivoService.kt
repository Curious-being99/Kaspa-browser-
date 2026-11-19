package com.example.network.kaspa

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.bouncycastle.crypto.digests.Blake3Digest
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow

/**
 * Protocol Taxonomy defining the strict boundary between Kaspa Mainnet L1 native KAS,
 * L1 KCC-20 Kaspa Covenant / Smart Contract Tokens (KasCov / Kasivo), and L2 KRC-20 Kasplex Inscription Tokens.
 */
enum class KaspaProtocolType(val displayName: String, val layer: String, val description: String) {
    L1_KASPA("KAS Native", "L1", "Kaspa BlockDAG Native UTXO Layer"),
    KCC20_COVENANT("KCC-20", "L1", "Kaspa Native L1 Covenant & Smart Contract Protocol (KasCov)"),
    KRC20_INSCRIPTION("KRC-20", "L2", "Kasplex & Igra Inscription Protocol");

    companion object {
        fun parseFromJson(jsonString: String): KaspaProtocolType {
            if (jsonString.isBlank()) return L1_KASPA
            return try {
                val obj = JSONObject(jsonString)
                val protocol = obj.optString("p", "").lowercase()
                when {
                    protocol == "kcc-20" || obj.has("covenantId") || obj.has("contractId") -> KCC20_COVENANT
                    protocol == "krc-20" || obj.has("tick") && obj.has("lim") -> KRC20_INSCRIPTION
                    else -> L1_KASPA
                }
            } catch (_: Exception) {
                if (jsonString.contains("kcc-20", ignoreCase = true) || jsonString.contains("kascov", ignoreCase = true) || jsonString.contains("kasivo", ignoreCase = true)) {
                    KCC20_COVENANT
                } else if (jsonString.contains("krc-20", ignoreCase = true)) {
                    KRC20_INSCRIPTION
                } else {
                    L1_KASPA
                }
            }
        }
    }
}

/**
 * Model representing a KCC-20 (Kaspa Covenant Token) balance.
 */
data class Kcc20TokenBalanceResult(
    val tokenContractId: String,
    val ticker: String,
    val balance: Double,
    val balanceRaw: String,
    val decimals: Int = 8,
    val protocolType: KaspaProtocolType = KaspaProtocolType.KCC20_COVENANT,
    val contractState: String = "active",
    val holderCount: Long = 0L,
    val rawMetadataJson: String = ""
) {
    fun formattedBalance(): String = "%,.4f %s".format(balance, ticker)
}

/**
 * Model representing a transaction item under the KCC-20 covenant protocol.
 */
data class Kcc20TransactionHistoryItem(
    val txId: String,
    val tokenContractId: String,
    val ticker: String,
    val operation: String, // "TRANSFER", "COVENANT_EXEC", "SWAP", "MINT"
    val amount: Double,
    val amountRaw: String,
    val fromAddress: String,
    val toAddress: String,
    val timestamp: Long,
    val status: String,
    val feeKas: Double,
    val protocolType: KaspaProtocolType = KaspaProtocolType.KCC20_COVENANT,
    val rawJson: String = ""
) {
    fun formattedJson(): String {
        return if (rawJson.isNotBlank()) {
            try {
                JSONObject(rawJson).toString(2)
            } catch (_: Exception) {
                rawJson
            }
        } else {
            val obj = JSONObject()
            obj.put("txId", txId)
            obj.put("tokenContractId", tokenContractId)
            obj.put("ticker", ticker)
            obj.put("p", "kcc-20")
            obj.put("op", operation.lowercase())
            obj.put("amount", amountRaw.ifBlank { amount.toString() })
            obj.put("from", fromAddress)
            obj.put("to", toAddress)
            obj.put("timestamp", timestamp)
            obj.put("feeKas", feeKas)
            obj.put("status", status)
            obj.toString(2)
        }
    }
}

/**
 * Multi-protocol taxonomy overview for a given Kaspa Mainnet address.
 */
data class ProtocolTaxonomySummary(
    val address: String,
    val l1KasBalance: Double,
    val kcc20TokenBalances: List<Kcc20TokenBalanceResult>,
    val krc20TokenBalancesCount: Int,
    val primaryProtocol: KaspaProtocolType = KaspaProtocolType.L1_KASPA
)

/**
 * KCC-20 KasCov / Kasivo Service interfacing directly with Kaspa's RPC gateway and KasCov BlockDAG indexer endpoints (https://kascov.io).
 * Connects to live Mainnet network nodes for real-time balances, transaction execution, and covenant state.
 */
data class Kcc20WalletToken(
    val contractId: String,
    val ticker: String,
    val name: String,
    val balanceRaw: String,
    val decimals: Int,
    val holderCount: Long?,
    val validationStatus: String,
    val marketPriceKas: String?,
    val marketPriceUsd: String?,
    val marketPriceSource: String?,
    val priceUpdatedAtMs: Long?,
    val priceChange24hPercent: String?,
    val imageUrl: String?,
    val bondingCurveProgressKas: Double? = null,
    val isGraduated: Boolean = false
) {
    val balanceFormatted: String
        get() {
            val raw = balanceRaw.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            if (raw == java.math.BigDecimal.ZERO) return "0"
            val scaled = raw.movePointLeft(decimals).stripTrailingZeros()
            return scaled.toPlainString()
        }
}

internal fun normalizeKascovNetwork(value: String): String? {
    val normalized = value.trim().lowercase().replace('_', '-').replace(' ', '-')
    return when (normalized) {
        "mainnet", "kaspa-mainnet" -> "mainnet"
        "testnet-10", "testnet10", "kaspa-testnet-10", "kaspatest" -> "testnet-10"
        else -> null
    }
}

class Kcc20KasivoService(
    private val context: Context? = null,
    private val endpointBases: List<String> = KASCOV_ENDPOINTS
) {

    companion object {
        const val KASCOV_PRIMARY_BASE = "https://kascov.io/v1/kcc20"

        val KASCOV_ENDPOINTS = listOf(
            "https://kascov.io/v1/kcc20",
            "https://api.kaspa.org",
            "https://api.kaspanet.io"
        )

        // Well-known KCC-20 Kaspa Covenant Token Definitions
        val KNOWN_KCC20_TOKENS = emptyMap<String, Kcc20TokenBalanceResult>()

        internal data class ResolvedTokenMeta(
            val ticker: String,
            val name: String,
            val decimals: Int,
            val imageUrl: String?
        )

        internal fun resolveTokenMetadataFromJson(
            tokenId: String,
            token: JSONObject
        ): ResolvedTokenMeta {
            val cleanId = tokenId.trim().lowercase().removePrefix("0x")
            val knownMeta = Kcc20NoteScanner.KNOWN_TOKENS[cleanId]
            val fieldsObj = token.optJSONObject("fields")

            val claimedDecimals = if (token.has("claimed_decimals") && !token.isNull("claimed_decimals")) token.optInt("claimed_decimals") else null
            val displayDecimals = if (token.has("display_decimals") && !token.isNull("display_decimals")) token.optInt("display_decimals") else null
            val rawDecimals = if (token.has("decimals") && !token.isNull("decimals")) token.optInt("decimals") else null
            val fieldDecimals = if (fieldsObj != null && fieldsObj.has("dec") && !fieldsObj.isNull("dec")) fieldsObj.optInt("dec") else null

            val ticker = listOf(
                token.optString("claimed_ticker", ""),
                token.optString("ticker", ""),
                token.optString("symbol", ""),
                fieldsObj?.optString("tick", "") ?: "",
                knownMeta?.ticker ?: "",
                token.optString("claimed_name", ""),
                token.optString("name", "")
            ).map { it.trim() }
                .firstOrNull { it.isNotBlank() && it != "null" }
                ?: "KCC-${cleanId.take(4).uppercase()}"

            val isKbContract = cleanId == KbKcc20Protocol.TOKEN_COVENANT_ID
            val decimals = (claimedDecimals ?: displayDecimals ?: rawDecimals ?: fieldDecimals ?: knownMeta?.decimals)
                ?: if (isKbContract) 3 else 0

            val name = listOf(
                token.optString("claimed_name", ""),
                token.optString("name", ""),
                knownMeta?.name ?: ""
            ).map { it.trim() }
                .firstOrNull { it.isNotBlank() && it != "null" }
                ?: ticker

            val claimedImg = token.optString("claimed_image", "")
            val iconUrl = token.optString("icon_url", "")
            val imageUrl = token.optString("image_url", "")
            val iconImg = token.optString("icon", "")
            val imageImg = token.optString("image", "")
            val logoImg = token.optString("logo", "")
            val metaImg = token.optJSONObject("metadata")?.optString("image", "") ?: ""
            val resolvedImage = listOf(claimedImg, iconUrl, imageUrl, iconImg, imageImg, logoImg, metaImg, knownMeta?.imageUrl.orEmpty())
                .map { it.trim() }
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: if (isKbContract) {
                    "https://storage.googleapis.com/kaspa-rocket-prod-uploads/7c70c6f4-faa9-4d84-ba81-62553168f1a9.png"
                } else null

            return ResolvedTokenMeta(
                ticker = ticker,
                name = name,
                decimals = decimals.coerceIn(0, 18),
                imageUrl = resolvedImage
            )
        }

        /**
         * Resolves candidate program hex strings from a live cell JSON entry per Guide §2.1:
         * uses raw `program` / `redeemScriptHex` if supplied, or rebuilds candidate programs
         * from full cell state (`amount`, `owner`, `owner_scheme`, `borrow_scheme`, `borrow_guard`,
         * `extension_commitment`, and optional `template`/`template_hash`) + pinned templates.
         */
        internal fun extractCandidateProgramHexesFromCellJson(
            cellJson: JSONObject,
            ownerPubKeyHex: String,
            networkPrefix: String,
            txid: String = "",
            vout: Int = -1
        ): List<String> {
            val direct = sequenceOf(
                "program", "program_hex", "redeemScriptHex", "redeem_script_hex", "redeem", "redeemScript", "script"
            ).mapNotNull { key -> cellJson.optString(key, "").takeIf { it.isNotBlank() && it != "null" } }
                .firstOrNull()?.trim()?.lowercase()?.removePrefix("0x")
            if (!direct.isNullOrBlank()) return listOf(direct)

            if (txid.isNotBlank() && vout >= 0) {
                Kcc20NoteScanner.findRedeemScript(txid, vout.toLong(), null)?.let { cached ->
                    if (cached.isNotBlank()) return listOf(cached.lowercase())
                }
            }

            val rawAmountStr = sequenceOf("amount_base_units", "amount", "token_amount", "balance", "balance_str")
                .mapNotNull { key ->
                    if (!cellJson.has(key) || cellJson.isNull(key)) null
                    else cellJson.opt(key)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                }
                .firstOrNull() ?: return emptyList()
            val amount = rawAmountStr.toBigIntegerOrNull()?.takeIf { it.signum() > 0 } ?: return emptyList()

            val cleanOwner = cellJson.optString("owner", ownerPubKeyHex).trim().lowercase().removePrefix("0x")
            if (!cleanOwner.matches(Regex("[0-9a-f]{64}"))) return emptyList()
            val ownerBytes = KbKcc20ScriptCodec.hexToBytes(cleanOwner)

            fun parseIntField(vararg keys: String, defaultVal: Int): Int? {
                for (k in keys) {
                    if (!cellJson.has(k) || cellJson.isNull(k)) continue
                    val raw = cellJson.opt(k)?.toString()?.trim()?.lowercase() ?: continue
                    if (raw == "p2pk" || raw == "p2pk-schnorr/v1") return 0
                    val parsed = raw.removePrefix("0x").toIntOrNull()
                    if (parsed != null) return parsed
                }
                return defaultVal
            }

            val ownerScheme = parseIntField("owner_scheme", "ownerScheme", defaultVal = 0) ?: return emptyList()
            val borrowScheme = parseIntField("borrow_scheme", "borrowScheme", defaultVal = 0) ?: return emptyList()
            if (ownerScheme != 0 || borrowScheme != 0) return emptyList()

            fun parse32ByteHex(vararg keys: String): ByteArray? {
                for (k in keys) {
                    if (!cellJson.has(k) || cellJson.isNull(k)) continue
                    val s = cellJson.optString(k, "").trim().lowercase().removePrefix("0x")
                    if (s.isBlank()) continue
                    if (!s.matches(Regex("[0-9a-f]{64}"))) return null
                    return KbKcc20ScriptCodec.hexToBytes(s)
                }
                return ByteArray(32)
            }

            val borrowGuard = parse32ByteHex("borrow_guard", "borrowGuard") ?: return emptyList()
            val extCommit = parse32ByteHex("extension_commitment", "extensionCommitment") ?: return emptyList()
            if (!extCommit.all { it == 0.toByte() }) return emptyList()

            val claimedTpl = sequenceOf("template", "template_hash", "templateHash")
                .mapNotNull { key -> cellJson.optString(key, "").trim().lowercase().removePrefix("0x").takeIf { it.matches(Regex("[0-9a-f]{64}")) } }
                .firstOrNull()

            val candidateKinds = when (claimedTpl) {
                Kcc20ReferenceProtocol.REF_TEMPLATE_HASH -> listOf(Kcc20ReferenceProtocol.TemplateKind.REFERENCE)
                Kcc20ReferenceProtocol.KB_TEMPLATE_HASH -> listOf(Kcc20ReferenceProtocol.TemplateKind.KB_10X10)
                null -> listOf(Kcc20ReferenceProtocol.TemplateKind.REFERENCE, Kcc20ReferenceProtocol.TemplateKind.KB_10X10)
                else -> return emptyList()
            }

            val claimedAddr = cellJson.optString("address", cellJson.optString("cell_address", "")).trim()
            val built = mutableListOf<String>()
            for (kind in candidateKinds) {
                val pinned = Kcc20ReferenceProtocol.pinnedProgramFor(kind) ?: continue
                val rebuilt = runCatching {
                    Kcc20ReferenceProtocol.programFor(
                        state = Kcc20ReferenceProtocol.CellState(
                            templateHashHex = kind.templateHash,
                            amount = amount,
                            owner = ownerBytes,
                            ownerScheme = ownerScheme,
                            borrowScheme = borrowScheme,
                            borrowGuard = borrowGuard,
                            extensionCommitment = extCommit
                        ),
                        pinnedProgram = pinned
                    )
                }.getOrNull() ?: continue
                if (claimedAddr.isNotBlank()) {
                    val derivedAddr = runCatching { Kcc20ReferenceProtocol.p2shAddress(rebuilt, networkPrefix) }.getOrNull()
                    if (!claimedAddr.equals(derivedAddr, ignoreCase = true)) continue
                }
                built += KbKcc20ScriptCodec.bytesToHex(rebuilt)
            }
            return built
        }
    }

    /**
     * Differentiate raw payload or metadata JSON into exact Kaspa Protocol Taxonomy.
     */
    suspend fun fetchWalletToken(
        publicKeyHex: String,
        contractId: String,
        network: String = "mainnet",
        kasUsd: String? = null,
        walletAddress: String? = null
    ): Result<Kcc20WalletToken> = withContext(Dispatchers.IO) {
        val owner = publicKeyHex.trim().lowercase().removePrefix("0x")
        val tokenId = contractId.trim().lowercase()
        if (!owner.matches(Regex("[0-9a-f]{64}"))) {
            return@withContext Result.failure(IllegalArgumentException("Wallet public key must be 32-byte x-only hex"))
        }
        if (!tokenId.matches(Regex("[0-9a-f]{64}"))) {
            return@withContext Result.failure(IllegalArgumentException("KCC-20 covenant ID must be 32-byte hex"))
        }
        val kascovNetwork = normalizeKascovNetwork(network)
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported KCC-20 network: " + network))
        try {
            fun getJson(path: String): JSONObject {
                val connection = (URL("https://kascov.io/data/$kascovNetwork/$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
                }
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw java.io.IOException("Kascov HTTP ${connection.responseCode} for $path")
                    }
                    return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            }

            val detail = getJson("token/$tokenId")
            val token = detail.optJSONObject("token")
                ?: throw java.io.IOException("Kascov did not return token metadata")
            val balances = detail.optJSONArray("balances") ?: JSONArray()
            fun String.stripKaspaPrefix(): String = removePrefix("kaspa:").removePrefix("kaspatest:")
            val normalizedAddress = walletAddress?.trim()?.lowercase()
            val strippedAddress = normalizedAddress?.stripKaspaPrefix()
            fun isWalletOwner(row: JSONObject): Boolean {
                val rowOwner = row.optString("owner").trim().lowercase().removePrefix("0x")
                val rowAddress = row.optString("owner_address", row.optString("address", row.optString("holder", ""))).trim().lowercase()
                val schemeName = row.optString("owner_scheme_name").trim().lowercase()
                val scheme = row.optString("owner_scheme").trim().lowercase()
                val directPublicKeyMatch = rowOwner == owner
                val addressMatch = normalizedAddress != null && normalizedAddress.isNotBlank() && (
                    rowAddress == normalizedAddress ||
                    (strippedAddress != null && rowAddress.stripKaspaPrefix() == strippedAddress) ||
                    rowOwner == normalizedAddress ||
                    (strippedAddress != null && rowOwner.stripKaspaPrefix() == strippedAddress)
                )
                val isSchnorrKeyHashOwner =
                    rowOwner.startsWith("p2pkh:") &&
                    (schemeName == "p2pkh-schnorr/v1" || scheme == "p2pkh-schnorr/v1" || schemeName.isBlank() || scheme.isBlank())
                val schnorrKeyHashMatch = if (isSchnorrKeyHashOwner) {
                    val digest = Blake3Digest()
                    val publicKeyBytes = owner.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    val hash = ByteArray(32)
                    digest.update(publicKeyBytes, 0, publicKeyBytes.size)
                    digest.doFinal(hash, 0)
                    rowOwner == "p2pkh:" + hash.joinToString("") { "%02x".format(it) }
                } else false
                if (rowOwner.startsWith("p2pkh-ecdsa:") || schemeName == "p2pkh-ecdsa/v1" || scheme == "p2pkh-ecdsa/v1") {
                    return addressMatch
                }
                return directPublicKeyMatch || schnorrKeyHashMatch || addressMatch
            }
            fun exactBalance(row: JSONObject): String? {
                return row.optString("balance_str").takeIf { it.isNotBlank() && it != "null" }
                    ?: row.optString("balance").takeIf { it.isNotBlank() && it != "null" }
                    ?: row.optString("amount").takeIf { it.isNotBlank() && it != "null" }
                    ?: row.optString("value").takeIf { it.isNotBlank() && it != "null" }
                    ?: row.optString("total_balance").takeIf { it.isNotBlank() && it != "null" }
                    ?: row.opt("balance")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            }
            val resolvedMeta = resolveTokenMetadataFromJson(tokenId, token)
            val ticker = resolvedMeta.ticker
            val decimals = resolvedMeta.decimals

            var balanceRaw: String? = null
            for (i in 0 until balances.length()) {
                val row = balances.optJSONObject(i) ?: continue
                if (isWalletOwner(row)) {
                    balanceRaw = exactBalance(row)
                    break
                }
            }

            if (balanceRaw == null) {
                try {
                    val page = getJson("token/$tokenId/holders?limit=100")
                    val rows = page.optJSONArray("holders")
                    if (rows != null) {
                        for (i in 0 until rows.length()) {
                            val row = rows.optJSONObject(i) ?: continue
                            if (isWalletOwner(row)) {
                                balanceRaw = exactBalance(row)
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Failover gracefully
                }
            }
            if ((balanceRaw == null || balanceRaw == "0") && !walletAddress.isNullOrBlank()) {
                try {
                    val historyRes = fetchKcc20History(walletAddress, tokenId).getOrNull()
                    if (!historyRes.isNullOrEmpty()) {
                        var netBaseUnits = java.math.BigInteger.ZERO
                        val cleanTarget = walletAddress.trim().lowercase().stripKaspaPrefix()
                        for (item in historyRes) {
                            val itemTo = item.toAddress.trim().lowercase().stripKaspaPrefix()
                            val itemFrom = item.fromAddress.trim().lowercase().stripKaspaPrefix()
                            val rawAmt = item.amountRaw.trim().toBigIntegerOrNull()
                                ?: java.math.BigDecimal.valueOf(item.amount).movePointRight(decimals).toBigInteger()
                            if (itemTo == cleanTarget) {
                                netBaseUnits += rawAmt
                            }
                            if (itemFrom == cleanTarget) {
                                netBaseUnits -= rawAmt
                            }
                        }
                        if (netBaseUnits.signum() > 0) {
                            balanceRaw = netBaseUnits.toString()
                        }
                    }
                } catch (_: Exception) {}
            }
            if (balanceRaw == null) {
                balanceRaw = "0"
            }

            val marketResponse = try { getJson("token/$tokenId/market") } catch (_: Exception) { null }
            val market = detail.optJSONObject("market")
                ?: marketResponse?.optJSONObject("market")
                ?: marketResponse

            fun parseJsonBigDecimal(obj: JSONObject?, vararg keys: String): java.math.BigDecimal? {
                if (obj == null) return null
                for (key in keys) {
                    if (!obj.has(key) || obj.isNull(key)) continue
                    val str = obj.optString(key, "").trim()
                    if (str.isNotBlank() && str != "null") {
                        val parsed = str.toBigDecimalOrNull()
                        if (parsed != null) return parsed
                    }
                    val d = obj.optDouble(key)
                    if (!d.isNaN()) return java.math.BigDecimal.valueOf(d)
                }
                return null
            }

            var priceKas: String? = null
            var priceUsd: String? = null
            var priceSource: String? = null
            var priceUpdatedAt: Long? = null

            val isVerified = token.optString("status") == "verified"
            val spotNumSompi = parseJsonBigDecimal(market, "spot_num_sompi", "last_quote_sompi", "quote_sompi")
                ?: parseJsonBigDecimal(detail.optJSONObject("market"), "spot_num_sompi", "last_quote_sompi", "quote_sompi")
            val spotDen = parseJsonBigDecimal(market, "spot_den", "last_base_amount", "base_amount")
                ?: parseJsonBigDecimal(detail.optJSONObject("market"), "spot_den", "last_base_amount", "base_amount")

            var currentPriceSompi = if (spotNumSompi != null && spotDen != null && spotDen.signum() > 0) {
                spotNumSompi.multiply(java.math.BigDecimal.TEN.pow(decimals))
                    .divide(spotDen, 18, java.math.RoundingMode.HALF_UP)
            } else null

            if (currentPriceSompi != null) {
                val kas = currentPriceSompi.divide(java.math.BigDecimal("100000000"), 18, java.math.RoundingMode.HALF_UP)
                priceKas = kas.stripTrailingZeros().toPlainString()

                val liveKasUsd = kasUsd?.toBigDecimalOrNull() 
                    ?: java.math.BigDecimal.valueOf(com.example.network.KaspaPriceService.priceState.value.priceUsd).takeIf { it.signum() > 0 }
                    ?: try {
                    val conn = (URL("https://kascov.io/data/price.json").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 4000
                        readTimeout = 4000
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
                    }
                    try {
                        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                            val pJson = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            pJson.optString("kas_usd").toBigDecimalOrNull()
                        } else null
                    } finally {
                        conn.disconnect()
                    }
                } catch (_: Exception) {
                    null
                }
                
                if (liveKasUsd != null && liveKasUsd.signum() > 0) {
                    priceUsd = kas.multiply(liveKasUsd).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                }
                priceSource = "Kascov live spot quote"
                priceUpdatedAt = market?.optLong("last_time_ms")?.takeIf { it > 0 }
                    ?: market?.optLong("updated_at_ms")?.takeIf { it > 0 }
            }

            val change24hBps = parseJsonBigDecimal(market, "change_24h_bps")
                ?: parseJsonBigDecimal(detail.optJSONObject("market"), "change_24h_bps")
            val change24h = change24hBps?.divide(java.math.BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP)
                ?.stripTrailingZeros()?.toPlainString()

            val resolvedImage = resolvedMeta.imageUrl

            val binaryFlag = market?.optInt("binary_flag", market.optInt("binaryFlag", detail.optJSONObject("state")?.optInt("binary_flag", -1) ?: -1)) ?: -1
            val poolCovenantId = market?.optString("poolCovenantId", market.optString("pool_covenant_id", detail.optString("pool_covenant_id", ""))).orEmpty()
            val raisedKasNum = parseJsonBigDecimal(market, "raised_kas", "curve_kas", "kas_reserve", "reserves_kas")
                ?: parseJsonBigDecimal(detail.optJSONObject("market"), "raised_kas", "curve_kas", "kas_reserve", "reserves_kas")
            val raisedKasDouble = raisedKasNum?.toDouble()
            val isGraduatedFlag = (binaryFlag == 1) ||
                poolCovenantId.isNotBlank() ||
                (market?.optBoolean("graduated", false) == true) ||
                (detail.optBoolean("graduated", false) == true) ||
                (market?.optString("type", "") == "amm") ||
                (raisedKasDouble != null && raisedKasDouble >= 125000.0)

            Result.success(Kcc20WalletToken(
                contractId = token.optString("covenant_id", "").trim().lowercase().ifBlank { tokenId },
                ticker = ticker,
                name = resolvedMeta.name,
                balanceRaw = balanceRaw,
                decimals = decimals,
                holderCount = if (token.has("holders")) token.optLong("holders") else null,
                validationStatus = token.optString("status"),
                marketPriceKas = priceKas,
                marketPriceUsd = priceUsd,
                marketPriceSource = priceSource,
                priceUpdatedAtMs = priceUpdatedAt,
                priceChange24hPercent = change24h,
                imageUrl = resolvedImage,
                bondingCurveProgressKas = raisedKasDouble,
                isGraduated = isGraduatedFlag
            ))
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /**
     * Fetch real-time KAS oracle price stream straight from Kascov (https://kascov.io/data/price.json).
     */
    suspend fun fetchKasUsdPriceFromOracle(): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("https://kascov.io/data/price.json").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0")
            }
            try {
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val pJson = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val rate = pJson.optDouble("kas_usd", 0.0)
                    if (rate > 0.0) {
                        return@withContext Result.success(rate)
                    }
                }
                Result.failure(IllegalStateException("KAS/USD oracle did not return a valid price"))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun classifyProtocol(rawPayload: String): KaspaProtocolType {
        return KaspaProtocolType.parseFromJson(rawPayload)
    }

    /**
     * Fetch real live KCC-20 balance for a Kaspa address and token contract ID/ticker from KasCov indexer (kascov.io).
     */
    suspend fun fetchKcc20Balance(
        address: String,
        tokenContractIdOrTicker: String
    ): Result<Kcc20TokenBalanceResult> = withContext(Dispatchers.IO) {
        if (address.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Address cannot be empty"))
        }

        val identifier = tokenContractIdOrTicker.uppercase().trim()
        val known = KNOWN_KCC20_TOKENS[identifier]

        val isTestnet = address.startsWith("kaspatest:")
        val network = if (isTestnet) "testnet-10" else "mainnet"
        val dynamicEndpoints = if (isTestnet) {
            listOf("https://kascov.io/v1/$network/kcc20", "https://api-tn10.kaspa.org")
        } else {
            endpointBases
        }

        for (baseEndpoint in dynamicEndpoints) {
            try {
                val endpointUrl = if (baseEndpoint.contains("kascov.io") || baseEndpoint.contains("kasivo.org")) {
                    "$baseEndpoint/address/$address/token/$identifier"
                } else {
                    "$baseEndpoint/addresses/$address/full-transactions?limit=10"
                }

                val url = URL(endpointUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0 (kascov.io Indexer)")
                }

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    val root = JSONObject(responseText)
                    if (root.has("balance") || root.has("status") || root.has("result")) {
                        val rawBalance = root.optString("balance", "0")
                        val dec = root.optInt("decimals", known?.decimals ?: 8)
                        val doubleBal = rawBalance.toDoubleOrNull()?.div(10.0.pow(dec)) ?: 0.0
                        val contractId = root.optString("contractId", known?.tokenContractId ?: identifier)
                        val tick = root.optString("ticker", known?.ticker ?: identifier)
                        val state = root.optString("state", "active")
                        val holders = root.optLong("holders", known?.holderCount ?: 0L)

                        val result = Kcc20TokenBalanceResult(
                            tokenContractId = contractId,
                            ticker = tick,
                            balance = doubleBal,
                            balanceRaw = rawBalance,
                            decimals = dec,
                            protocolType = KaspaProtocolType.KCC20_COVENANT,
                            contractState = state,
                            holderCount = holders,
                            rawMetadataJson = root.toString(2)
                        )
                        return@withContext Result.success(result)
                    }
                }
            } catch (_: Exception) {
                // Try next live KasCov / Kaspa endpoint
            }
        }

        if (known != null) {
            return@withContext Result.success(known)
        }

        Result.failure(IllegalStateException("KCC-20 balance is unavailable from the configured indexers"))
    }

    /**
     * Fetch real KCC-20 transaction history from KasCov BlockDAG indexer nodes (kascov.io).
     */
    suspend fun fetchKcc20History(
        address: String,
        tokenContractIdOrTicker: String
    ): Result<List<Kcc20TransactionHistoryItem>> = withContext(Dispatchers.IO) {
        val identifier = tokenContractIdOrTicker.uppercase().trim()
        val items = mutableListOf<Kcc20TransactionHistoryItem>()
        val isTestnet = address.trim().lowercase().startsWith("kaspatest:")
        val dynamicEndpoints = if (isTestnet) {
            listOf("https://kascov.io/v1/testnet-10/kcc20", "https://api-tn10.kaspa.org")
        } else {
            KASCOV_ENDPOINTS
        }

        if (address.isNotBlank()) {
            for (baseEndpoint in dynamicEndpoints) {
                try {
                    val endpointUrl = if (baseEndpoint.contains("kascov.io") || baseEndpoint.contains("kasivo.org")) {
                        "$baseEndpoint/history?address=$address&contract=$identifier"
                    } else {
                        "$baseEndpoint/addresses/$address/full-transactions?limit=20"
                    }

                    val url = URL(endpointUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0 (kascov.io Indexer)")
                    }

                    if (conn.responseCode == 200) {
                        val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                        val root = JSONObject(responseText)
                        val resultArray = root.optJSONArray("items") ?: root.optJSONArray("result")
                        if (resultArray != null) {
                            for (i in 0 until resultArray.length()) {
                                val item = resultArray.getJSONObject(i)
                                val txId = item.optString("txId", item.optString("hash", ""))
                                val contractId = item.optString("contractId", identifier)
                                val tick = item.optString("ticker", item.optString("tick", identifier))
                                val op = item.optString("operation", item.optString("op", "TRANSFER")).uppercase()
                                val rawAmt = item.optString("amountRaw", item.optString("amt", "0"))
                                val from = item.optString("fromAddress", item.optString("from", ""))
                                val to = item.optString("toAddress", item.optString("to", ""))
                                val ts = item.optLong("timestamp", item.optLong("mtsAdd", System.currentTimeMillis()))
                                val status = item.optString("status", "ACCEPTED")
                                val dec = item.optInt("decimals", 8)
                                val doubleAmt = rawAmt.toBigDecimalOrNull()?.movePointLeft(dec)?.toDouble() ?: 0.0

                                items.add(
                                    Kcc20TransactionHistoryItem(
                                        txId = txId,
                                        tokenContractId = contractId,
                                        ticker = tick,
                                        operation = op,
                                        amount = doubleAmt,
                                        amountRaw = rawAmt,
                                        fromAddress = from,
                                        toAddress = to,
                                        timestamp = ts,
                                        status = status,
                                        feeKas = 0.0001,
                                        protocolType = KaspaProtocolType.KCC20_COVENANT,
                                        rawJson = item.toString(2)
                                    )
                                )
                            }
                            if (items.isNotEmpty()) break
                        }
                    }
                } catch (_: Exception) {
                    // Failover
                }
            }
        }

        Result.success(items.sortedByDescending { it.timestamp })
    }

    /**
     * Generate a multi-protocol taxonomy overview for an address across L1 KAS, L1 KCC-20, and L2 KRC-20.
     */
    suspend fun fetchProtocolTaxonomySummary(
        address: String,
        l1KasBalance: Double
    ): ProtocolTaxonomySummary = withContext(Dispatchers.IO) {
        val kcc20Balances = mutableListOf<Kcc20TokenBalanceResult>()

        // Query well-known KCC-20 tokens
        KNOWN_KCC20_TOKENS.keys.forEach { tick ->
            val res = fetchKcc20Balance(address, tick).getOrNull()
            if (res != null) {
                kcc20Balances.add(res)
            }
        }

        ProtocolTaxonomySummary(
            address = address,
            l1KasBalance = l1KasBalance,
            kcc20TokenBalances = kcc20Balances,
            krc20TokenBalancesCount = 0,
            primaryProtocol = if (kcc20Balances.any { it.balance > 0 }) KaspaProtocolType.KCC20_COVENANT else KaspaProtocolType.L1_KASPA
        )
    }

    /**
     * Fetch live KCC-20 covenant token cells (UTXOs with redeem bytecode) owned by a wallet pubkey.
     * Maps directly to [KbKcc20StandardAssembler.SpendPiece] for real on-chain covenant spending.
     */
    suspend fun fetchLiveTokenCells(
        contractId: String,
        publicKeyHex: String,
        walletAddress: String? = null
    ): Result<List<KbKcc20StandardAssembler.SpendPiece>> = withContext(Dispatchers.IO) {
        val owner = publicKeyHex.trim().lowercase().removePrefix("0x")
        val tokenId = contractId.trim().lowercase().removePrefix("0x")
        if (!owner.matches(Regex("[0-9a-f]{64}"))) {
            return@withContext Result.failure(IllegalArgumentException("Wallet public key must be 32-byte x-only hex"))
        }
        if (!tokenId.matches(Regex("[0-9a-f]{64}"))) {
            return@withContext Result.failure(IllegalArgumentException("KCC-20 covenant ID must be 32-byte hex"))
        }
        val normalizedAddress = walletAddress?.trim()?.lowercase()
        val network = if (normalizedAddress?.startsWith("kaspatest:") == true) "testnet-10" else "mainnet"
        val networkPrefix = if (network == "testnet-10") "kaspatest" else "kaspa"
        val restNodes = if (network == "testnet-10") {
            listOf("https://api-tn10.kaspa.org", "https://api-tn10.kaspanet.io")
        } else {
            listOf("https://api.kaspa.org", "https://api.kaspanet.io")
        }

        suspend fun fetchNodeUtxos(p2shAddr: String): List<Kcc20ReferenceProtocol.NodeUtxo> {
            for (base in restNodes) {
                try {
                    val conn = (URL("$base/addresses/$p2shAddr/utxos").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                        setRequestProperty("Accept", "application/json")
                    }
                    try {
                        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                            val out = ArrayList<Kcc20ReferenceProtocol.NodeUtxo>(arr.length())
                            for (k in 0 until arr.length()) {
                                val u = arr.optJSONObject(k) ?: continue
                                val op = u.optJSONObject("outpoint") ?: u.optJSONObject("previous_outpoint") ?: u
                                val uTxid = op.optString("transactionId", op.optString("transaction_id", "")).trim().lowercase()
                                val uVout = op.optInt("index", op.optInt("output_index", -1))
                                val entry = u.optJSONObject("utxoEntry") ?: u.optJSONObject("utxo_entry") ?: u
                                val uAmt = entry.optLong("amount", entry.optLong("value", -1L))
                                val uCov = entry.optString("covenantId", entry.optString("covenant_id", ""))
                                    .ifBlank { u.optString("covenantId", u.optString("covenant_id", "")) }
                                    .ifBlank { entry.optJSONObject("covenant")?.let { it.optString("covenantId", it.optString("covenant_id", "")) } ?: "" }
                                    .trim().lowercase().removePrefix("0x").takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                                if (uTxid.isNotBlank() && uVout >= 0 && uAmt > 0L) {
                                    out += Kcc20ReferenceProtocol.NodeUtxo(uTxid, uVout, uAmt, uCov)
                                }
                            }
                            return out
                        }
                    } finally {
                        conn.disconnect()
                    }
                } catch (_: Exception) {}
            }
            throw IllegalStateException("Unable to reach Kaspa node UTXO endpoint for $p2shAddr")
        }

        suspend fun fetchCreatingTxCovenant(txid: String, vout: Int): String? {
            for (base in restNodes) {
                try {
                    val conn = (URL("$base/transactions/$txid").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 6000
                        setRequestProperty("Accept", "application/json")
                    }
                    try {
                        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                            val txJson = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            val outputs = txJson.optJSONArray("outputs") ?: continue
                            val outObj = outputs.optJSONObject(vout) ?: continue
                            val direct = outObj.optString("covenant_id", outObj.optString("covenantId", "")).trim()
                            if (direct.matches(Regex("[0-9a-fA-F]{64}"))) return direct.lowercase()
                            val covObj = outObj.optJSONObject("covenant")
                            val nested = covObj?.optString("covenant_id", covObj.optString("covenantId", "")).orEmpty().trim()
                            if (nested.matches(Regex("[0-9a-fA-F]{64}"))) return nested.lowercase()
                            val verbose = outObj.optJSONObject("verbose_data") ?: outObj.optJSONObject("verboseData")
                            val verbCov = verbose?.optString("covenant_id", verbose.optString("covenantId", "")).orEmpty().trim()
                            if (verbCov.matches(Regex("[0-9a-fA-F]{64}"))) return verbCov.lowercase()
                            return null
                        }
                    } finally {
                        conn.disconnect()
                    }
                } catch (_: Exception) {}
            }
            return null
        }

        try {
            val endpoints = listOfNotNull(
                "https://kascov.io/data/$network/token/$tokenId/cells",
                "https://kascov.io/data/$network/token/$tokenId",
                if (!normalizedAddress.isNullOrBlank()) "https://kascov.io/data/$network/address/$normalizedAddress/cells" else null
            )

            val pieces = mutableListOf<KbKcc20StandardAssembler.SpendPiece>()
            val seenOutpoints = HashSet<String>()

            for (endpointUrl in endpoints) {
                if (pieces.isNotEmpty()) break
                try {
                    val conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 6000
                        readTimeout = 8000
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "KaspaBrowserWallet/1.0 (kascov.io)")
                    }
                    try {
                        if (conn.responseCode != HttpURLConnection.HTTP_OK) continue
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val root = JSONObject(text)
                        val cellsArray = root.optJSONArray("cells")
                            ?: root.optJSONArray("utxos")
                            ?: root.optJSONArray("data")
                            ?: JSONArray()

                        for (i in 0 until cellsArray.length()) {
                            val c = cellsArray.optJSONObject(i) ?: continue
                            val cellCov = c.optString("covenant_id", c.optString("covenantId", "")).trim().lowercase().removePrefix("0x")
                            if (cellCov.isNotBlank() && cellCov != tokenId) continue

                            val cellOwner = c.optString("owner").trim().lowercase().removePrefix("0x")
                            val cellOwnerScheme = c.optString("owner_scheme", c.optString("ownerScheme", "")).trim().lowercase()
                            val schemeOk = cellOwnerScheme.isBlank() || cellOwnerScheme == "0" || cellOwnerScheme == "00" || cellOwnerScheme == "p2pk" || cellOwnerScheme == "p2pk-schnorr/v1"
                            if (!schemeOk || (cellOwner.isNotBlank() && cellOwner != owner)) continue

                            val outpoint = c.optString("outpoint", c.optString("cell_outpoint", ""))
                            val parts = outpoint.split(":")
                            val txid = (if (parts.size == 2 && parts[0].isNotBlank()) parts[0] else c.optString("txid", c.optString("transaction_id", c.optString("tx_id", "")))).trim().lowercase()
                            val idx = if (parts.size == 2) parts[1].toIntOrNull() ?: -1 else c.optInt("index", c.optInt("output_index", c.optInt("vout", -1)))
                            if (!txid.matches(Regex("[0-9a-f]{64}")) || idx < 0) continue
                            if (!seenOutpoints.add("$txid:$idx")) continue

                            val candidateProgramHexes = extractCandidateProgramHexesFromCellJson(
                                cellJson = c,
                                ownerPubKeyHex = owner,
                                networkPrefix = networkPrefix,
                                txid = txid,
                                vout = idx
                            )
                            if (candidateProgramHexes.isEmpty()) continue

                            val claimedCellAddr = c.optString("address", c.optString("cell_address", "")).trim()
                            val claimedSompi = sequenceOf("value_sompi", "sompi", "value", "amount_sompi")
                                .firstOrNull { c.has(it) && !c.isNull(it) }
                                ?.let { c.optLong(it, -1L).takeIf { v -> v > 0L } }

                            for (programHex in candidateProgramHexes) {
                                val programBytes = try { KbKcc20ScriptCodec.hexToBytes(programHex) } catch (_: Exception) { null } ?: continue
                                // Derive authoritative P2SH address from candidate program bytes (Guide §2.3 check 4)
                                val derivedP2sh = Kcc20ReferenceProtocol.p2shAddress(programBytes, networkPrefix)
                                val addrToVerify = if (claimedCellAddr.isNotBlank()) claimedCellAddr else derivedP2sh

                                val verified = try {
                                    Kcc20ReferenceProtocol.verifyCell(
                                        coin = Kcc20ReferenceProtocol.CandidateCell(
                                            redeemScriptHex = programHex,
                                            address = addrToVerify,
                                            txid = txid,
                                            vout = idx,
                                            kasSompi = claimedSompi
                                        ),
                                        expectedCovenantId = tokenId,
                                        networkPrefix = networkPrefix,
                                        utxosAt = ::fetchNodeUtxos,
                                        creatingTxCovenantAt = ::fetchCreatingTxCovenant
                                    )
                                } catch (_: Exception) {
                                    null
                                } ?: continue

                                // Ensure wallet x-only pubkey is the actual scheme-0x00 owner in verified cell state
                                // and no §10 unsupported features (borrow_scheme != 0 or non-zero extension_commitment) are present
                                if (verified.state.ownerScheme != 0x00 ||
                                    verified.state.borrowScheme != 0 ||
                                    !verified.state.extensionCommitment.all { it == 0.toByte() } ||
                                    !KbKcc20ScriptCodec.bytesToHex(verified.state.owner).equals(owner, ignoreCase = true) ||
                                    verified.state.amount.signum() <= 0
                                ) {
                                    continue
                                }

                                Kcc20NoteScanner.registerRedeemScript(txid, idx, null, programHex)
                                pieces.add(
                                    KbKcc20StandardAssembler.SpendPiece(
                                        transactionId = txid,
                                        index = idx,
                                        valueSompi = verified.kasSompi,
                                        redeem = verified.program
                                    )
                                )
                                break
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                } catch (_: Exception) {}
            }

            // Fallback: scan Kaspa REST full-transactions + Kcc20NoteScanner and strictly verify via §2.3
            if (pieces.isEmpty() && !normalizedAddress.isNullOrBlank()) {
                val scanResult = Kcc20NoteScanner.scanWalletNotes(
                    walletAddress = normalizedAddress,
                    walletPublicKeyHex = owner,
                    network = network,
                    knownContractIds = listOf(tokenId)
                )
                val candidates = scanResult.discoveredNotes
                    .filter { it.isUnspent && it.covenantId.equals(tokenId, ignoreCase = true) && it.redeemScriptHex.isNotBlank() && it.rawTokenAmount > 0L }
                    .map { note ->
                        Kcc20ReferenceProtocol.CandidateCell(
                            redeemScriptHex = note.redeemScriptHex,
                            address = note.p2shAddress,
                            txid = note.txId,
                            vout = note.outputIndex,
                            kasSompi = note.sompiAmount.takeIf { it > 0L }
                        )
                    }
                pieces.addAll(
                    verifyCandidateCellsToSpendPieces(
                        candidates = candidates,
                        tokenId = tokenId,
                        ownerPubKeyHex = owner,
                        networkPrefix = networkPrefix,
                        seenOutpoints = seenOutpoints,
                        utxosAt = ::fetchNodeUtxos,
                        creatingTxCovenantAt = ::fetchCreatingTxCovenant
                    )
                )
            }

            if (pieces.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "No live verified KCC-20 $tokenId token cells found for address $normalizedAddress."
                    )
                )
            }

            Result.success(pieces)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies candidate cells against Guide §2.3 checks 1–6 and §10 constraints, returning only
     * spendable P2PK-Schnorr (`ownerScheme == 0x00`, `borrowScheme == 0`, zero `extensionCommitment`) [SpendPiece]s.
     */
    internal suspend fun verifyCandidateCellsToSpendPieces(
        candidates: List<Kcc20ReferenceProtocol.CandidateCell>,
        tokenId: String,
        ownerPubKeyHex: String,
        networkPrefix: String,
        seenOutpoints: MutableSet<String> = HashSet(),
        utxosAt: suspend (String) -> List<Kcc20ReferenceProtocol.NodeUtxo>,
        creatingTxCovenantAt: (suspend (String, Int) -> String?)? = null
    ): List<KbKcc20StandardAssembler.SpendPiece> {
        val owner = ownerPubKeyHex.trim().lowercase().removePrefix("0x")
        val cleanTokenId = tokenId.trim().lowercase().removePrefix("0x")
        val out = mutableListOf<KbKcc20StandardAssembler.SpendPiece>()
        for (candidate in candidates) {
            val outKey = "${candidate.txid.trim().lowercase()}:${candidate.vout}"
            if (!seenOutpoints.add(outKey)) continue
            val verified = try {
                Kcc20ReferenceProtocol.verifyCell(
                    coin = candidate,
                    expectedCovenantId = cleanTokenId,
                    networkPrefix = networkPrefix,
                    utxosAt = utxosAt,
                    creatingTxCovenantAt = creatingTxCovenantAt
                )
            } catch (_: Exception) {
                null
            } ?: continue
            if (verified.state.ownerScheme != 0x00 ||
                verified.state.borrowScheme != 0 ||
                !verified.state.extensionCommitment.all { it == 0.toByte() } ||
                !KbKcc20ScriptCodec.bytesToHex(verified.state.owner).equals(owner, ignoreCase = true) ||
                verified.state.amount.signum() <= 0
            ) {
                continue
            }
            Kcc20NoteScanner.registerRedeemScript(candidate.txid, candidate.vout, null, candidate.redeemScriptHex)
            out += KbKcc20StandardAssembler.SpendPiece(
                transactionId = candidate.txid.trim().lowercase(),
                index = candidate.vout,
                valueSompi = verified.kasSompi,
                redeem = verified.program
            )
        }
        return out
    }
}

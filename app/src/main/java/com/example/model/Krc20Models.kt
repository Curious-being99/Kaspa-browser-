package com.example.model

import org.json.JSONObject

/**
 * Model representing KRC-20 token deployment metadata on Kaspa BlockDAG,
 * including Bonding Curve minting stage and DEX graduation progress.
 */
data class Krc20TokenMetadata(
    val tick: String,
    val tokenId: String, // Deployment hashRev / inscription tx ID on Kaspa
    val protocol: String = "krc-20",
    val maxSupply: String = "0",
    val mintedSupply: String = "0",
    val decimals: Int = 8,
    val limitPerMint: String = "0",
    val premint: String = "0",
    val deployer: String = "",
    val state: String = "deployed",
    val holderTotal: Long = 0L,
    val transferTotal: Long = 0L,
    val rawMetadataJson: String = "",
    val logoUrl: String? = null
) {
    /**
     * Resolves real metadata logo image URL for token rendering.
     */
    val effectiveLogoUrl: String?
        get() {
            if (!logoUrl.isNullOrBlank()) return logoUrl
            return when (tick.uppercase()) {
                "KB" -> "https://storage.googleapis.com/kaspa-rocket-prod-uploads/7c70c6f4-faa9-4d84-ba81-62553168f1a9.png"
                "NACHO" -> "https://raw.githubusercontent.com/kasplex/krc20-tokens/main/logos/nacho.png"
                "KASPER" -> "https://raw.githubusercontent.com/kasplex/krc20-tokens/main/logos/kasper.png"
                "SOMI" -> "https://raw.githubusercontent.com/kasplex/krc20-tokens/main/logos/somi.png"
                "BOMB" -> "https://raw.githubusercontent.com/kasplex/krc20-tokens/main/logos/bomb.png"
                "KASPUMP" -> "https://raw.githubusercontent.com/kasplex/krc20-tokens/main/logos/kaspump.png"
                else -> "https://api.kasplex.org/v1/krc20/logo/${tick.lowercase()}"
            }
        }

    /**
     * Indicates whether the token is currently in its active bonding curve / minting phase.
     */
    val isBondingCurve: Boolean
        get() = state.equals("deployed", ignoreCase = true) || bondingCurveProgress < 99.99

    /**
     * Bonding curve minting progress from 0.0% to 100.0%.
     */
    val bondingCurveProgress: Double
        get() {
            val maxD = maxSupply.toDoubleOrNull() ?: 0.0
            val mintedD = mintedSupply.toDoubleOrNull() ?: 0.0
            return if (maxD > 0.0) {
                ((mintedD / maxD) * 100.0).coerceIn(0.0, 100.0)
            } else if (state.equals("finished", ignoreCase = true)) {
                100.0
            } else {
                50.0
            }
        }

    /**
     * User-facing label for the bonding curve state.
     */
    val bondingCurveStatusLabel: String
        get() = if (isBondingCurve) "In Bonding Curve (%.1f%%)".format(bondingCurveProgress) else "Graduated to DEX"

    /**
     * Pretty-printed formatted JSON representation of token metadata.
     */
    fun formattedJson(): String {
        return if (rawMetadataJson.isNotBlank()) {
            try {
                val root = JSONObject(rawMetadataJson)
                root.put("isBondingCurve", isBondingCurve)
                root.put("bondingCurveProgress", "%.2f%%".format(bondingCurveProgress))
                root.put("logoUrl", effectiveLogoUrl ?: "")
                root.toString(2)
            } catch (_: Exception) {
                rawMetadataJson
            }
        } else {
            val obj = JSONObject()
            obj.put("p", protocol)
            obj.put("op", "deploy")
            obj.put("tick", tick)
            obj.put("max", maxSupply)
            obj.put("lim", limitPerMint)
            obj.put("pre", premint)
            obj.put("dec", decimals)
            obj.put("to", deployer)
            obj.put("tokenId", tokenId)
            obj.put("state", state)
            obj.put("isBondingCurve", isBondingCurve)
            obj.put("bondingCurveProgress", "%.2f%%".format(bondingCurveProgress))
            obj.put("minted", mintedSupply)
            obj.put("holderTotal", holderTotal)
            obj.put("transferTotal", transferTotal)
            obj.put("logoUrl", effectiveLogoUrl ?: "")
            obj.toString(2)
        }
    }
}

/**
 * Model representing a user's token holding with pin status, bonding curve state, and transactions.
 */
data class Krc20TokenBalance(
    val tick: String,
    val balance: Double = 0.0,
    val balanceRaw: String = "0",
    val isPinned: Boolean = false,
    val metadata: Krc20TokenMetadata? = null,
    val recentTransactions: List<Krc20TokenTransaction> = emptyList(),
    val logoUrl: String? = null
) {
    val tokenId: String
        get() = metadata?.tokenId ?: ""

    val decimals: Int
        get() = metadata?.decimals ?: 8

    val isBondingCurve: Boolean
        get() = metadata?.isBondingCurve ?: false

    val bondingCurveProgress: Double
        get() = metadata?.bondingCurveProgress ?: 0.0

    val effectiveLogoUrl: String?
        get() {
            if (!logoUrl.isNullOrBlank()) return logoUrl
            if (!metadata?.effectiveLogoUrl.isNullOrBlank()) return metadata?.effectiveLogoUrl
            return when (tick.uppercase()) {
                "KB" -> "https://storage.googleapis.com/kaspa-rocket-prod-uploads/7c70c6f4-faa9-4d84-ba81-62553168f1a9.png"
                "NACHO" -> "https://raw.githubusercontent.com/kasplex/krc20-tokens/main/logos/nacho.png"
                "KASPER" -> "https://raw.githubusercontent.com/kasplex/krc20-tokens/main/logos/kasper.png"
                "SOMI" -> "https://raw.githubusercontent.com/kasplex/krc20-tokens/main/logos/somi.png"
                "BOMB" -> "https://raw.githubusercontent.com/kasplex/krc20-tokens/main/logos/bomb.png"
                "KASPUMP" -> "https://raw.githubusercontent.com/kasplex/krc20-tokens/main/logos/kaspump.png"
                else -> "https://api.kasplex.org/v1/krc20/logo/${tick.lowercase()}"
            }
        }

    fun formattedBalance(): String {
        return "%,.4f %s".format(balance, tick)
    }
}

/**
 * Model representing a real on-chain transaction or swap/buy operation for a KRC-20 token.
 */
data class Krc20TokenTransaction(
    val txId: String,
    val tokenId: String,
    val tick: String,
    val op: String, // "BUY", "SWAP", "TRANSFER", "MINT", "DEPLOY"
    val amount: Double = 0.0,
    val amountRaw: String = "0",
    val fromAddress: String = "",
    val toAddress: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "ACCEPTED",
    val feeKas: Double = 0.0001,
    val rawJson: String = ""
) {
    /**
     * Pretty-printed formatted JSON string for this transaction.
     */
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
            obj.put("tokenId", tokenId)
            obj.put("p", "krc-20")
            obj.put("op", op.lowercase())
            obj.put("tick", tick)
            obj.put("amt", amountRaw.ifBlank { amount.toString() })
            obj.put("from", fromAddress)
            obj.put("to", toAddress)
            obj.put("timestamp", timestamp)
            obj.put("feeKas", feeKas)
            obj.put("status", status)
            obj.toString(2)
        }
    }
}

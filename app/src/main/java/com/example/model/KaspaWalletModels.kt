package com.example.model

data class KaspaWalletState(
    val kaspaAddress: String = "",
    val balanceSompis: Long = 0L,
    val balanceKas: Double = 0.0,
    val priceUsd: Double = 0.0,
    val balanceUsd: Double = 0.0,
    val utxosCount: Int = 0,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val statusNotice: String? = null,
    val recentTransactions: List<KaspaTransactionItem> = emptyList(),
    val networkStatus: String = "Kaspa BlockDAG Mainnet"
)

data class KaspaTransactionItem(
    val txId: String,
    val blockTime: Long,
    val amountKas: Double,
    val type: String, // "RECEIVED", "SENT", "DAG_MINT"
    val isAccepted: Boolean = true,
    val feeKas: Double = 0.0001,
    val counterpartyAddress: String = ""
)

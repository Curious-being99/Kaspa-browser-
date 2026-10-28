package com.example.network.kaspa

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class Kcc20SwapRecoveryStatus {
    PREPARED,
    BROADCAST_UNKNOWN,
    CONFIRMED,
    CONFLICTED
}

data class Kcc20SwapInput(val transactionId: String, val outputIndex: Long) {
    fun key(): String = "${transactionId.lowercase()}:$outputIndex"
}

data class Kcc20SignedSwapRecord(
    val swapId: String,
    val network: String,
    val walletAddress: String,
    val tokenId: String,
    val signedTransactionHex: String,
    val inputs: List<Kcc20SwapInput>,
    val status: Kcc20SwapRecoveryStatus,
    val createdAtMillis: Long,
    val transactionId: String? = null
)

data class Kcc20SwapHistoryPage(
    val transactions: List<Kcc20HistoryTransaction>,
    val nextOffset: Int?,
    val complete: Boolean
)

data class Kcc20HistoryTransaction(
    val transactionId: String,
    val spentOutpoints: Set<String>,
    val accepted: Boolean
)

sealed interface Kcc20SwapConflictResult {
    data object NoConflict : Kcc20SwapConflictResult
    data class Conflict(val spendingTransactionId: String) : Kcc20SwapConflictResult
    data class Unknown(val reason: String) : Kcc20SwapConflictResult
}

fun interface Kcc20SwapHistorySource {
    suspend fun loadPage(address: String, offset: Int, limit: Int): Kcc20SwapHistoryPage
}

object Kcc20SwapConflictChecker {
    const val PAGE_SIZE = 50
    const val MAX_PAGES_PER_ADDRESS = 20
    const val MAX_TOTAL_PAGES = 100

    suspend fun check(
        address: String,
        inputs: List<Kcc20SwapInput>,
        source: Kcc20SwapHistorySource
    ): Kcc20SwapConflictResult {
        if (address.isBlank() || inputs.isEmpty()) {
            return Kcc20SwapConflictResult.Unknown("Missing wallet address or swap inputs")
        }
        val targets = inputs.map { it.key() }.toSet()
        var offset = 0
        var pages = 0
        while (pages < MAX_PAGES_PER_ADDRESS && pages < MAX_TOTAL_PAGES) {
            val page = try {
                source.loadPage(address, offset, PAGE_SIZE)
            } catch (_: Exception) {
                return Kcc20SwapConflictResult.Unknown("Transaction history could not be fully checked")
            }
            pages++
            for (transaction in page.transactions) {
                if (transaction.accepted && transaction.spentOutpoints.any { it.lowercase() in targets }) {
                    return Kcc20SwapConflictResult.Conflict(transaction.transactionId)
                }
            }
            if (page.complete) return Kcc20SwapConflictResult.NoConflict
            val next = page.nextOffset
                ?: return Kcc20SwapConflictResult.Unknown("Transaction history pagination is incomplete")
            if (next <= offset) {
                return Kcc20SwapConflictResult.Unknown("Transaction history pagination did not advance")
            }
            offset = next
        }
        return Kcc20SwapConflictResult.Unknown("Transaction history scan reached its safety limit")
    }
}

class Kcc20SignedSwapStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun save(record: Kcc20SignedSwapRecord) {
        require(record.swapId.isNotBlank())
        require(record.network.isNotBlank())
        require(record.walletAddress.isNotBlank())
        require(record.tokenId.isNotBlank())
        require(record.signedTransactionHex.matches(Regex("(?i)^(?:[0-9a-f]{2})+$")))
        require(record.inputs.isNotEmpty())
        val json = JSONObject().apply {
            put("swapId", record.swapId)
            put("network", record.network)
            put("walletAddress", record.walletAddress)
            put("tokenId", record.tokenId)
            put("signedTransactionHex", record.signedTransactionHex)
            put("inputs", JSONArray().apply {
                record.inputs.forEach { input ->
                    put(JSONObject().put("transactionId", input.transactionId).put("outputIndex", input.outputIndex))
                }
            })
            put("status", record.status.name)
            put("createdAtMillis", record.createdAtMillis)
            put("transactionId", record.transactionId)
        }
        prefs.edit().putString(record.swapId, encrypt(json.toString())).apply()
    }

    @Synchronized
    fun get(swapId: String): Kcc20SignedSwapRecord? {
        val encrypted = prefs.getString(swapId, null) ?: return null
        return try {
            val json = JSONObject(decrypt(encrypted))
            val inputsJson = json.getJSONArray("inputs")
            val inputs = (0 until inputsJson.length()).map { index ->
                val item = inputsJson.getJSONObject(index)
                Kcc20SwapInput(item.getString("transactionId"), item.getLong("outputIndex"))
            }
            Kcc20SignedSwapRecord(
                swapId = json.getString("swapId"),
                network = json.getString("network"),
                walletAddress = json.getString("walletAddress"),
                tokenId = json.getString("tokenId"),
                signedTransactionHex = json.getString("signedTransactionHex"),
                inputs = inputs,
                status = Kcc20SwapRecoveryStatus.valueOf(json.getString("status")),
                createdAtMillis = json.getLong("createdAtMillis"),
                transactionId = json.optString("transactionId").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun remove(swapId: String) {
        prefs.edit().remove(swapId).apply()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        require(combined.size > IV_LENGTH)
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "kcc20_signed_swap_recovery"
        const val KEY_ALIAS = "kaspa_browser_kcc20_swap_recovery_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}


class KaspaRestKcc20SwapHistorySource(
    private val apiBase: String,
    private val httpClient: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .build()
) : Kcc20SwapHistorySource {
    init {
        val parsed = java.net.URI(apiBase)
        require(parsed.scheme == "https" && parsed.host != null && parsed.userInfo == null)
        require(parsed.path.isNullOrEmpty() || parsed.path == "/")
        require(parsed.query == null && parsed.fragment == null)
    }

    override suspend fun loadPage(address: String, offset: Int, limit: Int): Kcc20SwapHistoryPage =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(address.matches(Regex("(?i)^kaspa(?:test|dev)?1:[a-z0-9]+$")))
            require(offset >= 0 && limit == Kcc20SwapConflictChecker.PAGE_SIZE)
            val url = "$apiBase/addresses/${java.net.URLEncoder.encode(address, "UTF-8")}/full-transactions?limit=$limit&offset=$offset"
            val request = okhttp3.Request.Builder().url(url).get().header("Accept", "application/json").build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("Transaction history request failed with HTTP ${response.code}")
                val body = response.body?.string() ?: throw java.io.IOException("Transaction history response was empty")
                val rows = org.json.JSONArray(body)
                if (rows.length() > limit) throw java.io.IOException("Transaction history returned more rows than requested")
                val transactions = (0 until rows.length()).map { index ->
                    val row = rows.optJSONObject(index) ?: throw java.io.IOException("Transaction history row was invalid")
                    val txId = row.optString("transaction_id", "")
                    if (!txId.matches(Regex("(?i)^[0-9a-f]{64}$"))) throw java.io.IOException("Transaction history contained an invalid transaction ID")
                    val inputs = row.optJSONArray("inputs") ?: throw java.io.IOException("Transaction history omitted transaction inputs")
                    val spent = (0 until inputs.length()).mapNotNull { inputIndex ->
                        val input = inputs.optJSONObject(inputIndex) ?: return@mapNotNull null
                        val outpoint = input.optJSONObject("previous_outpoint")
                            ?: input.optJSONObject("previousOutpoint")
                        if (outpoint == null) return@mapNotNull null
                        val origin = outpoint.optString("transaction_id", outpoint.optString("transactionId", ""))
                        val outputIndex = if (outpoint.has("index")) outpoint.optLong("index", -1L)
                            else outpoint.optLong("output_index", -1L)
                        if (!origin.matches(Regex("(?i)^[0-9a-f]{64}$")) || outputIndex < 0) null
                        else "${origin.lowercase()}:$outputIndex"
                    }.toSet()
                    Kcc20HistoryTransaction(
                        transactionId = txId.lowercase(),
                        spentOutpoints = spent,
                        accepted = row.optBoolean("is_accepted", false)
                    )
                }
                Kcc20SwapHistoryPage(
                    transactions = transactions,
                    nextOffset = if (rows.length() == limit) offset + rows.length() else null,
                    complete = rows.length() < limit
                )
            }
        }
}

sealed interface Kcc20SwapRecoveryDecision {
    data object KeepWaiting : Kcc20SwapRecoveryDecision
    data object AlreadyConfirmed : Kcc20SwapRecoveryDecision
    data object OriginalTransactionMayBeResubmittedWithUserApproval : Kcc20SwapRecoveryDecision
    data class DoNotResubmit(val spendingTransactionId: String) : Kcc20SwapRecoveryDecision
    data class StopForManualReview(val reason: String) : Kcc20SwapRecoveryDecision
}

class Kcc20SwapRecoveryCoordinator(
    private val store: Kcc20SignedSwapStore,
    private val historySource: Kcc20SwapHistorySource
) {
    suspend fun inspect(swapId: String): Kcc20SwapRecoveryDecision {
        val record = store.get(swapId)
            ?: return Kcc20SwapRecoveryDecision.StopForManualReview("Saved signed swap record is unavailable")
        if (record.status == Kcc20SwapRecoveryStatus.CONFIRMED) {
            return Kcc20SwapRecoveryDecision.AlreadyConfirmed
        }
        if (record.status == Kcc20SwapRecoveryStatus.CONFLICTED) {
            return Kcc20SwapRecoveryDecision.StopForManualReview("This swap was already marked conflicted")
        }
        val result = Kcc20SwapConflictChecker.check(record.walletAddress, record.inputs, historySource)
        return when (result) {
            is Kcc20SwapConflictResult.Conflict -> {
                store.save(record.copy(status = Kcc20SwapRecoveryStatus.CONFLICTED))
                Kcc20SwapRecoveryDecision.DoNotResubmit(result.spendingTransactionId)
            }
            Kcc20SwapConflictResult.NoConflict -> {
                if (record.status == Kcc20SwapRecoveryStatus.BROADCAST_UNKNOWN) {
                    Kcc20SwapRecoveryDecision.OriginalTransactionMayBeResubmittedWithUserApproval
                } else {
                    Kcc20SwapRecoveryDecision.KeepWaiting
                }
            }
            is Kcc20SwapConflictResult.Unknown -> {
                Kcc20SwapRecoveryDecision.StopForManualReview(result.reason)
            }
        }
    }
}

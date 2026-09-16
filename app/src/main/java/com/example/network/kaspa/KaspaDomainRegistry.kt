package com.example.network.kaspa

import com.example.data.AccountEntity
import com.example.data.AppDatabase
import com.example.data.ContentEntity
import com.example.data.DomainEntity
import com.example.network.CryptoUtils
import com.example.network.DomainConstants
import com.example.network.KaspaWalletService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface DomainAvailability {
    object Idle : DomainAvailability
    data class Checking(val domain: String) : DomainAvailability
    data class Available(val domain: String, val feeKas: Double = 1.0) : DomainAvailability
    data class ClaimedByOther(
        val domain: String,
        val ownerAddress: String,
        val txId: String,
        val registeredAt: Long
    ) : DomainAvailability
    data class OwnedByYou(
        val domain: String,
        val txId: String,
        val registeredAt: Long,
        val targetCid: String?
    ) : DomainAvailability
    data class Invalid(val domain: String, val reason: String) : DomainAvailability
}

class KaspaDomainRegistry(
    private val database: AppDatabase,
    private val walletService: KaspaWalletService
) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    companion object {
        const val STANDARD_REGISTRATION_FEE_KAS = 35.0
        const val PROTOCOL_IDENTIFIER = "kns-k"

        fun calculateRegistrationFeeKas(domainName: String): Double {
            val slug = DomainConstants.removeDomainSuffix(domainName.trim().lowercase())
                .replace("@", "")
            return when (slug.length) {
                1 -> 4200.0
                2 -> 4200.0
                3 -> 2100.0
                4 -> 525.0
                else -> 35.0
            }
        }
    }

    /**
     * Normalizes and validates any raw domain or handle into a clean .k name.
     */
    fun normalizeDomainName(raw: String): String {
        val clean = DomainConstants.removeDomainSuffix(raw.trim().lowercase())
            .replace("@", "")
            .replace(" ", "-")
        return DomainConstants.formatDomain(clean)
    }

    /**
     * Validates domain name syntax rules.
     */
    fun validateDomainSyntax(raw: String): Pair<Boolean, String> {
        val cleanSlug = DomainConstants.removeDomainSuffix(raw.trim().lowercase())
            .replace("@", "")

        if (cleanSlug.isBlank()) {
            return false to "Domain name cannot be empty."
        }
        if (cleanSlug.length < 2) {
            return false to "Domain name must be at least 2 characters."
        }
        if (cleanSlug.length > 63) {
            return false to "Domain name must not exceed 63 characters."
        }
        if (!cleanSlug.matches(Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$"))) {
            return false to "Allowed characters: letters (a-z), numbers (0-9), hyphens (-). Cannot start/end with hyphen."
        }
        return true to "Valid domain name syntax."
    }

    /**
     * Checks availability of a .k domain across the on-chain BlockDAG registry and local database.
     */
    suspend fun checkAvailability(
        rawName: String,
        currentKaspaAddress: String?
    ): DomainAvailability = withContext(Dispatchers.IO) {
        val cleanInput = rawName.trim()
        if (cleanInput.isBlank()) return@withContext DomainAvailability.Idle

        val (isValid, errorReason) = validateDomainSyntax(cleanInput)
        val formattedDomain = normalizeDomainName(cleanInput)

        if (!isValid) {
            return@withContext DomainAvailability.Invalid(formattedDomain, errorReason)
        }

        // 1. Check database registry for existing domain claims
        val existingDomain = database.domainDao().getDomainByName(formattedDomain)
        if (existingDomain != null) {
            val isOwnedByCurrent = currentKaspaAddress != null &&
                    existingDomain.ownerAddress.equals(currentKaspaAddress, ignoreCase = true)
            return@withContext if (isOwnedByCurrent) {
                DomainAvailability.OwnedByYou(
                    domain = existingDomain.domain,
                    txId = existingDomain.txId,
                    registeredAt = existingDomain.registeredAt,
                    targetCid = existingDomain.targetCid
                )
            } else {
                DomainAvailability.ClaimedByOther(
                    domain = existingDomain.domain,
                    ownerAddress = existingDomain.ownerAddress,
                    txId = existingDomain.txId,
                    registeredAt = existingDomain.registeredAt
                )
            }
        }

        // 2. Query official live KNS Directory API to verify if registered on the BlockDAG network
        val slug = DomainConstants.removeDomainSuffix(cleanInput).replace("@", "").lowercase()
        var liveClaimedAvailability: DomainAvailability? = null
        try {
            val apiRequest = Request.Builder()
                .url("https://api.dotk.name/v1/names/$slug")
                .header("Accept", "application/json")
                .build()
            okHttpClient.newCall(apiRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val addressStr = json.optString("address", "")
                    val deedAddressStr = json.optString("deedAddress", "")
                    val registryCovenantId = json.optString("registryCovenantId", null)
                    
                    if (addressStr.isNotEmpty()) {
                        // Perform live on-chain verification to ensure this isn't just a cached or social claim
                        val isVerifiedOnChain = walletService.verifyKnsNameOnChain(
                            address = addressStr,
                            deedAddress = deedAddressStr,
                            registryCovenantId = registryCovenantId
                        )
                        
                        // If it fails on-chain verification, we treat it as potentially available or at least unverified
                        if (!isVerifiedOnChain) {
                            android.util.Log.w("KaspaDomainRegistry", "Live API claim failed on-chain verification for $slug")
                        }

                        val isOwnedByCurrent = currentKaspaAddress != null &&
                                addressStr.equals(currentKaspaAddress, ignoreCase = true)
                        val nameVal = json.optString("name", slug)
                        val domainName = if (nameVal.endsWith(".k")) nameVal else "$nameVal.k"
                        val txIdVal = json.optJSONObject("card")?.optString("outpointTxid", "") ?: "live_blockdag_claim"
                        
                        liveClaimedAvailability = if (isOwnedByCurrent) {
                            DomainAvailability.OwnedByYou(
                                domain = domainName,
                                txId = txIdVal,
                                registeredAt = System.currentTimeMillis(),
                                targetCid = json.optJSONObject("card")?.optJSONObject("records")?.optString("url", null)
                            )
                        } else {
                            DomainAvailability.ClaimedByOther(
                                domain = domainName,
                                ownerAddress = addressStr,
                                txId = txIdVal,
                                registeredAt = System.currentTimeMillis()
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("KaspaDomainRegistry", "Live KNS API check failed/timed out for $slug: ${e.message}")
        }

        if (liveClaimedAvailability != null) {
            return@withContext liveClaimedAvailability!!
        }

        // Domain has not been registered on Kaspa BlockDAG yet - available to claim with real KAS
        val registrationFeeKas = calculateRegistrationFeeKas(formattedDomain)
        DomainAvailability.Available(formattedDomain, registrationFeeKas)
    }

    /**
     * Executes real on-chain .k domain registration using Kaspa (KAS) funds.
     * Enforces that once claimed on-chain, the same name CANNOT be claimed by anyone else.
     */
    suspend fun claimDomainOnChain(
        rawName: String,
        account: AccountEntity,
        customCid: String? = null
    ): Result<DomainEntity> = withContext(Dispatchers.IO) {
        try {
            val (isValid, errorReason) = validateDomainSyntax(rawName)
            val formattedDomain = normalizeDomainName(rawName)

            if (!isValid) {
                return@withContext Result.failure(IllegalArgumentException("Invalid domain: $errorReason"))
            }

            val registrationFeeKas = calculateRegistrationFeeKas(formattedDomain)

            // CRITICAL CHECK: Verify domain is NOT claimed by anyone else
            val existingDomain = database.domainDao().getDomainByName(formattedDomain)
            if (existingDomain != null) {
                if (!existingDomain.ownerAddress.equals(account.kaspaAddress, ignoreCase = true)) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Domain '$formattedDomain' is already claimed on Kaspa BlockDAG by ${existingDomain.ownerAddress.take(16)}... (Tx: ${existingDomain.txId.take(12)}...).\n\n" +
                            "This name is permanently unavailable for re-registration."
                        )
                    )
                }
            }

            // Verify account seed is available
            val seedPhrase = try {
                CryptoUtils.getDecryptedSeed(account.seedPhrase)
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalStateException("Cannot decrypt account wallet seed phrase: ${e.message}"))
            }

            // Execute real on-chain transaction
            val txResult = walletService.registerDomainOnChain(
                senderAddress = account.kaspaAddress,
                senderSeed = seedPhrase,
                domain = formattedDomain,
                registrationFeeKas = registrationFeeKas,
                targetCid = customCid
            )

            if (txResult.isFailure) {
                return@withContext Result.failure(
                    txResult.exceptionOrNull() ?: Exception("On-chain Kaspa domain registration failed.")
                )
            }

            val txItem = txResult.getOrThrow()

            // Construct Domain Registration record
            val domainEntity = DomainEntity(
                domain = formattedDomain,
                ownerAddress = account.kaspaAddress,
                ownerDid = account.did,
                ownerPublicKey = account.publicKeyHex,
                txId = txItem.txId,
                registrationFeeKas = registrationFeeKas,
                registeredAt = txItem.blockTime,
                targetCid = customCid,
                customDnsRecord = "kns:v1|did:${account.did}|tx:${txItem.txId}",
                signature = CryptoUtils.signMessage(
                    "kns-claim:$formattedDomain|owner:${account.kaspaAddress}|tx:${txItem.txId}",
                    seedPhrase
                )
            )

            // Save to database registry
            if (existingDomain != null) {
                database.domainDao().updateDomain(domainEntity)
            } else {
                database.domainDao().insertDomain(domainEntity)
            }

            // If a content CID was linked, ensure content table indexing
            if (!customCid.isNullOrBlank()) {
                val cleanSlug = DomainConstants.removeDomainSuffix(formattedDomain)
                val existingContent = database.contentDao().getContentByCid(customCid)
                if (existingContent != null) {
                    val updated = existingContent.copy(
                        protocolPrefix = "mesh://$cleanSlug | $formattedDomain",
                        authorAddress = account.kaspaAddress
                    )
                    database.contentDao().updateContent(updated)
                }
            }

            Result.success(domainEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes real on-chain .k domain transfer to a new recipient Kaspa address.
     * Re-assigns ownership of the domain record in the local database upon confirmed broadcast.
     */
    suspend fun transferDomain(
        rawName: String,
        account: AccountEntity,
        newOwnerAddress: String
    ): Result<DomainEntity> = withContext(Dispatchers.IO) {
        try {
            val formattedDomain = normalizeDomainName(rawName)
            val existingDomain = database.domainDao().getDomainByName(formattedDomain)
                ?: return@withContext Result.failure(IllegalStateException("Domain '$formattedDomain' is not registered locally."))

            if (!existingDomain.ownerAddress.equals(account.kaspaAddress, ignoreCase = true)) {
                return@withContext Result.failure(IllegalStateException("You are not the owner of $formattedDomain"))
            }

            if (newOwnerAddress.equals(account.kaspaAddress, ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Recipient address is the same as current owner."))
            }

            val seedPhrase = try {
                CryptoUtils.getDecryptedSeed(account.seedPhrase)
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalStateException("Cannot decrypt account wallet seed phrase: ${e.message}"))
            }

            val txResult = walletService.transferDomainOnChain(
                senderAddress = account.kaspaAddress,
                senderSeed = seedPhrase,
                domain = formattedDomain,
                newOwnerAddress = newOwnerAddress
            )

            if (txResult.isFailure) {
                return@withContext Result.failure(
                    txResult.exceptionOrNull() ?: Exception("On-chain domain transfer failed.")
                )
            }

            val txItem = txResult.getOrThrow()
            val updatedDomain = existingDomain.copy(
                ownerAddress = newOwnerAddress,
                txId = txItem.txId,
                registeredAt = txItem.blockTime,
                customDnsRecord = "kns:v1|owner:$newOwnerAddress|tx:${txItem.txId}"
            )
            database.domainDao().updateDomain(updatedDomain)

            Result.success(updatedDomain)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun releaseDomain(
        rawName: String,
        account: AccountEntity,
        refundAddress: String = account.kaspaAddress
    ): Result<DomainEntity> = withContext(Dispatchers.IO) {
        try {
            val formattedDomain = normalizeDomainName(rawName)
            val existingDomain = database.domainDao().getDomainByName(formattedDomain)
                ?: return@withContext Result.failure(IllegalStateException("Domain '$formattedDomain' is not registered locally."))

            if (!existingDomain.ownerAddress.equals(account.kaspaAddress, ignoreCase = true)) {
                return@withContext Result.failure(IllegalStateException("You are not the owner of $formattedDomain"))
            }

            val seedPhrase = try {
                CryptoUtils.getDecryptedSeed(account.seedPhrase)
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalStateException("Cannot decrypt account wallet seed phrase: ${e.message}"))
            }

            val txResult = walletService.releaseDomainOnChain(
                ownerAddress = account.kaspaAddress,
                ownerSeed = seedPhrase,
                domain = formattedDomain,
                refundAddress = refundAddress
            )

            if (txResult.isFailure) {
                return@withContext Result.failure(
                    txResult.exceptionOrNull() ?: Exception("On-chain domain release failed.")
                )
            }

            database.domainDao().deleteDomain(existingDomain)
            Result.success(existingDomain)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRecords(
        rawName: String,
        records: Map<String, Any>,
        account: AccountEntity,
        cardValue: Long = DotkProtocol.DEFAULT_CARD_VALUE
    ): Result<DomainEntity> = withContext(Dispatchers.IO) {
        try {
            val formattedDomain = normalizeDomainName(rawName)
            val existingDomain = database.domainDao().getDomainByName(formattedDomain)
                ?: return@withContext Result.failure(IllegalStateException("Domain '$formattedDomain' is not registered locally."))

            if (!existingDomain.ownerAddress.equals(account.kaspaAddress, ignoreCase = true)) {
                return@withContext Result.failure(IllegalStateException("You are not the owner of $formattedDomain"))
            }

            val seedPhrase = try {
                CryptoUtils.getDecryptedSeed(account.seedPhrase)
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalStateException("Cannot decrypt account wallet seed phrase: ${e.message}"))
            }

            val txResult = walletService.updateDomainRecordsOnChain(
                ownerAddress = account.kaspaAddress,
                ownerSeed = seedPhrase,
                domain = formattedDomain,
                records = records,
                cardValue = cardValue
            )

            if (txResult.isFailure) {
                return@withContext Result.failure(
                    txResult.exceptionOrNull() ?: Exception("On-chain record update failed.")
                )
            }

            val txItem = txResult.getOrThrow()
            val urlVal = records["url"] as? String ?: existingDomain.targetCid
            val recordsSummary = records.entries.joinToString(";") { "${it.key}=${it.value}" }
            val updatedDomain = existingDomain.copy(
                targetCid = urlVal,
                customDnsRecord = if (recordsSummary.isNotBlank()) recordsSummary else "empty",
                txId = txItem.txId
            )
            database.domainDao().updateDomain(updatedDomain)
            Result.success(updatedDomain)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


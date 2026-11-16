package com.example.security

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.AuthenticatorSecretDao
import com.example.data.AuthenticatorSecretEntity
import com.example.network.CryptoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import java.util.UUID

data class AuthenticatorAccount(
    val id: String = UUID.randomUUID().toString(),
    val siteHost: String = "",
    val siteOrigin: String = "",
    val issuer: String,
    val accountName: String,
    val encryptedSecret: String,
    val type: OtpAuthType = OtpAuthType.TOTP,
    val algorithm: OtpHashAlgorithm = OtpHashAlgorithm.SHA1,
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    val counter: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

/**
 * Room-backed, hardware-Keystore AES-256-GCM encrypted repository for TOTP (Time-based) and
 * HOTP (Counter-based) 2FA secrets. Each credential is strictly isolated per normalized site
 * host both at the Room query level and cryptographically via GCM AAD + per-site key binding.
 */
class AuthenticatorRepository(
    private val dao: AuthenticatorSecretDao,
    context: Context? = null,
    private val keyPhrase: String = ""
) {
    constructor(context: Context, keyPhrase: String = "") : this(
        dao = AppDatabase.getDatabase(context.applicationContext).authenticatorSecretDao(),
        context = context.applicationContext,
        keyPhrase = keyPhrase
    )

    private val appContext: Context? = context?.applicationContext
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _accounts = MutableStateFlow<List<AuthenticatorAccount>>(emptyList())
    val accounts: StateFlow<List<AuthenticatorAccount>> = _accounts.asStateFlow()

    val accountsFlow: Flow<List<AuthenticatorAccount>> =
        dao.getAllSecrets().map { rows -> rows.map { it.toDomain() } }

    init {
        repoScope.launch {
            migrateLegacyPrefsIfNeeded()
            dao.getAllSecrets().collect { rows ->
                _accounts.value = rows.map { it.toDomain() }
            }
        }
    }

    fun accountsForSiteFlow(siteUrlOrHost: String): Flow<List<AuthenticatorAccount>> {
        val (siteHost, _) = normalizeSiteScope(siteUrlOrHost, fallbackLabel = siteUrlOrHost)
        return dao.getSecretsForSiteFlow(siteHost).map { rows -> rows.map { it.toDomain() } }
    }

    suspend fun getAllAccountsSuspend(): List<AuthenticatorAccount> = withContext(Dispatchers.IO) {
        migrateLegacyPrefsIfNeeded()
        val list = dao.getAllSecretsList().map { it.toDomain() }
        _accounts.value = list
        list
    }

    suspend fun getAccountsForSiteSuspend(siteUrlOrHost: String): List<AuthenticatorAccount> =
        withContext(Dispatchers.IO) {
            val (siteHost, _) = normalizeSiteScope(siteUrlOrHost, fallbackLabel = siteUrlOrHost)
            dao.getSecretsForSite(siteHost).map { it.toDomain() }
        }

    fun reload() {
        runBlocking(Dispatchers.IO) {
            migrateLegacyPrefsIfNeeded()
            val refreshed = dao.getAllSecretsList().map { it.toDomain() }
            _accounts.value = refreshed
        }
    }

    suspend fun addFromOtpAuthUriSuspend(
        rawUri: String,
        siteUrl: String? = null
    ): AuthenticatorAccount {
        val parsed = TotpAuthenticator.parseOtpAuthUri(rawUri)
            ?: throw IllegalArgumentException("Invalid otpauth:// 2FA URI or secret")
        return addManualSuspend(
            issuer = parsed.issuer,
            accountName = parsed.accountName,
            rawBase32Secret = parsed.base32Secret,
            type = parsed.type,
            algorithm = parsed.algorithm,
            digits = parsed.digits,
            periodSeconds = parsed.periodSeconds,
            counter = parsed.counter,
            siteUrl = siteUrl
        )
    }

    fun addFromOtpAuthUri(rawUri: String, siteUrl: String? = null): AuthenticatorAccount =
        runBlocking(Dispatchers.IO) {
            addFromOtpAuthUriSuspend(rawUri = rawUri, siteUrl = siteUrl)
        }

    suspend fun addManualSuspend(
        issuer: String,
        accountName: String,
        rawBase32Secret: String,
        type: OtpAuthType = OtpAuthType.TOTP,
        algorithm: OtpHashAlgorithm = OtpHashAlgorithm.SHA1,
        digits: Int = 6,
        periodSeconds: Int = 30,
        counter: Long = 0L,
        siteUrl: String? = null
    ): AuthenticatorAccount = withContext(Dispatchers.IO) {
        val normalizedSecret = TotpAuthenticator.normalizeBase32Secret(rawBase32Secret)
        require(TotpAuthenticator.isValidBase32Secret(normalizedSecret)) {
            "Secret must be valid Base32 (A-Z, 2-7, min 8 chars)"
        }
        val cleanIssuer = issuer.trim().ifBlank { "Authenticator" }
        val cleanAccount = accountName.trim()
        val id = UUID.randomUUID().toString()
        val (siteHost, siteOrigin) = normalizeSiteScope(
            siteUrlOrHost = siteUrl,
            fallbackLabel = cleanIssuer
        )
        val now = System.currentTimeMillis()
        val encrypted = CryptoUtils.encryptSiteIsolatedAes256(
            plainText = normalizedSecret,
            siteScope = siteHost,
            recordId = id,
            keyPhrase = keyPhrase
        )
        val entity = AuthenticatorSecretEntity(
            id = id,
            siteHost = siteHost,
            siteOrigin = siteOrigin,
            issuer = cleanIssuer,
            accountName = cleanAccount,
            encryptedSecret = encrypted,
            otpType = if (type == OtpAuthType.HOTP) {
                AuthenticatorSecretEntity.TYPE_HOTP
            } else {
                AuthenticatorSecretEntity.TYPE_TOTP
            },
            algorithm = algorithm.uriName,
            digits = digits.coerceIn(6, 8),
            periodSeconds = periodSeconds.coerceIn(10, 300),
            counter = counter.coerceAtLeast(0L),
            createdAt = now,
            updatedAt = now
        )
        dao.insertOrUpdate(entity)
        val refreshed = dao.getAllSecretsList().map { it.toDomain() }
        _accounts.value = refreshed
        entity.toDomain()
    }

    fun addManual(
        issuer: String,
        accountName: String,
        rawBase32Secret: String,
        type: OtpAuthType = OtpAuthType.TOTP,
        algorithm: OtpHashAlgorithm = OtpHashAlgorithm.SHA1,
        digits: Int = 6,
        periodSeconds: Int = 30,
        counter: Long = 0L,
        siteUrl: String? = null
    ): AuthenticatorAccount = runBlocking(Dispatchers.IO) {
        addManualSuspend(
            issuer = issuer,
            accountName = accountName,
            rawBase32Secret = rawBase32Secret,
            type = type,
            algorithm = algorithm,
            digits = digits,
            periodSeconds = periodSeconds,
            counter = counter,
            siteUrl = siteUrl
        )
    }

    fun decryptSecret(account: AuthenticatorAccount): String? {
        return try {
            CryptoUtils.decryptSiteIsolatedAes256(
                cipherText = account.encryptedSecret,
                siteScope = account.siteHost.ifBlank { normalizeSiteScope(null, account.issuer).first },
                recordId = account.id,
                keyPhrase = keyPhrase
            )
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun incrementHotpCounterSuspend(id: String): AuthenticatorAccount? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updatedRows = dao.incrementHotpCounter(id = id, updatedAt = now)
            if (updatedRows <= 0) return@withContext null
            val modified = dao.getById(id)?.toDomain()
            val refreshed = dao.getAllSecretsList().map { it.toDomain() }
            _accounts.value = refreshed
            modified
        }

    fun incrementHotpCounter(id: String): AuthenticatorAccount? = runBlocking(Dispatchers.IO) {
        incrementHotpCounterSuspend(id)
    }

    suspend fun deleteAccountSuspend(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
        _accounts.value = dao.getAllSecretsList().map { it.toDomain() }
    }

    fun deleteAccount(id: String) {
        runBlocking(Dispatchers.IO) {
            deleteAccountSuspend(id)
        }
    }

    suspend fun deleteSiteSecretsSuspend(siteUrlOrHost: String) = withContext(Dispatchers.IO) {
        val (siteHost, _) = normalizeSiteScope(siteUrlOrHost, fallbackLabel = siteUrlOrHost)
        dao.deleteBySiteHost(siteHost)
        _accounts.value = dao.getAllSecretsList().map { it.toDomain() }
    }

    fun buildShareableUri(account: AuthenticatorAccount): String? {
        val secret = decryptSecret(account) ?: return null
        return TotpAuthenticator.buildOtpAuthUri(
            type = account.type,
            issuer = account.issuer,
            accountName = account.accountName,
            base32Secret = secret,
            algorithm = account.algorithm,
            digits = account.digits,
            periodSeconds = account.periodSeconds,
            counter = account.counter
        )
    }

    private suspend fun migrateLegacyPrefsIfNeeded() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED_TO_ROOM, false)) return
        val raw = prefs.getString(KEY_ENTRIES, null)
        if (raw.isNullOrBlank()) {
            prefs.edit().putBoolean(KEY_MIGRATED_TO_ROOM, true).apply()
            return
        }
        val entities = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                    val enc = o.optString("encryptedSecret")
                    if (enc.isBlank()) continue
                    val issuer = o.optString("issuer", "Authenticator").ifBlank { "Authenticator" }
                    val accountName = o.optString("accountName", "")
                    val (siteHost, siteOrigin) = normalizeSiteScope(null, issuer)
                    val reEncrypted = runCatching {
                        val plain = CryptoUtils.decryptAes256(enc, keyPhrase = keyPhrase)
                        CryptoUtils.encryptSiteIsolatedAes256(
                            plainText = plain,
                            siteScope = siteHost,
                            recordId = id,
                            keyPhrase = keyPhrase
                        )
                    }.getOrDefault(enc)
                    val otpType = if (o.optString("type").equals("hotp", ignoreCase = true)) {
                        AuthenticatorSecretEntity.TYPE_HOTP
                    } else {
                        AuthenticatorSecretEntity.TYPE_TOTP
                    }
                    val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    add(
                        AuthenticatorSecretEntity(
                            id = id,
                            siteHost = siteHost,
                            siteOrigin = siteOrigin,
                            issuer = issuer,
                            accountName = accountName,
                            encryptedSecret = reEncrypted,
                            otpType = otpType,
                            algorithm = OtpHashAlgorithm.fromString(o.optString("algorithm", "SHA1")).uriName,
                            digits = o.optInt("digits", 6).coerceIn(6, 8),
                            periodSeconds = o.optInt("periodSeconds", 30).coerceIn(10, 300),
                            counter = o.optLong("counter", 0L).coerceAtLeast(0L),
                            createdAt = createdAt,
                            updatedAt = createdAt
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }
        prefs.edit()
            .remove(KEY_ENTRIES)
            .putBoolean(KEY_MIGRATED_TO_ROOM, true)
            .apply()
    }

    private fun AuthenticatorSecretEntity.toDomain(): AuthenticatorAccount {
        return AuthenticatorAccount(
            id = id,
            siteHost = siteHost,
            siteOrigin = siteOrigin,
            issuer = issuer,
            accountName = accountName,
            encryptedSecret = encryptedSecret,
            type = if (otpType.equals(AuthenticatorSecretEntity.TYPE_HOTP, ignoreCase = true)) {
                OtpAuthType.HOTP
            } else {
                OtpAuthType.TOTP
            },
            algorithm = OtpHashAlgorithm.fromString(algorithm),
            digits = digits.coerceIn(6, 8),
            periodSeconds = periodSeconds.coerceIn(10, 300),
            counter = counter.coerceAtLeast(0L),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        private const val PREFS_NAME = "kazium_authenticator_2fa_vault"
        private const val KEY_ENTRIES = "entries_json"
        private const val KEY_MIGRATED_TO_ROOM = "migrated_to_room_v16"

        @Volatile
        private var instance: AuthenticatorRepository? = null

        fun getInstance(context: Context): AuthenticatorRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthenticatorRepository(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Derives a strict per-site scope identifier (`siteHost` and `siteOrigin`) so secrets
         * belonging to different sites or apps are always isolated cleanly.
         */
        fun normalizeSiteScope(
            siteUrlOrHost: String?,
            fallbackLabel: String
        ): Pair<String, String> {
            val rawSite = siteUrlOrHost?.trim().orEmpty()
            if (rawSite.isNotEmpty()) {
                if (rawSite.startsWith("app:", ignoreCase = true)) {
                    val slug = rawSite.removePrefix("app:").trim().lowercase(Locale.ROOT)
                        .replace(Regex("[^a-z0-9._-]+"), "-")
                        .trim('-')
                        .ifBlank { "app" }
                    return "app:$slug" to ""
                }
                val looksLikeWebUrlOrDomain = rawSite.contains("://") ||
                    (!rawSite.contains(" ") && rawSite.contains("."))
                if (looksLikeWebUrlOrDomain) {
                    val host = PasswordVaultRepository.normalizeHost(rawSite)
                    if (!host.isNullOrBlank()) {
                        val origin = PasswordVaultRepository.normalizeOrigin(rawSite, host)
                        return host to origin
                    }
                }
            }

            val cleanFallback = fallbackLabel.trim()
            val labelIsDomain = cleanFallback.isNotEmpty() &&
                !cleanFallback.contains(" ") &&
                cleanFallback.contains(".")
            if (labelIsDomain) {
                val host = PasswordVaultRepository.normalizeHost(cleanFallback)
                if (!host.isNullOrBlank()) {
                    return host to "https://$host"
                }
            }

            val slug = cleanFallback.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "authenticator" }
            return "app:$slug" to ""
        }
    }
}

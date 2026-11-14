package com.example.security

import com.example.data.VaultCredentialDao
import com.example.data.VaultCredentialEntity
import com.example.network.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Locale
import java.util.UUID

class PasswordVaultRepository(
    private val dao: VaultCredentialDao
) {
    val allCredentials: Flow<List<VaultCredentialEntity>> = dao.getAllCredentials()

    suspend fun getAllList(): List<VaultCredentialEntity> = withContext(Dispatchers.IO) {
        dao.getAllCredentialsList()
    }

    suspend fun getMatchingCredentialsForUrl(url: String): List<VaultCredentialEntity> = withContext(Dispatchers.IO) {
        val targetHost = normalizeHost(url) ?: return@withContext emptyList()
        val all = dao.getAllCredentialsList()
        all.filter { isHostMatch(it.host, targetHost) }
            .sortedWith(
                compareByDescending<VaultCredentialEntity> { it.host == targetHost }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.updatedAt }
            )
    }

    suspend fun savePasswordCredential(
        url: String,
        siteName: String,
        username: String,
        rawPassword: String,
        notes: String = "",
        keyPhrase: String = ""
    ): VaultCredentialEntity = withContext(Dispatchers.IO) {
        val host = normalizeHost(url)
            ?: throw IllegalArgumentException("Valid HTTPS or domain host is required")
        val origin = normalizeOrigin(url, host)
        val cleanUser = username.trim()
        val cleanSite = siteName.trim().ifBlank { host }
        val encrypted = CryptoUtils.encryptAes256(rawPassword, keyPhrase = keyPhrase)
        val now = System.currentTimeMillis()

        val existing = dao.findByHostAndUsername(host, cleanUser)
        val entity = if (existing != null) {
            val nextType = if (existing.hasPasskey) {
                VaultCredentialEntity.TYPE_PASSKEY_AND_PASSWORD
            } else {
                VaultCredentialEntity.TYPE_PASSWORD
            }
            existing.copy(
                origin = origin,
                siteName = cleanSite.ifBlank { existing.siteName },
                encryptedPassword = encrypted,
                credentialType = nextType,
                notes = notes.ifBlank { existing.notes },
                updatedAt = now
            )
        } else {
            VaultCredentialEntity(
                id = UUID.randomUUID().toString(),
                host = host,
                origin = origin,
                siteName = cleanSite,
                username = cleanUser,
                encryptedPassword = encrypted,
                credentialType = VaultCredentialEntity.TYPE_PASSWORD,
                passkeyRpId = "",
                notes = notes,
                createdAt = now,
                updatedAt = now,
                lastUsedAt = 0L
            )
        }
        dao.insertOrUpdate(entity)
        entity
    }

    suspend fun recordPasskeyCredential(
        url: String,
        rpId: String,
        username: String,
        siteName: String = "",
        keyPhrase: String = ""
    ): VaultCredentialEntity = withContext(Dispatchers.IO) {
        val cleanRpId = normalizeHost(rpId) ?: normalizeHost(url)
            ?: throw IllegalArgumentException("Valid Relying Party ID or URL required")
        val host = normalizeHost(url) ?: cleanRpId
        val origin = normalizeOrigin(url, host)
        val cleanUser = username.trim().ifBlank { "Passkey ($cleanRpId)" }
        val cleanSite = siteName.trim().ifBlank { cleanRpId }
        val now = System.currentTimeMillis()

        val existing = dao.findByHostAndUsername(host, cleanUser)
            ?: dao.findByHostAndUsername(cleanRpId, cleanUser)
            ?: getMatchingCredentialsForUrl(url).let { matches ->
                matches.firstOrNull { it.username.equals(cleanUser, ignoreCase = true) }
                    ?: if (username.isBlank()) matches.singleOrNull { it.hasPassword } else null
            }
        val entity = if (existing != null) {
            val nextType = if (existing.hasPassword) {
                VaultCredentialEntity.TYPE_PASSKEY_AND_PASSWORD
            } else {
                VaultCredentialEntity.TYPE_PASSKEY
            }
            existing.copy(
                siteName = cleanSite.ifBlank { existing.siteName },
                credentialType = nextType,
                passkeyRpId = cleanRpId,
                updatedAt = now,
                lastUsedAt = now
            )
        } else {
            VaultCredentialEntity(
                id = UUID.randomUUID().toString(),
                host = host,
                origin = origin,
                siteName = cleanSite,
                username = cleanUser,
                encryptedPassword = "",
                credentialType = VaultCredentialEntity.TYPE_PASSKEY,
                passkeyRpId = cleanRpId,
                notes = if (keyPhrase.isNotEmpty()) "" else "",
                createdAt = now,
                updatedAt = now,
                lastUsedAt = now
            )
        }
        dao.insertOrUpdate(entity)
        entity
    }

    suspend fun updateCredential(
        id: String,
        siteName: String,
        originUrl: String,
        username: String,
        newRawPassword: String?,
        notes: String,
        keyPhrase: String = ""
    ): VaultCredentialEntity? = withContext(Dispatchers.IO) {
        val existing = dao.getById(id) ?: return@withContext null
        val host = normalizeHost(originUrl) ?: existing.host
        val origin = normalizeOrigin(originUrl, host)
        val encrypted = when {
            newRawPassword == null -> existing.encryptedPassword
            newRawPassword.isBlank() -> existing.encryptedPassword
            else -> CryptoUtils.encryptAes256(newRawPassword, keyPhrase = keyPhrase)
        }
        val nextType = when {
            existing.hasPasskey && encrypted.isNotBlank() -> VaultCredentialEntity.TYPE_PASSKEY_AND_PASSWORD
            existing.hasPasskey -> VaultCredentialEntity.TYPE_PASSKEY
            else -> VaultCredentialEntity.TYPE_PASSWORD
        }
        val updated = existing.copy(
            host = host,
            origin = origin,
            siteName = siteName.trim().ifBlank { host },
            username = username.trim(),
            encryptedPassword = encrypted,
            credentialType = nextType,
            notes = notes.trim(),
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrUpdate(updated)
        updated
    }

    suspend fun markUsed(id: String) = withContext(Dispatchers.IO) {
        dao.updateLastUsed(id, System.currentTimeMillis())
    }

    suspend fun deleteCredential(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    fun decryptPassword(entity: VaultCredentialEntity, keyPhrase: String = ""): String? {
        if (entity.encryptedPassword.isBlank()) return null
        return try {
            CryptoUtils.decryptAes256(entity.encryptedPassword, keyPhrase = keyPhrase)
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private val GENERIC_PUBLIC_SUFFIXES = setOf(
            "com", "org", "net", "edu", "gov", "io", "co", "app", "dev", "xyz",
            "co.uk", "org.uk", "com.au", "co.jp", "com.br", "github.io"
        )

        fun normalizeHost(urlOrHost: String?): String? {
            if (urlOrHost.isNullOrBlank()) return null
            val trimmed = urlOrHost.trim()
            val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val host = try {
                URI(candidate).host
            } catch (_: Throwable) {
                null
            } ?: return null
            val clean = host.lowercase(Locale.ROOT).removePrefix("www.").trimEnd('.')
            return clean.takeIf { it.isNotBlank() }
        }

        fun normalizeOrigin(urlOrHost: String, fallbackHost: String): String {
            val trimmed = urlOrHost.trim()
            val candidate = if (trimmed.contains("://")) trimmed else "https://$fallbackHost"
            return try {
                val uri = URI(candidate)
                val scheme = (uri.scheme ?: "https").lowercase(Locale.ROOT)
                val safeScheme = if (scheme == "http") "http" else "https"
                val host = (uri.host ?: fallbackHost).lowercase(Locale.ROOT)
                val port = if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
                "$safeScheme://$host$port"
            } catch (_: Throwable) {
                "https://$fallbackHost"
            }
        }

        fun isHostMatch(storedHostRaw: String, targetHostRaw: String): Boolean {
            val stored = storedHostRaw.lowercase(Locale.ROOT).removePrefix("www.").trimEnd('.')
            val target = targetHostRaw.lowercase(Locale.ROOT).removePrefix("www.").trimEnd('.')
            if (stored.isBlank() || target.isBlank()) return false
            if (stored == target) return true

            // Allow legitimate subdomain sharing (e.g. account.github.com <-> github.com)
            // only if the shared base domain has at least two labels and isn't a bare public suffix.
            if (target.endsWith(".$stored") && stored.contains(".") && !GENERIC_PUBLIC_SUFFIXES.contains(stored)) {
                return true
            }
            if (stored.endsWith(".$target") && target.contains(".") && !GENERIC_PUBLIC_SUFFIXES.contains(target)) {
                return true
            }

            // Same registrable parent (e.g. login.example.com <-> auth.example.com)
            val storedParent = extractSimpleRegistrableParent(stored)
            val targetParent = extractSimpleRegistrableParent(target)
            if (storedParent != null && storedParent == targetParent) {
                return true
            }
            return false
        }

        private fun extractSimpleRegistrableParent(host: String): String? {
            val parts = host.split('.').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            val lastTwo = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
            if (GENERIC_PUBLIC_SUFFIXES.contains(lastTwo)) {
                return if (parts.size >= 3) {
                    "${parts[parts.size - 3]}.$lastTwo"
                } else {
                    null
                }
            }
            return lastTwo
        }
    }
}

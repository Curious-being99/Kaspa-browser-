package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vault_credentials")
data class VaultCredentialEntity(
    @PrimaryKey val id: String,
    val host: String,
    val origin: String,
    val siteName: String,
    val username: String,
    val encryptedPassword: String,
    @ColumnInfo(defaultValue = "PASSWORD") val credentialType: String = TYPE_PASSWORD,
    @ColumnInfo(defaultValue = "") val passkeyRpId: String = "",
    @ColumnInfo(defaultValue = "") val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val lastUsedAt: Long = 0L
) {
    companion object {
        const val TYPE_PASSWORD = "PASSWORD"
        const val TYPE_PASSKEY = "PASSKEY"
        const val TYPE_PASSKEY_AND_PASSWORD = "PASSKEY_AND_PASSWORD"
    }

    val hasPassword: Boolean
        get() = encryptedPassword.isNotBlank()

    val hasPasskey: Boolean
        get() = credentialType == TYPE_PASSKEY || credentialType == TYPE_PASSKEY_AND_PASSWORD || passkeyRpId.isNotBlank()
}

@Dao
interface VaultCredentialDao {
    @Query("SELECT * FROM vault_credentials ORDER BY lastUsedAt DESC, updatedAt DESC")
    fun getAllCredentials(): Flow<List<VaultCredentialEntity>>

    @Query("SELECT * FROM vault_credentials ORDER BY lastUsedAt DESC, updatedAt DESC")
    suspend fun getAllCredentialsList(): List<VaultCredentialEntity>

    @Query("SELECT * FROM vault_credentials WHERE host = :host ORDER BY lastUsedAt DESC, updatedAt DESC")
    suspend fun getCredentialsByHost(host: String): List<VaultCredentialEntity>

    @Query("SELECT * FROM vault_credentials WHERE host = :host AND LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun findByHostAndUsername(host: String, username: String): VaultCredentialEntity?

    @Query("SELECT * FROM vault_credentials WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VaultCredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(credential: VaultCredentialEntity)

    @Query("UPDATE vault_credentials SET lastUsedAt = :lastUsedAt WHERE id = :id")
    suspend fun updateLastUsed(id: String, lastUsedAt: Long)

    @Query("DELETE FROM vault_credentials WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM vault_credentials")
    suspend fun clearAll()
}

/**
 * Encrypted Room table isolating TOTP (Time-based) and HOTP (Counter-based) 2FA secrets
 * per normalized [siteHost]. [encryptedSecret] is always encrypted at rest via AES-256-GCM
 * with site-scope + record-ID AAD binding (never stored in plaintext).
 */
@Entity(
    tableName = "authenticator_secrets",
    indices = [
        Index(value = ["siteHost"]),
        Index(value = ["otpType"]),
        Index(value = ["siteHost", "issuer", "accountName"])
    ]
)
data class AuthenticatorSecretEntity(
    @PrimaryKey val id: String,
    val siteHost: String,
    val siteOrigin: String,
    val issuer: String,
    val accountName: String,
    val encryptedSecret: String,
    @ColumnInfo(defaultValue = "TOTP") val otpType: String = TYPE_TOTP,
    @ColumnInfo(defaultValue = "SHA1") val algorithm: String = "SHA1",
    @ColumnInfo(defaultValue = "6") val digits: Int = 6,
    @ColumnInfo(defaultValue = "30") val periodSeconds: Int = 30,
    @ColumnInfo(defaultValue = "0") val counter: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_TOTP = "TOTP"
        const val TYPE_HOTP = "HOTP"
    }
}

@Dao
interface AuthenticatorSecretDao {
    @Query("SELECT * FROM authenticator_secrets ORDER BY updatedAt DESC, createdAt DESC")
    fun getAllSecrets(): Flow<List<AuthenticatorSecretEntity>>

    @Query("SELECT * FROM authenticator_secrets ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun getAllSecretsList(): List<AuthenticatorSecretEntity>

    @Query("SELECT * FROM authenticator_secrets WHERE siteHost = :siteHost ORDER BY updatedAt DESC, createdAt DESC")
    fun getSecretsForSiteFlow(siteHost: String): Flow<List<AuthenticatorSecretEntity>>

    @Query("SELECT * FROM authenticator_secrets WHERE siteHost = :siteHost ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun getSecretsForSite(siteHost: String): List<AuthenticatorSecretEntity>

    @Query("SELECT * FROM authenticator_secrets WHERE otpType = :otpType ORDER BY updatedAt DESC, createdAt DESC")
    fun getSecretsByTypeFlow(otpType: String): Flow<List<AuthenticatorSecretEntity>>

    @Query("SELECT * FROM authenticator_secrets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AuthenticatorSecretEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(secret: AuthenticatorSecretEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(secrets: List<AuthenticatorSecretEntity>)

    @Query("UPDATE authenticator_secrets SET counter = counter + 1, updatedAt = :updatedAt WHERE id = :id AND otpType = 'HOTP'")
    suspend fun incrementHotpCounter(id: String, updatedAt: Long): Int

    @Query("DELETE FROM authenticator_secrets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM authenticator_secrets WHERE siteHost = :siteHost")
    suspend fun deleteBySiteHost(siteHost: String)

    @Query("DELETE FROM authenticator_secrets")
    suspend fun clearAll()
}

package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
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

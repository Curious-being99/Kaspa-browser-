package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "webauthn_credentials",
    indices = [Index(value = ["rpId", "userHandle"]), Index(value = ["credentialId"], unique = true)]
)
data class WebAuthnCredentialEntity(
    @PrimaryKey val id: String,
    val rpId: String,
    val origin: String,
    val userHandle: String,
    val userName: String,
    val displayName: String,
    val credentialId: String,
    val publicKey: String,
    val keyAlias: String,
    val signCount: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L
)

@Dao
interface WebAuthnCredentialDao {
    @Query("SELECT * FROM webauthn_credentials WHERE rpId = :rpId ORDER BY lastUsedAt DESC")
    suspend fun forRp(rpId: String): List<WebAuthnCredentialEntity>

    @Query("SELECT * FROM webauthn_credentials WHERE rpId = :rpId AND credentialId = :credentialId LIMIT 1")
    suspend fun find(rpId: String, credentialId: String): WebAuthnCredentialEntity?

    @Query("SELECT * FROM webauthn_credentials WHERE rpId = :rpId AND userHandle = :userHandle LIMIT 1")
    suspend fun findForUser(rpId: String, userHandle: String): WebAuthnCredentialEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(credential: WebAuthnCredentialEntity)

    @Query("UPDATE webauthn_credentials SET signCount = :signCount, lastUsedAt = :lastUsedAt WHERE id = :id")
    suspend fun updateUse(id: String, signCount: Long, lastUsedAt: Long)

    @Query("DELETE FROM webauthn_credentials WHERE id = :id")
    suspend fun delete(id: String)
}

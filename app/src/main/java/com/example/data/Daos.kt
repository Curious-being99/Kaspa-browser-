package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Query("SELECT * FROM pinned_contents ORDER BY createdAt DESC")
    fun getAllContents(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM pinned_contents WHERE cid = :cid LIMIT 1")
    suspend fun getContentByCid(cid: String): ContentEntity?

    @Query("SELECT * FROM pinned_contents WHERE cid = :query OR title LIKE '%' || :query || '%' OR protocolPrefix LIKE '%' || :query || '%' LIMIT 1")
    suspend fun searchContent(query: String): ContentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: ContentEntity)

    @Update
    suspend fun updateContent(content: ContentEntity)

    @Delete
    suspend fun deleteContent(content: ContentEntity)

    @Query("SELECT COUNT(*) FROM pinned_contents")
    fun getContentCount(): Flow<Int>

    @Query("DELETE FROM pinned_contents WHERE cid IN ('bafybeiedbgbehecgcjgehddigfibcifhebfeh', 'bafybeiabecahcdjidbadaddfahcffabeijbjeh', 'bafybeidaabbbecdaffeiccddjdebedbidebgbj') OR protocolPrefix IN ('mesh://manifesto', 'mesh://freedom.wiki', 'mesh://news', 'mesh://wiki')")
    suspend fun deleteLegacyMockContents()

    @Query("DELETE FROM pinned_contents")
    suspend fun clearAllContents()
}

@Dao
interface TrafficAuditDao {
    @Query("SELECT * FROM traffic_audits ORDER BY timestamp DESC LIMIT 50")
    fun getRecentAudits(): Flow<List<TrafficAuditEntity>>

    @Insert
    suspend fun insertAudit(audit: TrafficAuditEntity)

    @Query("DELETE FROM traffic_audits")
    suspend fun clearAudits()

    @Query("DELETE FROM traffic_audits WHERE url LIKE '%manifesto%' OR url LIKE '%freedom.wiki%' OR url LIKE '%mesh://news%' OR url LIKE '%mock%' OR url LIKE '%sample%' OR url = ''")
    suspend fun deleteLegacyMockAudits()
}

@Dao
interface PeerDao {
    @Query("SELECT * FROM peer_nodes ORDER BY isOnline DESC, latencyMs ASC")
    fun getAllPeers(): Flow<List<PeerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeers(peers: List<PeerEntity>)

    @Query("UPDATE peer_nodes SET isOnline = :isOnline WHERE peerId = :peerId")
    suspend fun updatePeerStatus(peerId: String, isOnline: Boolean)

    @Query("SELECT COUNT(*) FROM peer_nodes WHERE isOnline = 1")
    fun getOnlinePeerCount(): Flow<Int>

    @Query("SELECT * FROM peer_nodes WHERE isOnline = 1")
    suspend fun getOnlinePeers(): List<PeerEntity>

    @Query("SELECT * FROM peer_nodes")
    suspend fun getAllPeersList(): List<PeerEntity>

    @Query("UPDATE peer_nodes SET latencyMs = :latencyMs, isOnline = :isOnline WHERE peerId = :peerId")
    suspend fun updatePeerLatency(peerId: String, latencyMs: Long, isOnline: Boolean)

    @Query("DELETE FROM peer_nodes WHERE isBootstrap = 0")
    suspend fun clearDiscoveredPeers()

    @Query("DELETE FROM peer_nodes WHERE name LIKE '%Fake%' OR name LIKE '%This Phone%'")
    suspend fun deleteLegacyMockPeers()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM decentralized_accounts ORDER BY createdAt DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM decentralized_accounts")
    suspend fun getAllAccountsList(): List<AccountEntity>

    @Query("SELECT * FROM decentralized_accounts WHERE isActive = 1 LIMIT 1")
    fun getActiveAccount(): Flow<AccountEntity?>

    @Query("SELECT * FROM decentralized_accounts WHERE did = :did LIMIT 1")
    suspend fun getAccountByDid(did: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Query("UPDATE decentralized_accounts SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE decentralized_accounts SET isActive = 1 WHERE did = :did")
    suspend fun setActive(did: String)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM decentralized_accounts WHERE handle LIKE '@peer_%' OR handle LIKE '%peer_node%'")
    suspend fun deleteLegacyMockAccounts()
}

@Dao
interface DomainDao {
    @Query("SELECT * FROM kab_domains ORDER BY registeredAt DESC")
    fun getAllDomains(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM kab_domains WHERE domain = :domain LIMIT 1")
    suspend fun getDomainByName(domain: String): DomainEntity?

    @Query("SELECT * FROM kab_domains WHERE ownerAddress = :address ORDER BY registeredAt DESC")
    fun getDomainsByOwner(address: String): Flow<List<DomainEntity>>

    @Query("SELECT * FROM kab_domains WHERE ownerAddress = :address ORDER BY registeredAt DESC")
    suspend fun getDomainsByOwnerList(address: String): List<DomainEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDomain(domain: DomainEntity)

    @Update
    suspend fun updateDomain(domain: DomainEntity)

    @Delete
    suspend fun deleteDomain(domain: DomainEntity)

    @Query("SELECT COUNT(*) FROM kab_domains WHERE domain = :domain")
    suspend fun countDomain(domain: String): Int

    @Query("DELETE FROM kab_domains WHERE txId LIKE 'local%' OR txId LIKE '%claim%' OR length(txId) < 32")
    suspend fun deleteLegacyMockDomains()
}


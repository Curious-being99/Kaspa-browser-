package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ContentEntity::class, TrafficAuditEntity::class, PeerEntity::class, AccountEntity::class, HistoryEntity::class, BookmarkEntity::class, BrowserTabEntity::class, BrowserSessionEntity::class, DomainEntity::class, NewsArticleEntity::class, TransactionEntity::class, VaultCredentialEntity::class],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun trafficAuditDao(): TrafficAuditDao
    abstract fun peerDao(): PeerDao
    abstract fun accountDao(): AccountDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun browserTabDao(): BrowserTabDao
    abstract fun browserSessionDao(): BrowserSessionDao
    abstract fun domainDao(): DomainDao
    abstract fun newsArticleDao(): NewsArticleDao
    abstract fun transactionDao(): TransactionDao
    abstract fun vaultCredentialDao(): VaultCredentialDao

    companion object {
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transaction_history (
                        txId TEXT NOT NULL,
                        blockTime INTEGER NOT NULL,
                        amountKas REAL NOT NULL,
                        type TEXT NOT NULL,
                        isAccepted INTEGER NOT NULL,
                        feeKas REAL NOT NULL,
                        counterpartyAddress TEXT NOT NULL,
                        PRIMARY KEY(txId)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transaction_history ADD COLUMN network TEXT NOT NULL DEFAULT 'UNKNOWN'"
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vault_credentials (
                        id TEXT NOT NULL,
                        host TEXT NOT NULL,
                        origin TEXT NOT NULL,
                        siteName TEXT NOT NULL,
                        username TEXT NOT NULL,
                        encryptedPassword TEXT NOT NULL,
                        credentialType TEXT NOT NULL DEFAULT 'PASSWORD',
                        passkeyRpId TEXT NOT NULL DEFAULT '',
                        notes TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastUsedAt INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }


        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE decentralized_accounts_new (
                        did TEXT NOT NULL, handle TEXT NOT NULL, kaspaAddress TEXT NOT NULL,
                        peerId TEXT NOT NULL, publicKeyHex TEXT NOT NULL, seedPhrase TEXT NOT NULL,
                        accountType TEXT NOT NULL DEFAULT 'DECENTRALIZED_NATIVE',
                        zkProofJson TEXT, createdAt INTEGER NOT NULL, isActive INTEGER NOT NULL,
                        PRIMARY KEY(did)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO decentralized_accounts_new
                    (did, handle, kaspaAddress, peerId, publicKeyHex, seedPhrase,
                     accountType, zkProofJson, createdAt, isActive)
                    SELECT did, handle, kaspaAddress, peerId, publicKeyHex, seedPhrase,
                           accountType, zkProofJson, createdAt, isActive
                    FROM decentralized_accounts
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE decentralized_accounts")
                db.execSQL("ALTER TABLE decentralized_accounts_new RENAME TO decentralized_accounts")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "decentralnet_db"
                )
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

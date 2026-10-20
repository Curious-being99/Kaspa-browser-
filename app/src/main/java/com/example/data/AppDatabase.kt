package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ContentEntity::class, TrafficAuditEntity::class, PeerEntity::class, AccountEntity::class, HistoryEntity::class, BookmarkEntity::class, BrowserTabEntity::class, BrowserSessionEntity::class, DomainEntity::class, NewsArticleEntity::class, TransactionEntity::class],
    version = 13,
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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "decentralnet_db"
                )
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

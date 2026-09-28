package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "browser_tabs")
data class BrowserTabEntity(
    @PrimaryKey val id: String,
    val url: String = "",
    val title: String = "New Tab",
    @ColumnInfo(defaultValue = "0") val tabOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val scrollX: Int = 0,
    @ColumnInfo(defaultValue = "0") val scrollY: Int = 0,
    @ColumnInfo(defaultValue = "[]") val historyJson: String = "[]",
    val webViewState: ByteArray? = null,
    val lastAccessed: Long = System.currentTimeMillis(),
    val isSuspended: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isExternal: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BrowserTabEntity
        if (id != other.id) return false
        if (url != other.url) return false
        if (title != other.title) return false
        if (tabOrder != other.tabOrder) return false
        if (scrollX != other.scrollX) return false
        if (scrollY != other.scrollY) return false
        if (historyJson != other.historyJson) return false
        if (lastAccessed != other.lastAccessed) return false
        if (isSuspended != other.isSuspended) return false
        if (isExternal != other.isExternal) return false
        if (webViewState != null) {
            if (other.webViewState == null) return false
            if (!webViewState.contentEquals(other.webViewState)) return false
        } else if (other.webViewState != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + tabOrder
        result = 31 * result + scrollX
        result = 31 * result + scrollY
        result = 31 * result + historyJson.hashCode()
        result = 31 * result + (webViewState?.contentHashCode() ?: 0)
        result = 31 * result + lastAccessed.hashCode()
        result = 31 * result + isSuspended.hashCode()
        result = 31 * result + isExternal.hashCode()
        return result
    }
}

@Entity(tableName = "browser_session")
data class BrowserSessionEntity(
    @PrimaryKey val id: String = "singleton",
    val activeTabId: String? = null,
    val onboardingCompleted: Boolean = false,
    val lastSavedTimestamp: Long = System.currentTimeMillis(),
    val sessionStateJson: String = "{}"
)

@Dao
interface BrowserSessionDao {
    @Query("SELECT * FROM browser_session WHERE id = 'singleton' LIMIT 1")
    fun getSessionFlow(): Flow<BrowserSessionEntity?>

    @Query("SELECT * FROM browser_session WHERE id = 'singleton' LIMIT 1")
    suspend fun getSession(): BrowserSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: BrowserSessionEntity)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 500")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun isBookmarked(url: String): Boolean
}

@Dao
interface BrowserTabDao {
    @Query("SELECT * FROM browser_tabs ORDER BY tabOrder ASC, lastAccessed DESC")
    fun getAllTabs(): Flow<List<BrowserTabEntity>>

    @Query("SELECT * FROM browser_tabs ORDER BY tabOrder ASC, lastAccessed DESC")
    suspend fun getAllTabsList(): List<BrowserTabEntity>

    @Query("SELECT * FROM browser_tabs WHERE id = :id LIMIT 1")
    suspend fun getTabById(id: String): BrowserTabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tab: BrowserTabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tabs: List<BrowserTabEntity>)

    @Delete
    suspend fun delete(tab: BrowserTabEntity)

    @Query("DELETE FROM browser_tabs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM browser_tabs")
    suspend fun clearAll()
}

@Entity(tableName = "news_articles")
data class NewsArticleEntity(
    @PrimaryKey val deduplicationKey: String,
    val title: String,
    val desc: String,
    val url: String,
    val category: String,
    val timestamp: String,
    val author: String = "",
    val videoId: String? = null,
    val duration: String? = null,
    val epochMillis: Long = System.currentTimeMillis()
)

@Dao
interface NewsArticleDao {
    @Query("SELECT * FROM news_articles ORDER BY epochMillis DESC LIMIT 300")
    fun getAllNews(): Flow<List<NewsArticleEntity>>

    @Query("SELECT * FROM news_articles ORDER BY epochMillis DESC LIMIT 300")
    suspend fun getAllNewsList(): List<NewsArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<NewsArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: NewsArticleEntity)

    @Query("SELECT COUNT(*) FROM news_articles")
    suspend fun getCount(): Int

    @Query("DELETE FROM news_articles")
    suspend fun clearAll()
}

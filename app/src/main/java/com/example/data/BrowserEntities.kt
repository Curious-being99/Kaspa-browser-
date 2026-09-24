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
    val url: String,
    val title: String,
    val lastAccessed: Long = System.currentTimeMillis(),
    val isSuspended: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isExternal: Boolean = false
)

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
    @Query("SELECT * FROM browser_tabs ORDER BY lastAccessed DESC")
    fun getAllTabs(): Flow<List<BrowserTabEntity>>

    @Query("SELECT * FROM browser_tabs ORDER BY lastAccessed DESC")
    suspend fun getAllTabsList(): List<BrowserTabEntity>

    @Query("SELECT * FROM browser_tabs WHERE id = :id LIMIT 1")
    suspend fun getTabById(id: String): BrowserTabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tab: BrowserTabEntity)

    @Delete
    suspend fun delete(tab: BrowserTabEntity)

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

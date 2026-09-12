package com.example.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserDao {
    // --- Tabs ---
    @Query("SELECT * FROM tabs ORDER BY position ASC")
    fun getAllTabs(): Flow<List<TabEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: TabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<TabEntity>)

    @Query("DELETE FROM tabs WHERE id = :tabId")
    suspend fun deleteTab(tabId: String)

    @Query("DELETE FROM tabs")
    suspend fun clearAllTabs()

    // --- History ---
    @Query("SELECT * FROM history WHERE isPrivate = 0 ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE isPrivate = 0 AND timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getHistorySince(sinceTimestamp: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE isPrivate = 0 AND (title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestVisitForUrl(url: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM history WHERE timestamp >= :sinceTimestamp")
    suspend fun deleteHistorySince(sinceTimestamp: Long)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // --- Bookmarks ---
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun getBookmarkByUrl(url: String): BookmarkEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun isUrlBookmarkedFlow(url: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun isUrlBookmarked(url: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(item: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    // --- Downloads ---
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadEntity)

    @Update
    suspend fun updateDownload(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    // --- Research Sessions ---
    @Query("SELECT * FROM research_sessions ORDER BY updatedAt DESC")
    fun getAllResearchSessions(): Flow<List<ResearchSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResearchSession(session: ResearchSessionEntity)

    @Query("DELETE FROM research_sessions WHERE id = :id")
    suspend fun deleteResearchSession(id: String)

    // --- Site Permissions ---
    @Query("SELECT * FROM site_permissions WHERE origin = :origin")
    suspend fun getPermissionsForOrigin(origin: String): List<SitePermissionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSitePermission(permission: SitePermissionEntity)

    @Query("DELETE FROM site_permissions WHERE origin = :origin")
    suspend fun clearPermissionsForOrigin(origin: String)

    @Query("DELETE FROM site_permissions")
    suspend fun clearAllPermissions()

    // --- Blocked Trackers ---
    @Query("SELECT * FROM blocked_trackers")
    fun getAllBlockedTrackers(): Flow<List<BlockedTrackerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedTrackers(trackers: List<BlockedTrackerEntity>)

    @Query("SELECT COUNT(*) FROM blocked_trackers WHERE isBlocked = 1")
    suspend fun getBlockedTrackerCount(): Int
}

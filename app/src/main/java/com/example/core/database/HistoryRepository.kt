package com.example.core.database

import com.example.core.model.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository interface for managing user browsing history.
 * Enforces strictly on-device local storage via Room, guaranteeing user privacy.
 */
interface HistoryRepository {
    /**
     * Records a web page visit in local Room database.
     * If [isPrivate] is true, the visit is strictly ignored and NEVER stored on device.
     */
    suspend fun recordVisit(url: String, title: String, isPrivate: Boolean)

    /**
     * Retrieves all on-device browsing history ordered by most recent.
     */
    fun getAllHistory(): Flow<List<HistoryItem>>

    /**
     * Retrieves browsing history recorded since [sinceTimestamp].
     */
    fun getHistorySince(sinceTimestamp: Long): Flow<List<HistoryItem>>

    /**
     * Searches browsing history matching query against page title or URL.
     */
    fun searchHistory(query: String): Flow<List<HistoryItem>>

    /**
     * Deletes a specific browsing history record by [id].
     */
    suspend fun deleteHistoryItem(id: Long)

    /**
     * Clears history recorded since [sinceTimestamp] or all history if [sinceTimestamp] is 0.
     */
    suspend fun clearHistory(sinceTimestamp: Long = 0L)
}

/**
 * Production implementation of [HistoryRepository] backed by Room SQLite database.
 */
class RoomHistoryRepository(
    private val browserDao: BrowserDao
) : HistoryRepository {

    override suspend fun recordVisit(url: String, title: String, isPrivate: Boolean) = withContext(Dispatchers.IO) {
        // Privacy invariant: Never write private browsing visits to on-device storage
        if (isPrivate || url.isBlank() || url == "about:blank") {
            return@withContext
        }

        val cleanTitle = title.ifBlank { url }
        val now = System.currentTimeMillis()

        // Deduplication window: If the same URL was recorded within the last 15 seconds, avoid spamming duplicate rows
        val latestVisit = browserDao.getLatestVisitForUrl(url)
        if (latestVisit != null && (now - latestVisit.timestamp) < 15_000L) {
            return@withContext
        }

        browserDao.insertHistory(
            HistoryEntity(
                url = url,
                title = cleanTitle,
                timestamp = now,
                isPrivate = false
            )
        )
    }

    override fun getAllHistory(): Flow<List<HistoryItem>> {
        return browserDao.getAllHistory().map { list ->
            list.map {
                HistoryItem(
                    id = it.id,
                    url = it.url,
                    title = it.title,
                    timestamp = it.timestamp,
                    isPrivate = it.isPrivate
                )
            }
        }
    }

    override fun getHistorySince(sinceTimestamp: Long): Flow<List<HistoryItem>> {
        return browserDao.getHistorySince(sinceTimestamp).map { list ->
            list.map {
                HistoryItem(
                    id = it.id,
                    url = it.url,
                    title = it.title,
                    timestamp = it.timestamp,
                    isPrivate = it.isPrivate
                )
            }
        }
    }

    override fun searchHistory(query: String): Flow<List<HistoryItem>> {
        return browserDao.searchHistory(query).map { list ->
            list.map {
                HistoryItem(
                    id = it.id,
                    url = it.url,
                    title = it.title,
                    timestamp = it.timestamp,
                    isPrivate = it.isPrivate
                )
            }
        }
    }

    override suspend fun deleteHistoryItem(id: Long) = withContext(Dispatchers.IO) {
        browserDao.deleteHistory(id)
    }

    override suspend fun clearHistory(sinceTimestamp: Long) = withContext(Dispatchers.IO) {
        if (sinceTimestamp <= 0L) {
            browserDao.clearHistory()
        } else {
            browserDao.deleteHistorySince(sinceTimestamp)
        }
    }
}

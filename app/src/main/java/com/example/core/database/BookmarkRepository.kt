package com.example.core.database

import com.example.core.model.BookmarkItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository interface for managing user bookmarks.
 * Enforces strictly local storage via Room SQLite, guaranteeing privacy.
 */
interface BookmarkRepository {
    /**
     * Observes all saved bookmarks ordered by creation date descending.
     */
    fun getAllBookmarks(): Flow<List<BookmarkItem>>

    /**
     * Checks if a given URL is bookmarked in real-time.
     */
    fun isBookmarked(url: String): Flow<Boolean>

    /**
     * Toggles bookmark state for the given URL.
     * @return true if page was bookmarked, false if bookmark was removed.
     */
    suspend fun toggleBookmark(url: String, title: String, folder: String = "Bookmarks"): Boolean

    /**
     * Adds a bookmark for the given URL.
     */
    suspend fun addBookmark(url: String, title: String, folder: String = "Bookmarks")

    /**
     * Deletes a bookmark for the given URL.
     */
    suspend fun deleteBookmarkByUrl(url: String)

    /**
     * Deletes a bookmark by its primary key ID.
     */
    suspend fun deleteBookmark(id: Long)
}

/**
 * Production implementation of [BookmarkRepository] backed by Room SQLite database.
 */
class RoomBookmarkRepository(
    private val browserDao: BrowserDao
) : BookmarkRepository {

    override fun getAllBookmarks(): Flow<List<BookmarkItem>> {
        return browserDao.getAllBookmarks().map { list ->
            list.map {
                BookmarkItem(
                    id = it.id,
                    url = it.url,
                    title = it.title,
                    folder = it.folder,
                    createdAt = it.createdAt,
                    faviconUrl = it.faviconUrl
                )
            }
        }
    }

    override fun isBookmarked(url: String): Flow<Boolean> {
        if (url.isBlank() || url == "about:blank") {
            return kotlinx.coroutines.flow.flowOf(false)
        }
        return browserDao.isUrlBookmarkedFlow(url)
    }

    override suspend fun toggleBookmark(url: String, title: String, folder: String): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank() || url == "about:blank") return@withContext false

        val existing = browserDao.getBookmarkByUrl(url)
        if (existing != null) {
            browserDao.deleteBookmark(existing.id)
            false
        } else {
            val cleanTitle = title.ifBlank { url }
            browserDao.insertBookmark(
                BookmarkEntity(
                    url = url,
                    title = cleanTitle,
                    folder = folder,
                    createdAt = System.currentTimeMillis()
                )
            )
            true
        }
    }

    override suspend fun addBookmark(url: String, title: String, folder: String) = withContext(Dispatchers.IO) {
        if (url.isBlank() || url == "about:blank") return@withContext
        val cleanTitle = title.ifBlank { url }
        browserDao.insertBookmark(
            BookmarkEntity(
                url = url,
                title = cleanTitle,
                folder = folder,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteBookmarkByUrl(url: String) = withContext(Dispatchers.IO) {
        browserDao.deleteBookmarkByUrl(url)
    }

    override suspend fun deleteBookmark(id: Long) = withContext(Dispatchers.IO) {
        browserDao.deleteBookmark(id)
    }
}

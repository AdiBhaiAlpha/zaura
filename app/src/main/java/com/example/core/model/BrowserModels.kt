package com.example.core.model

data class BrowserTab(
    val id: String,
    val url: String = "about:blank",
    val title: String = "New Tab",
    val faviconUrl: String? = null,
    val groupName: String = "General",
    val isPrivate: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = true,
    val lastAccessedAt: Long = System.currentTimeMillis()
)

data class HistoryItem(
    val id: Long = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false
)

data class BookmarkItem(
    val id: Long = 0,
    val url: String,
    val title: String,
    val folder: String = "Bookmarks",
    val createdAt: Long = System.currentTimeMillis(),
    val faviconUrl: String? = null
)

data class DownloadItem(
    val id: Long = 0,
    val fileName: String,
    val url: String,
    val mimeType: String = "",
    val fileSize: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String = ""
)

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    PAUSED,
    FAILED,
    CANCELLED
}

data class ResearchSession(
    val id: String,
    val title: String,
    val query: String,
    val aiAnswer: String,
    val sources: List<SourceCitation> = emptyList(),
    val followups: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class SourceCitation(
    val id: Int,
    val title: String,
    val url: String,
    val domain: String,
    val snippet: String = ""
)

data class SearchResult(
    val id: String,
    val title: String,
    val url: String,
    val displayUrl: String,
    val snippet: String,
    val source: String,
    val publishedAt: String? = null,
    val thumbnail: String? = null,
    val domain: String = "",
    val type: SearchType = SearchType.WEB,
    val imageUrl: String? = null,
    val videoDuration: String? = null,
    val videoPlatform: String? = null
)

enum class SearchType {
    WEB,
    NEWS,
    IMAGES,
    VIDEOS
}

enum class QueryIntentType {
    URL,
    SEARCH,
    QUESTION,
    AI_COMMAND,
    NAVIGATION,
    UNKNOWN
}

data class ClassifiedQuery(
    val rawQuery: String,
    val normalizedTarget: String,
    val intent: QueryIntentType,
    val confidence: Float = 1.0f
)

data class ReaderArticle(
    val title: String,
    val byline: String? = null,
    val content: String,
    val excerpt: String? = null,
    val url: String,
    val textLength: Int = 0
)

data class SitePermission(
    val id: Long = 0,
    val origin: String,
    val permissionType: String,
    val isAllowed: Boolean,
    val grantedAt: Long = System.currentTimeMillis()
)

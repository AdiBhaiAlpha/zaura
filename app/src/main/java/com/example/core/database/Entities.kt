package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val faviconUrl: String? = null,
    val groupName: String = "General",
    val isPrivate: Boolean = false,
    val position: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val folder: String = "Bookmarks",
    val createdAt: Long = System.currentTimeMillis(),
    val faviconUrl: String? = null
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val fileSize: Long,
    val downloadedBytes: Long,
    val status: String,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String
)

@Entity(tableName = "research_sessions")
data class ResearchSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val query: String,
    val aiAnswer: String,
    val sourcesJson: String,
    val followupsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "site_permissions")
data class SitePermissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val origin: String,
    val permissionType: String,
    val isAllowed: Boolean,
    val grantedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocked_trackers")
data class BlockedTrackerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val category: String,
    val isBlocked: Boolean = true
)

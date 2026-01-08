package com.android.music.download.data.model

import java.util.Date

/**
 * Represents a downloadable item extracted from a URL
 */
data class DownloadItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
    val duration: String?,
    val author: String?,
    val platform: String,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val fileSize: Long? = null,
    val downloadedSize: Long = 0,
    val createdAt: Date = Date(),
    val completedAt: Date? = null,
    val filePath: String? = null,
    val errorMessage: String? = null,
    val isFolder: Boolean = false,
    val itemCount: Int = 0,
    // Playlist download progress
    val isPlaylist: Boolean = false,
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val currentItemTitle: String? = null
)

/**
 * Download status enum
 */
enum class DownloadStatus {
    PENDING,
    EXTRACTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Extracted content from a URL (before download)
 */
data class ExtractedContent(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: String?,
    val author: String?,
    val platform: String,
    val formats: List<DownloadFormat> = emptyList(),
    val isPlaylist: Boolean = false,
    val playlistItems: List<ExtractedContent> = emptyList(),
    // Cached streaming URLs for instant preview (no need to fetch again)
    val cachedVideoUrl: String? = null,
    val cachedAudioUrl: String? = null
)

/**
 * Available download formats
 */
data class DownloadFormat(
    val formatId: String,
    val extension: String,
    val quality: String,
    val fileSize: Long?,
    val hasAudio: Boolean,
    val hasVideo: Boolean,
    val resolution: String?
)

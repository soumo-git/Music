package com.android.music.download.engine.core

import com.android.music.download.data.model.DownloadFormat
import com.android.music.download.data.model.ExtractedContent
import kotlinx.coroutines.flow.Flow

/**
 * Core interface for download engines.
 * This abstraction allows swapping different download engines (yt-dlp, youtube-dl, etc.)
 * without changing the rest of the application.
 */
interface DownloadEngine {
    
    /**
     * Get the engine name (e.g., "yt-dlp", "youtube-dl")
     */
    val engineName: String
    
    /**
     * Get the current installed version of the engine
     * Returns null if engine is not installed
     */
    suspend fun getInstalledVersion(): String?
    
    /**
     * Check if the engine is properly installed and ready to use
     */
    suspend fun isInstalled(): Boolean
    
    /**
     * Extract content metadata from a URL
     * 
     * @param url The URL to extract from
     * @return ExtractedContent with metadata and available formats
     */
    suspend fun extractContent(url: String): Result<ExtractedContent>
    
    /**
     * Download content from a URL
     * 
     * @param url The URL to download from
     * @param format The format to download
     * @param outputPath The output file path
     * @return Flow emitting download progress (0-100)
     */
    fun download(
        url: String,
        format: DownloadFormat,
        outputPath: String
    ): Flow<DownloadProgress>
    
    /**
     * Cancel an ongoing download
     * 
     * @param downloadId The download ID to cancel
     */
    suspend fun cancelDownload(downloadId: String)
    
    /**
     * Check if the engine supports the given URL
     */
    fun supportsUrl(url: String): Boolean
}

/**
 * Download progress information
 */
data class DownloadProgress(
    val downloadId: String,
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: String?,
    val eta: String?,
    val status: DownloadProgressStatus,
    // Playlist progress
    val isPlaylist: Boolean = false,
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val currentItemTitle: String? = null
)

enum class DownloadProgressStatus {
    STARTING,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Exception thrown when engine operations fail
 */
open class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

class EngineNotInstalledException(message: String = "Download engine is not installed") : EngineException(message)
class EngineOutdatedException(
    val currentVersion: String,
    val latestVersion: String,
    message: String = "Engine is outdated. Current: $currentVersion, Latest: $latestVersion"
) : EngineException(message)
class ExtractionFailedException(message: String, cause: Throwable? = null) : EngineException(message, cause)
class DownloadFailedException(message: String, cause: Throwable? = null) : EngineException(message, cause)

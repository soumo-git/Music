package com.android.music.download.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton to manage download states across the app
 * Tracks which videos are being extracted or downloaded
 */
object DownloadStateManager {
    
    /**
     * Download state for a video
     */
    enum class DownloadState {
        IDLE,
        EXTRACTING,
        DOWNLOADING,
        COMPLETED,
        FAILED
    }
    
    /**
     * Download info for a video
     */
    data class DownloadInfo(
        val state: DownloadState = DownloadState.IDLE,
        val progress: Int = 0,
        val formatId: String = "best"
    )
    
    // Map of video ID to download state
    private val _downloadStates = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadInfo>> = _downloadStates.asStateFlow()
    
    /**
     * Set state for a video
     */
    fun setState(videoId: String, state: DownloadState, progress: Int = 0, formatId: String = "best") {
        val current = _downloadStates.value.toMutableMap()
        current[videoId] = DownloadInfo(state, progress, formatId)
        _downloadStates.value = current
    }
    
    /**
     * Get state for a video
     */
    fun getState(videoId: String): DownloadInfo {
        return _downloadStates.value[videoId] ?: DownloadInfo()
    }
    
    /**
     * Clear state for a video
     */
    fun clearState(videoId: String) {
        val current = _downloadStates.value.toMutableMap()
        current.remove(videoId)
        _downloadStates.value = current
    }
    
    /**
     * Check if video is being extracted
     */
    fun isExtracting(videoId: String): Boolean {
        return getState(videoId).state == DownloadState.EXTRACTING
    }
    
    /**
     * Check if video is being downloaded
     */
    fun isDownloading(videoId: String): Boolean {
        return getState(videoId).state == DownloadState.DOWNLOADING
    }
}

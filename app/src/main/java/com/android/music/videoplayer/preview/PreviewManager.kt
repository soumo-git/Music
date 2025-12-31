package com.android.music.videoplayer.preview

import android.content.Context
import android.util.Log
import com.android.music.download.data.model.ExtractedContent
import com.android.music.download.engine.manager.EngineManagerFactory
import com.android.music.download.engine.ytdlp.YtDlpAndroidEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton manager for video preview functionality.
 * Handles caching of streaming URLs and playback position.
 * 
 * Flow:
 * 1. User pastes link -> App extracts content with preview URLs -> URLs cached
 * 2. User clicks preview -> App starts preview using cached URLs (instant)
 * 3. User back presses -> Preview saves timestamp
 * 4. User clicks preview again -> Video continues from saved position
 * 5. User pastes new link -> Previous preview cleared, new extraction starts
 */
object PreviewManager {
    
    private const val TAG = "PreviewManager"
    
    /**
     * Data class holding cached preview data for a URL
     */
    data class PreviewCache(
        val originalUrl: String,
        val videoUrl: String,
        val audioUrl: String?,
        val title: String,
        var savedPositionMs: Long = 0L
    )
    
    // Current cached preview (only one at a time)
    private var currentCache: PreviewCache? = null
    
    // Lock for thread safety
    private val lock = Any()
    
    /**
     * Cache preview URLs from extracted content.
     * Called after successful extraction.
     */
    fun cachePreview(
        originalUrl: String,
        videoUrl: String,
        audioUrl: String?,
        title: String
    ) {
        synchronized(lock) {
            // Clear previous cache if it's a different URL
            if (currentCache?.originalUrl != originalUrl) {
                Log.d(TAG, "Caching new preview for: $title")
                currentCache = PreviewCache(
                    originalUrl = originalUrl,
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    title = title,
                    savedPositionMs = 0L
                )
            } else {
                Log.d(TAG, "Preview already cached for: $title")
            }
        }
    }
    
    /**
     * Get cached preview for a URL.
     * Returns null if not cached.
     */
    fun getCachedPreview(originalUrl: String): PreviewCache? {
        synchronized(lock) {
            return if (currentCache?.originalUrl == originalUrl) {
                currentCache
            } else {
                null
            }
        }
    }
    
    /**
     * Get current cached preview (regardless of URL).
     */
    fun getCurrentPreview(): PreviewCache? {
        synchronized(lock) {
            return currentCache
        }
    }
    
    /**
     * Save playback position for resume functionality.
     */
    fun savePosition(positionMs: Long) {
        synchronized(lock) {
            currentCache?.let {
                it.savedPositionMs = positionMs
                Log.d(TAG, "Saved position: ${positionMs}ms for ${it.title}")
            }
        }
    }
    
    /**
     * Get saved position for current preview.
     */
    fun getSavedPosition(): Long {
        synchronized(lock) {
            return currentCache?.savedPositionMs ?: 0L
        }
    }
    
    /**
     * Clear all cached preview data.
     * Called when user pastes a new link.
     */
    fun clearCache() {
        synchronized(lock) {
            Log.d(TAG, "Clearing preview cache")
            currentCache = null
        }
    }
    
    /**
     * Check if preview is cached for a URL.
     */
    fun hasCachedPreview(originalUrl: String): Boolean {
        synchronized(lock) {
            return currentCache?.originalUrl == originalUrl
        }
    }
    
    /**
     * Extract content and cache streaming URLs for instant preview.
     * This is the main entry point for extraction with preview support.
     */
    suspend fun extractWithPreview(
        context: Context,
        url: String
    ): Result<ExtractedContent> = withContext(Dispatchers.IO) {
        try {
            // Only clear cache if this is a different URL
            synchronized(lock) {
                if (currentCache?.originalUrl != url) {
                    Log.d(TAG, "New URL detected, clearing previous cache")
                    currentCache = null
                }
            }
            
            val engineManager = EngineManagerFactory.getInstance(context)
            val engine = engineManager.getEngine()
            
            if (engine == null) {
                return@withContext Result.failure(Exception("Download engine not installed"))
            }
            
            val ytDlpEngine = engine as? YtDlpAndroidEngine
            if (ytDlpEngine == null) {
                return@withContext Result.failure(Exception("Preview requires yt-dlp engine"))
            }
            
            Log.d(TAG, "Extracting content with preview URLs for: $url")
            
            // Extract content with streaming URL pre-fetching
            val result = ytDlpEngine.extractContent(url, prefetchStreamingUrls = true)
            
            result.onSuccess { content ->
                // Cache the streaming URLs if available
                if (content.cachedVideoUrl != null) {
                    cachePreview(
                        originalUrl = url,
                        videoUrl = content.cachedVideoUrl,
                        audioUrl = content.cachedAudioUrl,
                        title = content.title
                    )
                    Log.d(TAG, "Successfully cached preview URLs")
                }
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract with preview: ${e.message}")
            Result.failure(e)
        }
    }
}

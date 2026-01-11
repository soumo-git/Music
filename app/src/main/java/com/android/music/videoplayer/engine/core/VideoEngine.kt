package com.android.music.videoplayer.engine.core

import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.StateFlow

/**
 * Core interface for video player engines.
 * This abstraction allows swapping different video engines (ExoPlayer, VLC, IJKPlayer, etc.)
 * without changing the rest of the application.
 */
interface VideoEngine {
    
    /**
     * Get the engine name (e.g., "ExoPlayer", "VLC", "IJKPlayer")
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
     * Initialize the video engine
     * @return Result indicating success or failure
     */
    suspend fun initialize(): Result<Unit>
    
    /**
     * Current player state
     */
    val playerState: StateFlow<VideoPlayerState>
    
    /**
     * Current playback position in milliseconds
     */
    val currentPosition: StateFlow<Long>
    
    /**
     * Total duration in milliseconds
     */
    val duration: StateFlow<Long>
    
    /**
     * Buffered position in milliseconds
     */
    val bufferedPosition: StateFlow<Long>
    
    /**
     * Whether the player is currently buffering
     */
    val isBuffering: StateFlow<Boolean>
    
    /**
     * Current error message, null if no error
     */
    val error: StateFlow<String?>
    
    /**
     * Available video qualities
     */
    val availableQualities: StateFlow<List<VideoQuality>>
    
    /**
     * Currently selected video quality
     */
    val selectedQuality: StateFlow<VideoQuality?>
    
    /**
     * Attach the engine to a PlayerView for rendering
     */
    fun attachToPlayerView(playerView: PlayerView)
    
    /**
     * Detach the engine from the current PlayerView
     */
    fun detachFromPlayerView()
    
    /**
     * Load and prepare a video from URL
     * @param url The video stream URL
     * @param audioUrl Optional separate audio stream URL (for merging)
     */
    fun loadVideo(url: String, audioUrl: String? = null)
    
    /**
     * Start video playback
     */
    fun play()
    
    /**
     * Pause video playback
     */
    fun pause()
    
    /**
     * Stop video playback and release resources
     */
    fun stop()
    
    /**
     * Seek to a specific position
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long)
    
    /**
     * Set playback speed
     * @param speed Playback speed (1.0 = normal, 0.5 = half speed, 2.0 = double speed)
     */
    fun setPlaybackSpeed(speed: Float)
    
    /**
     * Set video quality
     * @param quality The desired video quality
     */
    fun setVideoQuality(quality: VideoQuality)
    
    /**
     * Set volume
     * @param volume Volume level (0.0 to 1.0)
     */
    fun setVolume(volume: Float)
    
    /**
     * Check if the engine supports the given URL
     */
    fun supportsUrl(url: String): Boolean
    
    /**
     * Release all resources and cleanup
     */
    fun release()
}

/**
 * Video player states
 */
enum class VideoPlayerState {
    IDLE,
    LOADING,
    READY,
    PLAYING,
    PAUSED,
    STOPPED,
    COMPLETED,
    ERROR
}

/**
 * Video quality representation
 */
data class VideoQuality(
    val id: String,
    val label: String,
    val width: Int,
    val height: Int,
    val bitrate: Int? = null
) {
    companion object {
        val AUTO = VideoQuality("auto", "Auto", 0, 0)
        val QUALITY_360P = VideoQuality("360p", "360p", 640, 360)
        val QUALITY_480P = VideoQuality("480p", "480p", 854, 480)
        val QUALITY_720P = VideoQuality("720p", "720p", 1280, 720)
    }
}

/**
 * Video engine information
 */
data class VideoEngineInfo(
    val name: String,
    val installedVersion: String?,
    val latestVersion: String?,
    val isInstalled: Boolean,
    val isUpdateAvailable: Boolean,
    val lastChecked: Long,
    val enginePath: String?
) {
    companion object {
        fun notInstalled(name: String) = VideoEngineInfo(
            name = name,
            installedVersion = null,
            latestVersion = null,
            isInstalled = false,
            isUpdateAvailable = false,
            lastChecked = 0L,
            enginePath = null
        )
    }
}

/**
 * Video engine update result
 */
sealed class VideoEngineUpdateResult {
    data class Success(val newVersion: String) : VideoEngineUpdateResult()
    data class AlreadyUpToDate(val version: String) : VideoEngineUpdateResult()
    data class Failed(val error: String, val cause: Throwable? = null) : VideoEngineUpdateResult()
    object Downloading : VideoEngineUpdateResult()
}

/**
 * Exception thrown when video engine operations fail
 */
open class VideoEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)


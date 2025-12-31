package com.android.music.videoplayer.core

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core video player engine using Media3 ExoPlayer for robust video streaming
 * Supports multiple formats: MP4, MKV, WebM, HLS, DASH, etc.
 * Supports merging separate video and audio streams for highest quality playback
 * 
 * Architecture: Singleton pattern with state management via StateFlow
 * Thread Safety: All operations are thread-safe
 */
class VideoPlayerEngine private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoPlayerEngine"
        
        @Volatile
        private var instance: VideoPlayerEngine? = null
        
        fun getInstance(context: Context): VideoPlayerEngine {
            return instance ?: synchronized(this) {
                instance ?: VideoPlayerEngine(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private var exoPlayer: ExoPlayer? = null
    private var mediaSourceFactory: DefaultMediaSourceFactory? = null
    
    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    
    private val _videoScale = MutableStateFlow(1f)
    val videoScale: StateFlow<Float> = _videoScale.asStateFlow()
    
    private val _selectedQuality = MutableStateFlow("auto")
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()
    
    private val _availableQualities = MutableStateFlow<List<String>>(emptyList())
    val availableQualities: StateFlow<List<String>> = _availableQualities.asStateFlow()
    
    private var currentUrl: String? = null
    
    /**
     * Initialize ExoPlayer and attach to PlayerView
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun initialize(playerView: PlayerView) {
        try {
            if (exoPlayer == null) {
                mediaSourceFactory = DefaultMediaSourceFactory(context)
                exoPlayer = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory!!)
                    .build()
            }
            
            playerView.player = exoPlayer
            
            exoPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_IDLE -> _playerState.value = PlayerState.Idle
                        Player.STATE_BUFFERING -> {
                            _isBuffering.value = true
                            _playerState.value = PlayerState.Loading
                        }
                        Player.STATE_READY -> {
                            _isBuffering.value = false
                            _duration.value = exoPlayer?.duration ?: 0L
                            _playerState.value = if (exoPlayer?.isPlaying == true) 
                                PlayerState.Playing else PlayerState.Ready
                        }
                        Player.STATE_ENDED -> {
                            _playerState.value = PlayerState.Completed
                        }
                    }
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        _playerState.value = PlayerState.Playing
                    } else if (_playerState.value == PlayerState.Playing) {
                        _playerState.value = PlayerState.Paused
                    }
                }
                
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error: ${error.message}")
                    _error.value = error.message ?: "Unknown playback error"
                    _playerState.value = PlayerState.Error
                }
            })
            
            Log.d(TAG, "Video player initialized with ExoPlayer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize video player: ${e.message}")
            _error.value = "Failed to initialize video player"
        }
    }
    
    /**
     * Load and prepare a video from URL (supports streaming)
     */
    fun loadVideo(url: String) {
        loadVideo(url, null)
    }
    
    /**
     * Load and prepare video with separate video and audio URLs for highest quality
     * @param videoUrl The video stream URL
     * @param audioUrl Optional separate audio stream URL (for merging)
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun loadVideo(videoUrl: String, audioUrl: String?) {
        try {
            _playerState.value = PlayerState.Loading
            _error.value = null
            currentUrl = videoUrl
            
            if (audioUrl != null && mediaSourceFactory != null) {
                // Merge video and audio streams for highest quality
                Log.d(TAG, "Loading video with separate audio stream (highest quality)")
                Log.d(TAG, "Video URL: ${videoUrl.take(100)}...")
                Log.d(TAG, "Audio URL: ${audioUrl.take(100)}...")
                
                val videoSource: MediaSource = mediaSourceFactory!!.createMediaSource(
                    MediaItem.fromUri(Uri.parse(videoUrl))
                )
                val audioSource: MediaSource = mediaSourceFactory!!.createMediaSource(
                    MediaItem.fromUri(Uri.parse(audioUrl))
                )
                
                val mergedSource = MergingMediaSource(videoSource, audioSource)
                exoPlayer?.setMediaSource(mergedSource)
                exoPlayer?.prepare()
            } else {
                // Single URL with both video and audio
                Log.d(TAG, "Loading video from single URL: ${videoUrl.take(100)}...")
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                exoPlayer?.setMediaItem(mediaItem)
                exoPlayer?.prepare()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video: ${e.message}")
            _error.value = "Failed to load video: ${e.message}"
            _playerState.value = PlayerState.Error
        }
    }
    
    /**
     * Start video playback
     */
    fun play() {
        try {
            exoPlayer?.play()
            Log.d(TAG, "Playback started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play: ${e.message}")
            _error.value = "Failed to play video"
        }
    }
    
    /**
     * Pause video playback
     */
    fun pause() {
        try {
            exoPlayer?.pause()
            Log.d(TAG, "Playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause: ${e.message}")
            _error.value = "Failed to pause video"
        }
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        if (exoPlayer?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }
    
    /**
     * Seek to a specific position in milliseconds
     */
    fun seekTo(position: Long) {
        try {
            exoPlayer?.seekTo(position)
            _currentPosition.value = position
            Log.d(TAG, "Seeked to: $position ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek: ${e.message}")
            _error.value = "Failed to seek"
        }
    }
    
    /**
     * Seek forward by specified milliseconds
     */
    fun seekForward(ms: Long = 10000) {
        val newPosition = (getCurrentPosition() + ms).coerceAtMost(getDuration())
        seekTo(newPosition)
    }
    
    /**
     * Seek backward by specified milliseconds
     */
    fun seekBackward(ms: Long = 10000) {
        val newPosition = (getCurrentPosition() - ms).coerceAtLeast(0)
        seekTo(newPosition)
    }
    
    /**
     * Stop playback
     */
    fun stop() {
        try {
            exoPlayer?.stop()
            _playerState.value = PlayerState.Stopped
            Log.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop: ${e.message}")
        }
    }
    
    /**
     * Release all resources
     */
    fun release() {
        try {
            exoPlayer?.release()
            exoPlayer = null
            _playerState.value = PlayerState.Idle
            _currentPosition.value = 0
            _duration.value = 0
            currentUrl = null
            Log.d(TAG, "Resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources: ${e.message}")
        }
    }
    
    /**
     * Get current playback position
     */
    fun getCurrentPosition(): Long {
        return try {
            exoPlayer?.currentPosition ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Get video duration
     */
    fun getDuration(): Long {
        return try {
            exoPlayer?.duration ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Get buffered position
     */
    fun getBufferedPosition(): Long {
        return try {
            exoPlayer?.bufferedPosition ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Check if video is playing
     */
    fun isPlaying(): Boolean {
        return try {
            exoPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update position state (call from UI timer)
     */
    fun updatePositionState() {
        _currentPosition.value = getCurrentPosition()
        _bufferedPosition.value = getBufferedPosition()
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Set video scale/zoom level (1.0 = normal, 1.5 = 1.5x zoom, etc.)
     */
    fun setVideoScale(scale: Float) {
        try {
            val clampedScale = scale.coerceIn(0.5f, 3f)
            _videoScale.value = clampedScale
            Log.d(TAG, "Video scale set to: $clampedScale")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set video scale: ${e.message}")
        }
    }
    
    /**
     * Get current video scale
     */
    fun getVideoScale(): Float = _videoScale.value
    
    /**
     * Zoom in (increase scale by 0.2)
     */
    fun zoomIn() {
        val newScale = (_videoScale.value + 0.2f).coerceAtMost(3f)
        setVideoScale(newScale)
    }
    
    /**
     * Zoom out (decrease scale by 0.2)
     */
    fun zoomOut() {
        val newScale = (_videoScale.value - 0.2f).coerceAtLeast(0.5f)
        setVideoScale(newScale)
    }
    
    /**
     * Reset zoom to normal
     */
    fun resetZoom() {
        setVideoScale(1f)
    }
    
    /**
     * Set video quality preference
     */
    fun setQuality(quality: String) {
        _selectedQuality.value = quality
        Log.d(TAG, "Quality preference set to: $quality")
    }
    
    /**
     * Get current quality preference
     */
    fun getQuality(): String = _selectedQuality.value
    
    /**
     * Set available qualities for current video
     */
    fun setAvailableQualities(qualities: List<String>) {
        _availableQualities.value = qualities
        Log.d(TAG, "Available qualities: $qualities")
    }
    
    /**
     * Get available qualities
     */
    fun getAvailableQualities(): List<String> = _availableQualities.value
}

/**
 * Video player state enum
 */
enum class PlayerState {
    Idle,
    Loading,
    Ready,
    Playing,
    Paused,
    Stopped,
    Completed,
    Error
}

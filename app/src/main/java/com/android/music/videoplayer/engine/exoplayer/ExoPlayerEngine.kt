package com.android.music.videoplayer.engine.exoplayer

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
import com.android.music.videoplayer.engine.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ExoPlayer implementation of VideoEngine
 * Uses Media3 ExoPlayer for robust video streaming with support for multiple formats
 */
class ExoPlayerEngine(private val context: Context) : VideoEngine {
    
    companion object {
        private const val TAG = "ExoPlayerEngine"
        private const val ENGINE_VERSION = "1.2.0" // Media3 ExoPlayer version
    }
    
    override val engineName: String = "ExoPlayer"
    
    private var exoPlayer: ExoPlayer? = null
    private var mediaSourceFactory: DefaultMediaSourceFactory? = null
    private var currentPlayerView: PlayerView? = null
    
    private val _playerState = MutableStateFlow<VideoPlayerState>(VideoPlayerState.IDLE)
    override val playerState: StateFlow<VideoPlayerState> = _playerState.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _bufferedPosition = MutableStateFlow(0L)
    override val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    
    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    override val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities.asStateFlow()
    
    private val _selectedQuality = MutableStateFlow<VideoQuality?>(VideoQuality.AUTO)
    override val selectedQuality: StateFlow<VideoQuality?> = _selectedQuality.asStateFlow()
    
    private var isInitialized = false
    
    override suspend fun getInstalledVersion(): String? {
        return ENGINE_VERSION
    }
    
    override suspend fun isInstalled(): Boolean {
        return true // ExoPlayer is bundled with the app
    }
    
    override suspend fun initialize(): Result<Unit> {
        return try {
            if (isInitialized) {
                return Result.success(Unit)
            }
            
            Log.d(TAG, "Initializing ExoPlayer engine...")
            
            mediaSourceFactory = DefaultMediaSourceFactory(context)
            exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory!!)
                .build()
            
            setupPlayerListener()
            
            isInitialized = true
            Log.d(TAG, "ExoPlayer engine initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ExoPlayer engine", e)
            Result.failure(VideoEngineException("Failed to initialize ExoPlayer: ${e.message}", e))
        }
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun setupPlayerListener() {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> _playerState.value = VideoPlayerState.IDLE
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                        _playerState.value = VideoPlayerState.LOADING
                    }
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        _duration.value = exoPlayer?.duration ?: 0L
                        _playerState.value = if (exoPlayer?.isPlaying == true) 
                            VideoPlayerState.PLAYING else VideoPlayerState.READY
                        
                        // Set available qualities (simplified for ExoPlayer)
                        _availableQualities.value = listOf(
                            VideoQuality.AUTO,
                            VideoQuality.QUALITY_720P,
                            VideoQuality.QUALITY_480P,
                            VideoQuality.QUALITY_360P
                        )
                    }
                    Player.STATE_ENDED -> {
                        _playerState.value = VideoPlayerState.COMPLETED
                    }
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _playerState.value = VideoPlayerState.PLAYING
                } else if (_playerState.value == VideoPlayerState.PLAYING) {
                    _playerState.value = VideoPlayerState.PAUSED
                }
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error: ${error.message}")
                _error.value = error.message ?: "Unknown playback error"
                _playerState.value = VideoPlayerState.ERROR
            }
        })
    }
    
    override fun attachToPlayerView(playerView: PlayerView) {
        currentPlayerView = playerView
        playerView.player = exoPlayer
        Log.d(TAG, "Attached to PlayerView")
    }
    
    override fun detachFromPlayerView() {
        currentPlayerView?.player = null
        currentPlayerView = null
        Log.d(TAG, "Detached from PlayerView")
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun loadVideo(url: String, audioUrl: String?) {
        try {
            // Stop any currently playing video first
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            
            _playerState.value = VideoPlayerState.LOADING
            _error.value = null
            
            if (audioUrl != null && mediaSourceFactory != null) {
                // Merge video and audio streams for highest quality
                Log.d(TAG, "Loading video with separate audio stream")
                Log.d(TAG, "Video URL: ${url.take(100)}...")
                Log.d(TAG, "Audio URL: ${audioUrl.take(100)}...")
                
                val videoSource: MediaSource = mediaSourceFactory!!.createMediaSource(
                    MediaItem.fromUri(Uri.parse(url))
                )
                val audioSource: MediaSource = mediaSourceFactory!!.createMediaSource(
                    MediaItem.fromUri(Uri.parse(audioUrl))
                )
                
                val mergedSource = MergingMediaSource(videoSource, audioSource)
                exoPlayer?.setMediaSource(mergedSource)
                exoPlayer?.prepare()
            } else {
                // Single URL with both video and audio
                Log.d(TAG, "Loading video from single URL: ${url.take(100)}...")
                val mediaItem = MediaItem.fromUri(Uri.parse(url))
                exoPlayer?.setMediaItem(mediaItem)
                exoPlayer?.prepare()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video: ${e.message}")
            _error.value = "Failed to load video: ${e.message}"
            _playerState.value = VideoPlayerState.ERROR
        }
    }
    
    override fun play() {
        try {
            exoPlayer?.play()
            Log.d(TAG, "Playback started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play: ${e.message}")
            _error.value = "Failed to play video"
        }
    }
    
    override fun pause() {
        try {
            exoPlayer?.pause()
            Log.d(TAG, "Playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause: ${e.message}")
            _error.value = "Failed to pause video"
        }
    }
    
    override fun stop() {
        try {
            exoPlayer?.stop()
            _playerState.value = VideoPlayerState.STOPPED
            Log.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop: ${e.message}")
        }
    }
    
    override fun seekTo(positionMs: Long) {
        try {
            exoPlayer?.seekTo(positionMs)
            _currentPosition.value = positionMs
            Log.d(TAG, "Seeked to: $positionMs ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek: ${e.message}")
            _error.value = "Failed to seek"
        }
    }
    
    override fun setPlaybackSpeed(speed: Float) {
        try {
            exoPlayer?.setPlaybackSpeed(speed)
            Log.d(TAG, "Playback speed set to: $speed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set playback speed: ${e.message}")
        }
    }
    
    override fun setVideoQuality(quality: VideoQuality) {
        _selectedQuality.value = quality
        Log.d(TAG, "Video quality set to: ${quality.label}")
        // ExoPlayer handles quality selection automatically in most cases
        // For manual quality selection, you would need to implement track selection
    }
    
    override fun setVolume(volume: Float) {
        try {
            exoPlayer?.volume = volume.coerceIn(0f, 1f)
            Log.d(TAG, "Volume set to: $volume")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume: ${e.message}")
        }
    }
    
    override fun supportsUrl(url: String): Boolean {
        // ExoPlayer supports most common video formats and protocols
        return url.startsWith("http://") || url.startsWith("https://") || 
               url.startsWith("file://") || url.startsWith("content://")
    }
    
    override fun release() {
        try {
            // Ensure we're on the main thread for ExoPlayer operations
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                // Already on main thread
                releaseInternal()
            } else {
                // Post to main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    releaseInternal()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ExoPlayer engine: ${e.message}")
        }
    }
    
    private fun releaseInternal() {
        try {
            detachFromPlayerView()
            exoPlayer?.release()
            exoPlayer = null
            mediaSourceFactory = null
            _playerState.value = VideoPlayerState.IDLE
            _currentPosition.value = 0
            _duration.value = 0
            _bufferedPosition.value = 0
            _error.value = null
            _isBuffering.value = false
            _availableQualities.value = emptyList()
            _selectedQuality.value = null
            isInitialized = false
            Log.d(TAG, "ExoPlayer engine released")
        } catch (e: Exception) {
            Log.e(TAG, "Error in releaseInternal: ${e.message}")
        }
    }
    
    /**
     * Update position state (call from UI timer)
     */
    fun updatePositionState() {
        _currentPosition.value = exoPlayer?.currentPosition ?: 0L
        _bufferedPosition.value = exoPlayer?.bufferedPosition ?: 0L
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
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
}
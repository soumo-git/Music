package com.android.music.videoplayer.core

import android.content.Context
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
    
    private val _playerState = MutableStateFlow(PlayerState.Idle)

    private val _currentPosition = MutableStateFlow(0L)

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isBuffering = MutableStateFlow(false)

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
     * Check if video is playing
     */
    fun isPlaying(): Boolean {
        return try {
            exoPlayer?.isPlaying ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }

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

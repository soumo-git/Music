package com.android.music.videoplayer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.android.music.R
import com.android.music.databinding.ActivityVideoPlayerBinding
import com.android.music.videoplayer.engine.manager.VideoEngineManagerFactory
import com.android.music.videoplayer.preview.PreviewManager

/**
 * Clean video player activity for streaming video preview.
 * 
 * Features:
 * - Single ExoPlayer instance per activity
 * - Resume from saved position
 * - Saves position on back press
 * - No duplicate players or audio overlap
 */
class VideoPlayerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "VideoPlayerActivity"
        private const val EXTRA_VIDEO_URL = "extra_video_url"
        private const val EXTRA_AUDIO_URL = "extra_audio_url"
        private const val EXTRA_VIDEO_TITLE = "extra_video_title"
        private const val EXTRA_START_POSITION = "extra_start_position"
        
        /**
         * Start video player with video and optional audio URLs.
         */
        fun start(
            context: Context,
            videoUrl: String,
            audioUrl: String? = null,
            title: String = "",
            startPositionMs: Long = 0L
        ) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_AUDIO_URL, audioUrl)
                putExtra(EXTRA_VIDEO_TITLE, title)
                putExtra(EXTRA_START_POSITION, startPositionMs)
                // Ensure single instance - clear any existing
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
    }
    
    private lateinit var binding: ActivityVideoPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    
    private var videoUrl: String = ""
    private var audioUrl: String? = null
    private var videoTitle: String = ""
    private var startPositionMs: Long = 0L
    
    // Pinch-to-zoom support
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoomMode = 0 // 0=fit, 1=fill, 2=zoom
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        
        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get extras
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
        startPositionMs = intent.getLongExtra(EXTRA_START_POSITION, PreviewManager.getSavedPosition())
        
        if (videoUrl.isEmpty()) {
            Toast.makeText(this, "No video URL provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Check if video engine is installed
        if (!isEngineInstalled()) {
            showEngineNotInstalledDialog()
            return
        }
        
        setupUI()
        enableImmersiveMode()
        initializePlayer()
    }
    
    private fun isEngineInstalled(): Boolean {
        val engineManager = VideoEngineManagerFactory.getInstance(applicationContext)
        return engineManager.getEngine() != null
    }
    
    private fun setupUI() {
        binding.tvTitle.text = videoTitle
        
        binding.btnClose.setOnClickListener {
            savePositionAndFinish()
        }
        
        binding.btnRetry.setOnClickListener {
            binding.errorOverlay.visibility = View.GONE
            initializePlayer()
        }
        
        binding.btnFullscreen.setOnClickListener {
            toggleOrientation()
        }
        
        setupPinchToZoom()
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun setupPinchToZoom() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                
                // Pinch out (zoom in) - scale > 1
                if (scaleFactor > 1.1f) {
                    cycleZoomMode(true)
                    return true
                }
                // Pinch in (zoom out) - scale < 1
                else if (scaleFactor < 0.9f) {
                    cycleZoomMode(false)
                    return true
                }
                return false
            }
        })
        
        // Set touch listener on playerView to detect pinch gestures
        binding.playerView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            // Return false to allow PlayerView to handle other touch events (play/pause, seek, etc.)
            false
        }
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun cycleZoomMode(zoomIn: Boolean) {
        currentZoomMode = if (zoomIn) {
            (currentZoomMode + 1).coerceAtMost(2)
        } else {
            (currentZoomMode - 1).coerceAtLeast(0)
        }
        
        val resizeMode = when (currentZoomMode) {
            0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        
        binding.playerView.resizeMode = resizeMode
        
        val modeName = when (currentZoomMode) {
            0 -> "Fit"
            1 -> "Fill"
            2 -> "Zoom"
            else -> "Fit"
        }
        Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Zoom mode changed to: $modeName")
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initializePlayer() {
        Log.d(TAG, "Initializing player")
        
        // Release any existing player first
        releasePlayer()
        
        // Show loading
        binding.progressLoading.visibility = View.VISIBLE
        binding.errorOverlay.visibility = View.GONE
        
        try {
            // Create ExoPlayer
            val mediaSourceFactory = DefaultMediaSourceFactory(this)
            exoPlayer = ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
            
            // Attach to PlayerView
            binding.playerView.player = exoPlayer
            
            // Setup listener
            exoPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            binding.progressLoading.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            binding.progressLoading.visibility = View.GONE
                            // Auto-play when ready
                            exoPlayer?.play()
                        }
                        Player.STATE_ENDED -> {
                            // Video finished - reset position
                            PreviewManager.savePosition(0L)
                        }
                        Player.STATE_IDLE -> {
                            // Idle state
                        }
                    }
                }
                
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error: ${error.message}")
                    binding.progressLoading.visibility = View.GONE
                    binding.errorOverlay.visibility = View.VISIBLE
                    binding.tvError.text = "Playback error: ${error.message}"
                }
            })
            
            // Load media
            loadMedia()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize player: ${e.message}")
            binding.progressLoading.visibility = View.GONE
            binding.errorOverlay.visibility = View.VISIBLE
            binding.tvError.text = "Failed to initialize player: ${e.message}"
        }
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun loadMedia() {
        val player = exoPlayer ?: return
        
        try {
            if (audioUrl != null) {
                // Merge video and audio streams for highest quality
                Log.d(TAG, "Loading video with separate audio stream")
                
                val mediaSourceFactory = DefaultMediaSourceFactory(this)
                val videoSource = mediaSourceFactory.createMediaSource(
                    MediaItem.fromUri(videoUrl)
                )
                val audioSource = mediaSourceFactory.createMediaSource(
                    MediaItem.fromUri(audioUrl!!)
                )
                
                val mergedSource = MergingMediaSource(videoSource, audioSource)
                player.setMediaSource(mergedSource)
            } else {
                // Single URL with both video and audio
                Log.d(TAG, "Loading video from single URL")
                player.setMediaItem(MediaItem.fromUri(videoUrl))
            }
            
            player.prepare()
            
            // Seek to saved position if any
            if (startPositionMs > 0) {
                Log.d(TAG, "Seeking to saved position: ${startPositionMs}ms")
                player.seekTo(startPositionMs)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load media: ${e.message}")
            binding.progressLoading.visibility = View.GONE
            binding.errorOverlay.visibility = View.VISIBLE
            binding.tvError.text = "Failed to load video: ${e.message}"
        }
    }
    
    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    private fun toggleOrientation() {
        requestedOrientation = if (requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            binding.btnFullscreen.setImageResource(R.drawable.ic_fullscreen)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            binding.btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
    
    private fun showEngineNotInstalledDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Video Engine Not Installed")
            .setMessage("The video playback engine is not installed. Please install it from Settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                startActivity(Intent(this, com.android.music.settings.SettingsActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun savePositionAndFinish() {
        // Save current position for resume
        exoPlayer?.let { player ->
            val position = player.currentPosition
            if (position > 0) {
                PreviewManager.savePosition(position)
                Log.d(TAG, "Saved position: ${position}ms")
            }
        }
        finish()
    }
    
    private fun releasePlayer() {
        Log.d(TAG, "Releasing player")
        binding.playerView.player = null
        exoPlayer?.release()
        exoPlayer = null
    }
    
    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }
    
    override fun onStop() {
        super.onStop()
        // Save position when activity stops
        exoPlayer?.let { player ->
            val position = player.currentPosition
            if (position > 0) {
                PreviewManager.savePosition(position)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        savePositionAndFinish()
    }
}

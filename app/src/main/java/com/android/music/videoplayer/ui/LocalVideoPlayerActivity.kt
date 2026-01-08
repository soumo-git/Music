package com.android.music.videoplayer.ui

import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.android.music.R
import com.android.music.data.model.Video
import com.android.music.databinding.ActivityLocalVideoPlayerBinding

/**
 * Local video player activity for playing device videos using ExoPlayer.
 * Supports minimizing to a player bar while continuing playback.
 */
class LocalVideoPlayerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "LocalVideoPlayer"
        const val EXTRA_VIDEO = "extra_video"
        const val EXTRA_VIDEO_LIST = "extra_video_list"
        const val EXTRA_START_POSITION = "extra_start_position"
        
        // Broadcast actions
        const val BROADCAST_VIDEO_STATE = "com.android.music.VIDEO_STATE"
        const val BROADCAST_VIDEO_PROGRESS = "com.android.music.VIDEO_PROGRESS"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        
        // Singleton player state for background playback
        private var sharedPlayer: ExoPlayer? = null
        private var currentVideo: Video? = null
        private var videoList: List<Video> = emptyList()
        private var currentIndex: Int = 0
        
        fun start(context: Context, video: Video, videos: List<Video> = listOf(video)) {
            val intent = Intent(context, LocalVideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO, video)
                putParcelableArrayListExtra(EXTRA_VIDEO_LIST, ArrayList(videos))
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
        
        fun getCurrentVideo(): Video? = currentVideo
        fun isPlaying(): Boolean = sharedPlayer?.isPlaying == true
        fun getPlayer(): ExoPlayer? = sharedPlayer
        fun getCurrentPosition(): Long = sharedPlayer?.currentPosition ?: 0L
        fun getDuration(): Long = sharedPlayer?.duration ?: 0L
        
        fun togglePlayPause() {
            sharedPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
        }
        
        fun stopPlayback() {
            sharedPlayer?.stop()
            sharedPlayer?.release()
            sharedPlayer = null
            currentVideo = null
        }
    }
    
    private lateinit var binding: ActivityLocalVideoPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    
    private var video: Video? = null
    private var startPositionMs: Long = 0L
    
    // Pinch-to-zoom support
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoomMode = 0 // 0=fit, 1=fill, 2=zoom
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        binding = ActivityLocalVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get video from intent
        video = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_VIDEO, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_VIDEO)
        }
        
        val videos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_VIDEO_LIST, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_VIDEO_LIST)
        }
        
        if (video == null) {
            Toast.makeText(this, "No video provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        videos?.let { 
            videoList = it 
            currentIndex = it.indexOfFirst { v -> v.id == video?.id }.coerceAtLeast(0)
        }
        currentVideo = video
        
        startPositionMs = intent.getLongExtra(EXTRA_START_POSITION, 0L)
        
        setupUI()
        enableImmersiveMode()
        initializePlayer()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Get new video
        val newVideo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_VIDEO, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_VIDEO)
        }
        
        if (newVideo != null && newVideo.id != video?.id) {
            video = newVideo
            currentVideo = newVideo
            loadMedia()
        }
    }
    
    private fun setupUI() {
        binding.tvTitle.text = video?.title ?: "Video"
        
        binding.btnClose.setOnClickListener {
            minimizeToBar()
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
                
                if (scaleFactor > 1.1f) {
                    cycleZoomMode(true)
                    return true
                } else if (scaleFactor < 0.9f) {
                    cycleZoomMode(false)
                    return true
                }
                return false
            }
        })
        
        binding.playerView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            false
        }
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun cycleZoomMode(zoomIn: Boolean) {
        if (zoomIn) {
            currentZoomMode = (currentZoomMode + 1).coerceAtMost(2)
        } else {
            currentZoomMode = (currentZoomMode - 1).coerceAtLeast(0)
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
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initializePlayer() {
        Log.d(TAG, "Initializing player")
        
        binding.progressLoading.visibility = View.VISIBLE
        binding.errorOverlay.visibility = View.GONE
        
        try {
            // Reuse shared player if exists, otherwise create new
            if (sharedPlayer == null) {
                sharedPlayer = ExoPlayer.Builder(this).build()
            }
            exoPlayer = sharedPlayer
            
            binding.playerView.player = exoPlayer
            
            exoPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            binding.progressLoading.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            binding.progressLoading.visibility = View.GONE
                            exoPlayer?.play()
                            broadcastState()
                        }
                        Player.STATE_ENDED -> {
                            // Play next video if available
                            playNextVideo()
                        }
                        Player.STATE_IDLE -> {}
                    }
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    broadcastState()
                }
                
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error: ${error.message}")
                    binding.progressLoading.visibility = View.GONE
                    binding.errorOverlay.visibility = View.VISIBLE
                    binding.tvError.text = "Playback error: ${error.message}"
                }
            })
            
            loadMedia()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize player: ${e.message}")
            binding.progressLoading.visibility = View.GONE
            binding.errorOverlay.visibility = View.VISIBLE
            binding.tvError.text = "Failed to initialize player: ${e.message}"
        }
    }
    
    private fun loadMedia() {
        val player = exoPlayer ?: return
        val videoPath = video?.path ?: return
        
        try {
            Log.d(TAG, "Loading video from: $videoPath")
            binding.tvTitle.text = video?.title ?: "Video"
            
            val mediaItem = MediaItem.fromUri(videoPath)
            player.setMediaItem(mediaItem)
            player.prepare()
            
            if (startPositionMs > 0) {
                player.seekTo(startPositionMs)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load media: ${e.message}")
            binding.progressLoading.visibility = View.GONE
            binding.errorOverlay.visibility = View.VISIBLE
            binding.tvError.text = "Failed to load video: ${e.message}"
        }
    }
    
    private fun playNextVideo() {
        if (currentIndex < videoList.size - 1) {
            currentIndex++
            video = videoList[currentIndex]
            currentVideo = video
            loadMedia()
        }
    }
    
    private fun broadcastState() {
        val intent = Intent(BROADCAST_VIDEO_STATE).apply {
            putExtra(EXTRA_IS_PLAYING, exoPlayer?.isPlaying == true)
            putExtra(EXTRA_VIDEO_TITLE, video?.title)
            putExtra(EXTRA_VIDEO_PATH, video?.path)
            putExtra(EXTRA_POSITION, exoPlayer?.currentPosition ?: 0L)
            putExtra(EXTRA_DURATION, exoPlayer?.duration ?: 0L)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun minimizeToBar() {
        // Save current position and finish activity
        // Player continues in background via sharedPlayer
        finish()
        overridePendingTransition(0, R.anim.slide_down)
    }
    
    private fun toggleOrientation() {
        requestedOrientation = if (requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
    
    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    override fun onResume() {
        super.onResume()
        exoPlayer?.play()
    }
    
    override fun onPause() {
        super.onPause()
        // Don't pause - let it continue for background playback
    }
    
    override fun onStop() {
        super.onStop()
        // Player continues in background
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Don't release player here - it's shared for background playback
        // Only detach from view
        binding.playerView.player = null
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        minimizeToBar()
    }
}

package com.android.music.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.android.music.R
import com.android.music.data.model.Song
import com.android.music.data.model.Video
import com.android.music.databinding.ActivityFolderContentsBinding
import com.android.music.databinding.LayoutPlayerBarBinding
import com.android.music.databinding.LayoutVideoPlayerBarBinding
import com.android.music.service.MusicService
import com.android.music.ui.fragment.PlayerSheetFragment
import com.android.music.videoplayer.ui.LocalVideoPlayerActivity
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayoutMediator

class FolderContentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderContentsBinding
    private lateinit var playerBarBinding: LayoutPlayerBarBinding
    private var videoPlayerBarBinding: LayoutVideoPlayerBarBinding? = null
    
    private var folderName: String = ""
    private var songs: List<Song> = emptyList()
    private var videos: List<Video> = emptyList()
    private var currentSong: Song? = null
    private var isPlaying = false

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicService.BROADCAST_PROGRESS -> {
                    val position = intent.getIntExtra(MusicService.EXTRA_POSITION, 0)
                    val duration = intent.getIntExtra(MusicService.EXTRA_DURATION, 0)
                    updateProgress(position, duration)
                }
                MusicService.BROADCAST_PLAYBACK_STATE -> {
                    isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                    val song = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(MusicService.EXTRA_SONG, Song::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(MusicService.EXTRA_SONG)
                    }
                    updatePlaybackUI(isPlaying, song)
                }
            }
        }
    }
    
    private val videoStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LocalVideoPlayerActivity.BROADCAST_VIDEO_STATE -> {
                    val videoIsPlaying = intent.getBooleanExtra(LocalVideoPlayerActivity.EXTRA_IS_PLAYING, false)
                    val title = intent.getStringExtra(LocalVideoPlayerActivity.EXTRA_VIDEO_TITLE)
                    val position = intent.getLongExtra(LocalVideoPlayerActivity.EXTRA_POSITION, 0L)
                    val duration = intent.getLongExtra(LocalVideoPlayerActivity.EXTRA_DURATION, 0L)
                    updateVideoPlayerBar(videoIsPlaying, title, position, duration)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderContentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: "Folder"
        songs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_SONGS, Song::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_SONGS) ?: emptyList()
        }
        videos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_VIDEOS, Video::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_VIDEOS) ?: emptyList()
        }

        binding.tvTitle.text = folderName
        binding.btnBack.setOnClickListener { finish() }

        setupViewPager()
        setupPlayerBar()
        setupVideoPlayerBar()
        registerReceivers()
    }

    private fun setupViewPager() {
        val adapter = FolderContentsPagerAdapter(this, songs, videos)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Songs (${songs.size})"
                1 -> "Videos (${videos.size})"
                else -> ""
            }
        }.attach()
        
        // Hide tabs if only one type of content
        if (songs.isEmpty() || videos.isEmpty()) {
            binding.tabLayout.visibility = View.GONE
            if (songs.isEmpty() && videos.isNotEmpty()) {
                binding.viewPager.setCurrentItem(1, false)
            }
        }
    }

    private fun setupPlayerBar() {
        playerBarBinding = LayoutPlayerBarBinding.inflate(layoutInflater)
        binding.playerBarContainer.addView(playerBarBinding.root)

        playerBarBinding.root.setOnClickListener { openPlayerSheet() }
        playerBarBinding.cardThumbnail.setOnClickListener { openPlayerSheet() }
        playerBarBinding.playerSongInfo.setOnClickListener { openPlayerSheet() }

        playerBarBinding.btnPlayPause.setOnClickListener {
            val action = if (isPlaying) MusicService.ACTION_PAUSE else MusicService.ACTION_RESUME
            startService(Intent(this, MusicService::class.java).apply { this.action = action })
        }

        playerBarBinding.btnNext.setOnClickListener {
            startService(Intent(this, MusicService::class.java).apply { action = MusicService.ACTION_NEXT })
        }

        playerBarBinding.btnPrevious.setOnClickListener {
            startService(Intent(this, MusicService::class.java).apply { action = MusicService.ACTION_PREVIOUS })
        }
    }
    
    private fun setupVideoPlayerBar() {
        videoPlayerBarBinding = LayoutVideoPlayerBarBinding.inflate(layoutInflater)
        binding.videoPlayerBarContainer.addView(videoPlayerBarBinding?.root)
        
        videoPlayerBarBinding?.apply {
            root.setOnClickListener {
                LocalVideoPlayerActivity.getCurrentVideo()?.let { video ->
                    LocalVideoPlayerActivity.start(this@FolderContentsActivity, video, videos)
                }
            }
            
            btnPlayPause.setOnClickListener {
                LocalVideoPlayerActivity.togglePlayPause()
                updateVideoPlayPauseButton()
            }
            
            btnClose.setOnClickListener {
                LocalVideoPlayerActivity.stopPlayback()
                binding.videoPlayerBarContainer.visibility = View.GONE
            }
        }
    }

    private fun openPlayerSheet() {
        val playerSheet = PlayerSheetFragment.newInstance()
        playerSheet.show(supportFragmentManager, PlayerSheetFragment.TAG)
    }

    fun playSong(song: Song) {
        currentSong = song
        isPlaying = true
        binding.playerBarContainer.visibility = View.VISIBLE

        val serviceIntent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY
            putExtra(MusicService.EXTRA_SONG, song)
            putParcelableArrayListExtra(MusicService.EXTRA_PLAYLIST, ArrayList(songs))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        updatePlayerBarUI(song)
    }
    
    fun shufflePlaySongs(songList: List<Song>) {
        if (songList.isEmpty()) return
        
        val shuffledSongs = songList.shuffled()
        val firstSong = shuffledSongs.first()
        
        currentSong = firstSong
        isPlaying = true
        binding.playerBarContainer.visibility = View.VISIBLE

        val serviceIntent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY
            putExtra(MusicService.EXTRA_SONG, firstSong)
            putParcelableArrayListExtra(MusicService.EXTRA_PLAYLIST, ArrayList(shuffledSongs))
            putExtra(MusicService.EXTRA_SHUFFLE, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        updatePlayerBarUI(firstSong)
        Toast.makeText(this, "Playing ${songList.size} songs in shuffle mode", Toast.LENGTH_SHORT).show()
    }
    
    fun playVideo(video: Video) {
        LocalVideoPlayerActivity.start(this, video, videos)
        binding.videoPlayerBarContainer.visibility = View.VISIBLE
        updateVideoPlayerBarUI(video)
    }

    private fun updatePlayerBarUI(song: Song) {
        playerBarBinding.tvPlayerTitle.text = song.title
        playerBarBinding.tvPlayerSubtitle.text = song.artist
        
        com.android.music.util.AlbumArtUtil.loadAlbumArtWithFallback(
            Glide.with(this),
            playerBarBinding.ivPlayerThumbnail,
            song,
            44
        )
        
        com.android.music.util.AlbumArtUtil.applyPlayerBarTheming(
            playerBarBinding.root,
            song
        )
    }
    
    private fun updateVideoPlayerBarUI(video: Video) {
        videoPlayerBarBinding?.apply {
            tvVideoTitle.text = video.title
            tvVideoDuration.text = video.formattedDuration
            
            Glide.with(this@FolderContentsActivity)
                .load(video.thumbnailUri)
                .placeholder(R.drawable.ic_video)
                .centerCrop()
                .into(ivVideoThumbnail)
        }
    }
    
    private fun updateVideoPlayerBar(isPlaying: Boolean, title: String?, position: Long, duration: Long) {
        if (title == null) {
            binding.videoPlayerBarContainer.visibility = View.GONE
            return
        }
        
        binding.videoPlayerBarContainer.visibility = View.VISIBLE
        
        videoPlayerBarBinding?.apply {
            tvVideoTitle.text = title
            
            if (duration > 0) {
                val positionStr = formatDuration(position)
                val durationStr = formatDuration(duration)
                tvVideoDuration.text = "$positionStr / $durationStr"
                progressBar.max = duration.toInt()
                progressBar.progress = position.toInt()
            }
            
            btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
    }
    
    private fun updateVideoPlayPauseButton() {
        val isPlaying = LocalVideoPlayerActivity.isPlaying()
        videoPlayerBarBinding?.btnPlayPause?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
    
    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000 / 60) % 60
        val hours = ms / 1000 / 3600
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun updatePlaybackUI(isPlaying: Boolean, song: Song?) {
        this.isPlaying = isPlaying
        playerBarBinding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        song?.let {
            currentSong = it
            updatePlayerBarUI(it)
            binding.playerBarContainer.visibility = View.VISIBLE
        }
    }

    private fun updateProgress(position: Int, duration: Int) {
        playerBarBinding.progressBar.max = duration
        playerBarBinding.progressBar.progress = position
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(MusicService.BROADCAST_PROGRESS)
            addAction(MusicService.BROADCAST_PLAYBACK_STATE)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(playbackReceiver, filter)
        
        val videoFilter = IntentFilter().apply {
            addAction(LocalVideoPlayerActivity.BROADCAST_VIDEO_STATE)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(videoStateReceiver, videoFilter)
    }

    override fun onResume() {
        super.onResume()
        // Check if video is playing
        LocalVideoPlayerActivity.getCurrentVideo()?.let { video ->
            binding.videoPlayerBarContainer.visibility = View.VISIBLE
            updateVideoPlayerBarUI(video)
            updateVideoPlayPauseButton()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playbackReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(videoStateReceiver)
    }

    companion object {
        const val EXTRA_FOLDER_NAME = "extra_folder_name"
        const val EXTRA_SONGS = "extra_songs"
        const val EXTRA_VIDEOS = "extra_videos"

        fun start(context: Context, folderName: String, songs: List<Song>, videos: List<Video>) {
            val intent = Intent(context, FolderContentsActivity::class.java).apply {
                putExtra(EXTRA_FOLDER_NAME, folderName)
                putParcelableArrayListExtra(EXTRA_SONGS, ArrayList(songs))
                putParcelableArrayListExtra(EXTRA_VIDEOS, ArrayList(videos))
            }
            context.startActivity(intent)
        }
    }
    
    // Inner adapter for ViewPager
    inner class FolderContentsPagerAdapter(
        activity: FragmentActivity,
        private val songs: List<Song>,
        private val videos: List<Video>
    ) : FragmentStateAdapter(activity) {
        
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> FolderSongsFragment.newInstance(songs)
                1 -> FolderVideosFragment.newInstance(videos)
                else -> FolderSongsFragment.newInstance(songs)
            }
        }
    }
}

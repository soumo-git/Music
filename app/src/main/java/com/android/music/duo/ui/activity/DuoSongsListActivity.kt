package com.android.music.duo.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.R
import com.android.music.data.model.Song
import com.android.music.databinding.ActivitySongsListBinding
import com.android.music.databinding.LayoutPlayerBarBinding
import com.android.music.service.MusicService
import com.android.music.ui.adapter.SongAdapter
import com.android.music.ui.fragment.PlayerSheetFragment

/**
 * Activity to display songs list in Duo mode
 * Does NOT use DuoViewModel to avoid creating new repository instances
 * Instead, sends broadcasts to MainActivity for Duo sync
 */
class DuoSongsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongsListBinding
    private lateinit var playerBarBinding: LayoutPlayerBarBinding
    private lateinit var songAdapter: SongAdapter
    
    private var songs: List<Song> = emptyList()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySongsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Songs"
        songs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_SONGS, Song::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_SONGS) ?: emptyList()
        }

        binding.tvTitle.text = title
        binding.btnBack.setOnClickListener { finish() }
        binding.btnShuffle.setOnClickListener { shufflePlay() }

        setupRecyclerView()
        setupPlayerBar()
        registerReceivers()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song -> playSong(song) },
            onSongOptionClick = { song, option ->
                Toast.makeText(this, "${option.name}: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        )
        songAdapter.submitList(songs)

        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@DuoSongsListActivity)
            adapter = songAdapter
            setHasFixedSize(true)
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
            // Send Duo sync broadcast
            sendDuoSyncBroadcast(if (isPlaying) ACTION_DUO_PAUSE else ACTION_DUO_RESUME, null)
        }

        playerBarBinding.btnNext.setOnClickListener { 
            startService(Intent(this, MusicService::class.java).apply { 
                action = MusicService.ACTION_NEXT 
            })
            // Duo sync will be handled by MainActivity when it receives playback state change
        }
        
        playerBarBinding.btnPrevious.setOnClickListener { 
            startService(Intent(this, MusicService::class.java).apply { 
                action = MusicService.ACTION_PREVIOUS 
            })
            // Duo sync will be handled by MainActivity when it receives playback state change
        }
    }

    private fun openPlayerSheet() {
        val playerSheet = PlayerSheetFragment.newInstance()
        playerSheet.show(supportFragmentManager, PlayerSheetFragment.TAG)
    }

    private fun playSong(song: Song) {
        currentSong = song
        isPlaying = true
        binding.playerBarContainer.visibility = android.view.View.VISIBLE
        songAdapter.setCurrentPlayingSong(song.id)

        // Start playback via MusicService
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
        
        // Send Duo sync broadcast to MainActivity
        sendDuoSyncBroadcast(ACTION_DUO_PLAY, song)
    }

    private fun shufflePlay() {
        if (songs.isNotEmpty()) {
            val shuffled = songs.shuffled()
            playSong(shuffled.first())
        }
    }
    
    /**
     * Send broadcast to MainActivity for Duo sync
     */
    private fun sendDuoSyncBroadcast(action: String, song: Song?) {
        val intent = Intent(action)
        song?.let { intent.putExtra(EXTRA_SONG, it) }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun updatePlayerBarUI(song: Song) {
        playerBarBinding.tvPlayerTitle.text = song.title
        playerBarBinding.tvPlayerSubtitle.text = song.artist
        
        com.android.music.util.AlbumArtUtil.loadAlbumArtWithFallback(
            com.bumptech.glide.Glide.with(this),
            playerBarBinding.ivPlayerThumbnail,
            song,
            44
        )
        
        com.android.music.util.AlbumArtUtil.applyPlayerBarTheming(
            playerBarBinding.root,
            song
        )
    }

    private fun updatePlaybackUI(isPlaying: Boolean, song: Song?) {
        this.isPlaying = isPlaying
        playerBarBinding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        song?.let {
            currentSong = it
            updatePlayerBarUI(it)
            songAdapter.setCurrentPlayingSong(it.id)
            binding.playerBarContainer.visibility = android.view.View.VISIBLE
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
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playbackReceiver)
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SONGS = "extra_songs"
        const val EXTRA_SONG = "extra_song"
        
        // Duo sync broadcast actions
        const val ACTION_DUO_PLAY = "com.android.music.duo.ACTION_DUO_PLAY"
        const val ACTION_DUO_PAUSE = "com.android.music.duo.ACTION_DUO_PAUSE"
        const val ACTION_DUO_RESUME = "com.android.music.duo.ACTION_DUO_RESUME"

        fun start(context: Context, title: String, songs: List<Song>) {
            val intent = Intent(context, DuoSongsListActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putParcelableArrayListExtra(EXTRA_SONGS, ArrayList(songs))
            }
            context.startActivity(intent)
        }
    }
}

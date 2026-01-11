package com.android.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.music.R
import com.android.music.data.PlayCountManager
import com.android.music.data.model.Song
import com.android.music.equalizer.manager.EqualizerManager
import com.android.music.ui.activity.MainActivity

class MusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null
    private var mediaSession: MediaSessionCompat? = null
    private val handler = Handler(Looper.getMainLooper())
    private val binder = MusicBinder()
    
    // Equalizer manager for automatic settings application
    private lateinit var equalizerManager: EqualizerManager
    
    // Play count manager
    private lateinit var playCountManager: PlayCountManager

    // Playlist management within service for lock screen controls
    private var playlist: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var isRepeatOne: Boolean = false
    
    // Queue for "Add to Queue" functionality
    private val queue: MutableList<Song> = mutableListOf()

    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "music_player_channel"
    private val PREVIOUS_THRESHOLD_MS = 3000 // 3 seconds

    class MusicBinder : Binder()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        
        // Initialize equalizer manager
        equalizerManager = EqualizerManager.getInstance(this)
        
        // Initialize play count manager
        playCountManager = PlayCountManager.getInstance(this)
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resumeSong()
                }

                override fun onPause() {
                    pauseSong()
                }

                override fun onSkipToNext() {
                    playNextInternal()
                }

                override fun onSkipToPrevious() {
                    playPreviousInternal()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos.toInt())
                }

                override fun onStop() {
                    stopSong()
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    when (action) {
                        ACTION_TOGGLE_REPEAT -> toggleRepeat()
                        ACTION_STOP -> stopSong()
                    }
                }
            })
            isActive = true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val song = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_SONG, Song::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_SONG)
                }
                // Get playlist if provided
                val newPlaylist = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_PLAYLIST, Song::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(EXTRA_PLAYLIST)
                }
                newPlaylist?.let { playlist = it }
                song?.let { playSong(it) }
            }
            ACTION_PAUSE -> pauseSong()
            ACTION_RESUME -> resumeSong()
            ACTION_NEXT -> playNextInternal()
            ACTION_PREVIOUS -> playPreviousInternal()
            ACTION_TOGGLE_REPEAT -> toggleRepeat()
            ACTION_STOP -> stopSong()
            ACTION_SEEK -> {
                val position = intent.getIntExtra(EXTRA_POSITION, 0)
                seekTo(position)
            }
            ACTION_ADD_TO_QUEUE -> {
                val song = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_SONG, Song::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_SONG)
                }
                song?.let { addToQueue(it) }
            }
        }
        return START_STICKY
    }
    
    /**
     * Add a song to the queue
     */
    fun addToQueue(song: Song) {
        queue.add(song)
        broadcastQueueUpdate()
    }

    private fun broadcastQueueUpdate() {
        val intent = Intent(BROADCAST_QUEUE_UPDATE).apply {
            putExtra(EXTRA_QUEUE_SIZE, queue.size)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    fun playSong(song: Song) {
        currentSong = song
        currentIndex = playlist.indexOfFirst { it.id == song.id }
        if (currentIndex == -1 && playlist.isEmpty()) {
            playlist = listOf(song)
            currentIndex = 0
        }
        
        // Increment play count
        playCountManager.incrementPlayCount(song.id)
        
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(song.path)
                prepareAsync()
                setOnPreparedListener {
                    // Initialize equalizer with the new audio session
                    equalizerManager.initializeWithAudioSession(audioSessionId)
                    
                    start()
                    updateMediaSessionMetadata(song)
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    showNotification(song, true)
                    startProgressUpdates()
                    broadcastPlaybackState(true, song)
                    // Broadcast for Duo sync - include song info
                    broadcastSongChange(song)
                }
                setOnCompletionListener {
                    if (isRepeatOne) {
                        // Replay the same song
                        seekTo(0)
                        start()
                    } else {
                        playNextInternal()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    broadcastPlaybackState(false, song)
                    true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playNextInternal() {
        // First check if there are songs in the queue
        if (queue.isNotEmpty()) {
            val nextSong = queue.removeAt(0)
            broadcastQueueUpdate()
            playSong(nextSong)
            return
        }
        
        if (playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
            currentIndex++
            val nextSong = playlist[currentIndex]
            playSong(nextSong)
        } else {
            // End of playlist
            currentSong?.let {
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                showNotification(it, false)
                broadcastPlaybackState(false, it)
            }
        }
        broadcastAction(BROADCAST_NEXT)
    }

    private fun playPreviousInternal() {
        val currentPosition = mediaPlayer?.currentPosition ?: 0
        
        // If played more than 3 seconds, restart current song
        if (currentPosition > PREVIOUS_THRESHOLD_MS) {
            mediaPlayer?.seekTo(0)
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        } else {
            // Play previous song
            if (playlist.isNotEmpty() && currentIndex > 0) {
                currentIndex--
                val prevSong = playlist[currentIndex]
                playSong(prevSong)
            }
        }
        broadcastAction(BROADCAST_PREVIOUS)
    }

    private fun toggleRepeat() {
        isRepeatOne = !isRepeatOne
        currentSong?.let { showNotification(it, mediaPlayer?.isPlaying ?: false) }
        broadcastRepeatState()
    }

    private fun broadcastRepeatState() {
        val intent = Intent(BROADCAST_REPEAT_STATE).apply {
            putExtra(EXTRA_IS_REPEAT_ONE, isRepeatOne)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun pauseSong() {
        mediaPlayer?.pause()
        currentSong?.let { 
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            showNotification(it, false)
            broadcastPlaybackState(false, it)
        }
        stopProgressUpdates()
        // Broadcast for Duo sync
        broadcastAction(BROADCAST_PAUSE)
    }

    fun resumeSong() {
        mediaPlayer?.start()
        currentSong?.let { 
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            showNotification(it, true)
            broadcastPlaybackState(true, it)
        }
        startProgressUpdates()
        // Broadcast for Duo sync
        broadcastAction(BROADCAST_RESUME)
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updatePlaybackState(if (mediaPlayer?.isPlaying == true) 
            PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
        // Broadcast for Duo sync
        val intent = Intent(BROADCAST_SEEK).apply {
            putExtra(EXTRA_POSITION, position)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun stopSong() {
        stopProgressUpdates()
        val stoppedSong = currentSong
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentSong = null
        mediaSession?.isActive = false
        
        // Broadcast stop event to update UI
        stoppedSong?.let {
            broadcastPlaybackState(false, it)
        }
        broadcastAction(BROADCAST_STOP)
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateMediaSessionMetadata(song: Song) {
        val albumArt = getAlbumArt(song.albumArtUri)
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .build()
        )
    }

    private fun updatePlaybackState(state: Int) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val playbackSpeed = if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f
        
        // Create repeat custom action
        val repeatIcon = if (isRepeatOne) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        val repeatLabel = if (isRepeatOne) "Repeat One" else "Repeat Off"
        val repeatAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_REPEAT,
            repeatLabel,
            repeatIcon
        ).build()
        
        // Create close custom action
        val closeAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_STOP,
            "Close",
            R.drawable.ic_close
        ).build()
        
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, position, playbackSpeed)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP
                )
                .addCustomAction(repeatAction)
                .addCustomAction(closeAction)
                .build()
        )
    }

    private fun startProgressUpdates() {
        handler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    broadcastProgress(player.currentPosition, player.duration)
                    // Update playback state to refresh seek bar position
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    private fun broadcastProgress(position: Int, duration: Int) {
        val intent = Intent(BROADCAST_PROGRESS).apply {
            putExtra(EXTRA_POSITION, position)
            putExtra(EXTRA_DURATION, duration)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastPlaybackState(isPlaying: Boolean, song: Song) {
        val intent = Intent(BROADCAST_PLAYBACK_STATE).apply {
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_SONG, song)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        
        // Also broadcast to widget
        sendBroadcast(intent)
        
        // Update widget state
        com.android.music.widget.MusicWidgetProvider.updateWidgetState(this, song, isPlaying)
    }

    private fun broadcastAction(action: String) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(action))
    }
    
    private fun broadcastSongChange(song: Song) {
        val intent = Intent(BROADCAST_SONG_CHANGE).apply {
            putExtra(EXTRA_SONG, song)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun getAlbumArt(albumArtUri: Uri?): Bitmap? {
        return try {
            currentSong?.let { song ->
                com.android.music.util.AlbumArtUtil.getAlbumArt(
                    contentResolver,
                    albumArtUri,
                    song.path
                )
            }
        } catch (_: Exception) {
            null
        }
    }


    private fun showNotification(song: Song, isPlaying: Boolean) {
        val albumArt = getAlbumArt(song.albumArtUri)
        
        // Content intent - opens app when notification is tapped
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build MediaStyle notification
        // On Android 13+, buttons are derived from PlaybackState (including custom actions)
        // On older versions, we add notification actions as fallback
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setLargeIcon(albumArt)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(createPendingIntent(ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(isPlaying)
        
        // Add fallback actions for pre-Android 13
        val repeatIcon = if (isRepeatOne) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        builder.addAction(repeatIcon, "Repeat", createPendingIntent(ACTION_TOGGLE_REPEAT))
        builder.addAction(R.drawable.ic_skip_previous, "Previous", createPendingIntent(ACTION_PREVIOUS))
        
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_RESUME
        builder.addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", createPendingIntent(playPauseAction))
        
        builder.addAction(R.drawable.ic_skip_next, "Next", createPendingIntent(ACTION_NEXT))
        builder.addAction(R.drawable.ic_close, "Close", createPendingIntent(ACTION_STOP))
        
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(1, 2, 3) // prev, play/pause, next in compact
        )

        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music player notifications"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
    }

    companion object {
        const val ACTION_PLAY = "com.android.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.android.music.ACTION_PAUSE"
        const val ACTION_RESUME = "com.android.music.ACTION_RESUME"
        const val ACTION_NEXT = "com.android.music.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.android.music.ACTION_PREVIOUS"
        const val ACTION_TOGGLE_REPEAT = "com.android.music.ACTION_TOGGLE_REPEAT"
        const val ACTION_STOP = "com.android.music.ACTION_STOP"
        const val ACTION_SEEK = "com.android.music.ACTION_SEEK"
        const val ACTION_ADD_TO_QUEUE = "com.android.music.ACTION_ADD_TO_QUEUE"
        const val EXTRA_SONG = "extra_song"
        const val EXTRA_PLAYLIST = "extra_playlist"
        const val EXTRA_SHUFFLE = "extra_shuffle"
        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_IS_REPEAT_ONE = "extra_is_repeat_one"
        const val EXTRA_QUEUE_SIZE = "extra_queue_size"
        const val BROADCAST_PROGRESS = "com.android.music.BROADCAST_PROGRESS"
        const val BROADCAST_PLAYBACK_STATE = "com.android.music.BROADCAST_PLAYBACK_STATE"
        const val BROADCAST_NEXT = "com.android.music.BROADCAST_NEXT"
        const val BROADCAST_PREVIOUS = "com.android.music.BROADCAST_PREVIOUS"
        const val BROADCAST_REPEAT_STATE = "com.android.music.BROADCAST_REPEAT_STATE"
        const val BROADCAST_STOP = "com.android.music.BROADCAST_STOP"
        const val BROADCAST_PAUSE = "com.android.music.BROADCAST_PAUSE"
        const val BROADCAST_RESUME = "com.android.music.BROADCAST_RESUME"
        const val BROADCAST_SEEK = "com.android.music.BROADCAST_SEEK"
        const val BROADCAST_SONG_CHANGE = "com.android.music.BROADCAST_SONG_CHANGE"
        const val BROADCAST_QUEUE_UPDATE = "com.android.music.BROADCAST_QUEUE_UPDATE"
    }
}

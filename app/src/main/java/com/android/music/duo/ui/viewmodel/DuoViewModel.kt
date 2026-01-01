package com.android.music.duo.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.music.data.model.Song
import com.android.music.data.model.Video
import com.android.music.data.model.Artist
import com.android.music.data.model.Album
import com.android.music.data.model.Folder
import com.android.music.duo.data.model.*
import com.android.music.duo.data.repository.DuoRepository
import com.android.music.duo.webrtc.SignalingManager
import com.android.music.duo.webrtc.WebRTCRepository
import com.android.music.duo.webrtc.model.WebRTCConnectionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DuoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DuoRepository(application)
    private val webRTCRepository = WebRTCRepository(application)
    
    // Flag to prevent feedback loops when processing remote commands
    private var isProcessingRemoteCommand = false

    // WiFi Direct connection state
    val connectionState: StateFlow<DuoConnectionState> = repository.connectionState
    val isHost: StateFlow<Boolean> = repository.isHost
    val signalStrength: StateFlow<SignalStrength> = repository.signalStrength
    val discoveredDevices: StateFlow<List<DuoDevice>> = repository.discoveredDevices
    val isWifiP2pEnabled: StateFlow<Boolean> = repository.isWifiP2pEnabled

    // WebRTC connection state
    val webRTCConnectionState: StateFlow<WebRTCConnectionState> = webRTCRepository.connectionState
    val incomingWebRTCOffer: StateFlow<SignalingManager.IncomingOffer?> = webRTCRepository.incomingRequest

    // Songs - combine common songs from both WiFi Direct and WebRTC
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    
    // Combined common songs from both connection types
    private val _combinedCommonSongs = MutableStateFlow<List<Song>>(emptyList())
    val commonSongs: StateFlow<List<Song>> = _combinedCommonSongs.asStateFlow()

    private val _filteredSongs = MutableStateFlow<List<Song>>(emptyList())
    val filteredSongs: StateFlow<List<Song>> = _filteredSongs.asStateFlow()
    
    // Videos - derived from common songs
    private val _commonVideos = MutableStateFlow<List<Video>>(emptyList())
    val commonVideos: StateFlow<List<Video>> = _commonVideos.asStateFlow()
    
    private val _filteredVideos = MutableStateFlow<List<Video>>(emptyList())
    val filteredVideos: StateFlow<List<Video>> = _filteredVideos.asStateFlow()
    
    // Artists - derived from common songs
    private val _commonArtists = MutableStateFlow<List<Artist>>(emptyList())
    val commonArtists: StateFlow<List<Artist>> = _commonArtists.asStateFlow()
    
    private val _filteredArtists = MutableStateFlow<List<Artist>>(emptyList())
    val filteredArtists: StateFlow<List<Artist>> = _filteredArtists.asStateFlow()
    
    // Albums - derived from common songs
    private val _commonAlbums = MutableStateFlow<List<Album>>(emptyList())
    val commonAlbums: StateFlow<List<Album>> = _commonAlbums.asStateFlow()
    
    private val _filteredAlbums = MutableStateFlow<List<Album>>(emptyList())
    val filteredAlbums: StateFlow<List<Album>> = _filteredAlbums.asStateFlow()
    
    // Folders - derived from common songs (folders containing common songs)
    private val _commonFolders = MutableStateFlow<List<Folder>>(emptyList())
    val commonFolders: StateFlow<List<Folder>> = _commonFolders.asStateFlow()
    
    private val _filteredFolders = MutableStateFlow<List<Folder>>(emptyList())
    val filteredFolders: StateFlow<List<Folder>> = _filteredFolders.asStateFlow()
    
    // Local media for reference
    private val _localVideos = MutableStateFlow<List<Video>>(emptyList())
    private val _localArtists = MutableStateFlow<List<Artist>>(emptyList())
    private val _localAlbums = MutableStateFlow<List<Album>>(emptyList())
    private val _localFolders = MutableStateFlow<List<Folder>>(emptyList())

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Sort
    private val _sortOption = MutableStateFlow(DuoSortOption.NAME)
    val sortOption: StateFlow<DuoSortOption> = _sortOption.asStateFlow()

    // Current playing
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Playback controls
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    // Events
    private val _showConnectionSheet = MutableStateFlow(false)
    val showConnectionSheet: StateFlow<Boolean> = _showConnectionSheet.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()
    
    // Playback event - emits song to play (observed by Activity to start MusicService)
    private val _playSongEvent = MutableSharedFlow<Pair<Song, List<Song>>>()
    val playSongEvent: SharedFlow<Pair<Song, List<Song>>> = _playSongEvent.asSharedFlow()
    
    // Pause event
    private val _pauseEvent = MutableSharedFlow<Unit>()
    val pauseEvent: SharedFlow<Unit> = _pauseEvent.asSharedFlow()
    
    // Resume event
    private val _resumeEvent = MutableSharedFlow<Unit>()
    val resumeEvent: SharedFlow<Unit> = _resumeEvent.asSharedFlow()
    
    // Seek event
    private val _seekEvent = MutableSharedFlow<Long>()
    val seekEvent: SharedFlow<Long> = _seekEvent.asSharedFlow()

    // Incoming commands (from WiFi Direct)
    val incomingCommand: SharedFlow<DuoCommand> = repository.incomingCommand

    // Device info
    val deviceName: String = Build.MODEL
    val userId: String = "DUO${System.currentTimeMillis() % 10000}" // Legacy - now using WebRTC Duo ID

    init {
        initializeWebRTC()
        observeCommonSongsFromBothSources()
        observeIncomingCommands()
        observeWebRTCEvents()
    }
    
    /**
     * Observe common songs from both WiFi Direct and WebRTC repositories
     * and update the filtered songs accordingly
     */
    private fun observeCommonSongsFromBothSources() {
        // Observe common songs from WiFi Direct repository
        viewModelScope.launch {
            repository.commonSongs.collect { songs ->
                android.util.Log.d("DuoViewModel", "WiFi Direct commonSongs updated: ${songs.size}")
                if (songs.isNotEmpty()) {
                    _combinedCommonSongs.value = songs
                    updateFilteredSongs(songs)
                    updateAllCommonMedia()
                    _toastMessage.emit("Found ${songs.size} common songs!")
                }
            }
        }
        
        // Observe common songs from WebRTC repository
        viewModelScope.launch {
            webRTCRepository.commonSongs.collect { songs ->
                android.util.Log.d("DuoViewModel", "WebRTC commonSongs updated: ${songs.size}")
                if (songs.isNotEmpty()) {
                    _combinedCommonSongs.value = songs
                    updateFilteredSongs(songs)
                    updateAllCommonMedia()
                    _toastMessage.emit("Found ${songs.size} common songs!")
                }
            }
        }
        
        // Observe search and sort changes
        viewModelScope.launch {
            combine(
                _combinedCommonSongs,
                _searchQuery,
                _sortOption
            ) { songs, query, sort ->
                android.util.Log.d("DuoViewModel", "Filter/sort changed: ${songs.size} songs, query='$query', sort=$sort")
                filterAndSortSongs(songs, query, sort)
            }.collect { filtered ->
                android.util.Log.d("DuoViewModel", "Setting filteredSongs: ${filtered.size}")
                _filteredSongs.value = filtered
            }
        }
    }
    
    /**
     * Update all common media types (videos, artists, albums, folders)
     * Called when common songs change or when local media is set
     */
    private fun updateAllCommonMedia() {
        android.util.Log.d("DuoViewModel", "updateAllCommonMedia called - commonSongs: ${_combinedCommonSongs.value.size}, localArtists: ${_localArtists.value.size}, localAlbums: ${_localAlbums.value.size}, localFolders: ${_localFolders.value.size}, localVideos: ${_localVideos.value.size}")
        updateCommonVideos()
        updateCommonArtists()
        updateCommonAlbums()
        updateCommonFolders()
    }
    
    private fun initializeWebRTC() {
        viewModelScope.launch {
            try {
                val duoId = webRTCRepository.initialize()
                android.util.Log.d("DuoViewModel", "WebRTC initialized with Duo ID: $duoId")
            } catch (e: Exception) {
                android.util.Log.e("DuoViewModel", "Failed to initialize WebRTC", e)
            }
        }
    }
    
    /**
     * Get my Duo ID
     */
    suspend fun getMyDuoId(): String {
        return webRTCRepository.getMyDuoId() ?: webRTCRepository.initialize()
    }
    
    /**
     * Format last seen timestamp
     */
    fun formatLastSeen(timestamp: Long): String {
        return webRTCRepository.formatLastSeen(timestamp)
    }
    
    /**
     * Connect to partner by Duo ID (WebRTC)
     */
    fun connectByDuoId(partnerId: String) {
        viewModelScope.launch {
            webRTCRepository.connectToPartner(partnerId)
        }
    }
    
    /**
     * Request notification when partner comes online
     */
    fun requestNotifyWhenOnline(partnerId: String) {
        viewModelScope.launch {
            val success = webRTCRepository.requestNotifyWhenOnline(partnerId)
            if (success) {
                _toastMessage.emit("You'll be notified when they come online")
            } else {
                _toastMessage.emit("Failed to set notification")
            }
        }
    }
    
    /**
     * Accept incoming WebRTC connection offer
     */
    fun acceptIncomingOffer(offer: SignalingManager.IncomingOffer) {
        viewModelScope.launch {
            webRTCRepository.acceptIncomingRequest(offer)
        }
    }
    
    /**
     * Reject incoming WebRTC connection offer
     */
    fun rejectIncomingOffer(offer: SignalingManager.IncomingOffer) {
        webRTCRepository.rejectIncomingRequest(offer)
    }
    
    private fun observeWebRTCEvents() {
        // Observe WebRTC events
        viewModelScope.launch {
            webRTCRepository.events.collect { event ->
                when (event) {
                    is WebRTCRepository.WebRTCEvent.Error -> {
                        _toastMessage.emit(event.message)
                    }
                    is WebRTCRepository.WebRTCEvent.PartnerOffline -> {
                        // UI will handle this via state
                    }
                    is WebRTCRepository.WebRTCEvent.Connected -> {
                        _toastMessage.emit("Connected via Internet!")
                    }
                    is WebRTCRepository.WebRTCEvent.Disconnected -> {
                        _toastMessage.emit("Disconnected")
                    }
                    is WebRTCRepository.WebRTCEvent.IncomingRequest -> {
                        _toastMessage.emit("${event.fromDeviceName} wants to connect")
                    }
                }
            }
        }
        
        // Observe WebRTC incoming commands
        viewModelScope.launch {
            webRTCRepository.incomingCommand.collect { command ->
                handleIncomingCommand(command)
            }
        }
    }

    private fun updateFilteredSongs(songs: List<Song>) {
        val filtered = filterAndSortSongs(songs, _searchQuery.value, _sortOption.value)
        android.util.Log.d("DuoViewModel", "updateFilteredSongs: ${filtered.size}")
        _filteredSongs.value = filtered
    }
    
    private fun filterAndSortSongs(songs: List<Song>, query: String, sort: DuoSortOption): List<Song> {
        var filtered = if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
        
        filtered = when (sort) {
            DuoSortOption.NAME -> filtered.sortedBy { it.title.lowercase() }
            DuoSortOption.TIME -> filtered.sortedByDescending { it.dateAdded }
            DuoSortOption.DURATION -> filtered.sortedByDescending { it.duration }
        }
        
        return filtered
    }

    private fun observeIncomingCommands() {
        viewModelScope.launch {
            incomingCommand.collect { command ->
                handleIncomingCommand(command)
            }
        }
    }

    private fun handleIncomingCommand(command: DuoCommand) {
        android.util.Log.d("DuoViewModel", "Handling incoming command: $command")
        
        isProcessingRemoteCommand = true
        
        when (command) {
            is DuoCommand.Play -> {
                // Try to find song from appropriate repository
                val song = if (webRTCRepository.isConnected()) {
                    webRTCRepository.findSongByHash(command.songHash)
                } else {
                    repository.findSongByHash(command.songHash)
                }
                android.util.Log.d("DuoViewModel", "Play command received, hash=${command.songHash}, found song=${song?.title}")
                song?.let {
                    _currentSong.value = it
                    _isPlaying.value = true
                    viewModelScope.launch {
                        val playlist = filteredSongs.value.ifEmpty { listOf(it) }
                        android.util.Log.d("DuoViewModel", "Emitting playSongEvent for ${it.title}")
                        _playSongEvent.emit(Pair(it, playlist))
                        kotlinx.coroutines.delay(500)
                        isProcessingRemoteCommand = false
                    }
                } ?: run {
                    isProcessingRemoteCommand = false
                    viewModelScope.launch {
                        _toastMessage.emit("Song not found on this device")
                    }
                }
            }
            is DuoCommand.Pause -> {
                android.util.Log.d("DuoViewModel", "Pause command received")
                _isPlaying.value = false
                viewModelScope.launch {
                    _pauseEvent.emit(Unit)
                    kotlinx.coroutines.delay(500)
                    isProcessingRemoteCommand = false
                }
            }
            is DuoCommand.Resume -> {
                android.util.Log.d("DuoViewModel", "Resume command received")
                _isPlaying.value = true
                viewModelScope.launch {
                    _resumeEvent.emit(Unit)
                    kotlinx.coroutines.delay(500)
                    isProcessingRemoteCommand = false
                }
            }
            is DuoCommand.Seek -> {
                android.util.Log.d("DuoViewModel", "Seek command received: ${command.position}")
                viewModelScope.launch {
                    _seekEvent.emit(command.position)
                    kotlinx.coroutines.delay(500)
                    isProcessingRemoteCommand = false
                }
            }
            is DuoCommand.Next -> {
                android.util.Log.d("DuoViewModel", "Next command received")
                val songs = filteredSongs.value
                val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
                if (currentIndex >= 0 && currentIndex < songs.size - 1) {
                    val nextSong = songs[currentIndex + 1]
                    _currentSong.value = nextSong
                    _isPlaying.value = true
                    viewModelScope.launch {
                        _playSongEvent.emit(Pair(nextSong, songs))
                        kotlinx.coroutines.delay(500)
                        isProcessingRemoteCommand = false
                    }
                } else if (_repeatMode.value == RepeatMode.ALL && songs.isNotEmpty()) {
                    val firstSong = songs.first()
                    _currentSong.value = firstSong
                    _isPlaying.value = true
                    viewModelScope.launch {
                        _playSongEvent.emit(Pair(firstSong, songs))
                        kotlinx.coroutines.delay(500)
                        isProcessingRemoteCommand = false
                    }
                } else {
                    isProcessingRemoteCommand = false
                }
            }
            is DuoCommand.Previous -> {
                android.util.Log.d("DuoViewModel", "Previous command received")
                val songs = filteredSongs.value
                val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
                if (currentIndex > 0) {
                    val prevSong = songs[currentIndex - 1]
                    _currentSong.value = prevSong
                    _isPlaying.value = true
                    viewModelScope.launch {
                        _playSongEvent.emit(Pair(prevSong, songs))
                        kotlinx.coroutines.delay(500)
                        isProcessingRemoteCommand = false
                    }
                } else if (_repeatMode.value == RepeatMode.ALL && songs.isNotEmpty()) {
                    val lastSong = songs.last()
                    _currentSong.value = lastSong
                    _isPlaying.value = true
                    viewModelScope.launch {
                        _playSongEvent.emit(Pair(lastSong, songs))
                        kotlinx.coroutines.delay(500)
                        isProcessingRemoteCommand = false
                    }
                } else {
                    isProcessingRemoteCommand = false
                }
            }
            is DuoCommand.SetShuffle -> {
                android.util.Log.d("DuoViewModel", "SetShuffle command received: ${command.enabled}")
                _shuffleEnabled.value = command.enabled
                isProcessingRemoteCommand = false
            }
            is DuoCommand.SetRepeat -> {
                android.util.Log.d("DuoViewModel", "SetRepeat command received: ${command.mode}")
                _repeatMode.value = command.mode
                isProcessingRemoteCommand = false
            }
            is DuoCommand.AddToQueue -> {
                android.util.Log.d("DuoViewModel", "AddToQueue command received")
                isProcessingRemoteCommand = false
            }
            is DuoCommand.ClearQueue -> {
                android.util.Log.d("DuoViewModel", "ClearQueue command received")
                isProcessingRemoteCommand = false
            }
            is DuoCommand.RequestDisconnect -> {
                isProcessingRemoteCommand = false
                viewModelScope.launch {
                    _toastMessage.emit("Partner disconnected")
                }
            }
            else -> {
                android.util.Log.d("DuoViewModel", "Unknown command: $command")
                isProcessingRemoteCommand = false
            }
        }
    }

    fun setLocalSongs(songs: List<Song>) {
        android.util.Log.d("DuoViewModel", "setLocalSongs called with ${songs.size} songs")
        _allSongs.value = songs
        repository.setLocalSongs(songs)
        webRTCRepository.setLocalSongs(songs) // Also set for WebRTC
        
        viewModelScope.launch {
            _toastMessage.emit("Local songs loaded: ${songs.size}")
        }
    }
    
    fun setLocalVideos(videos: List<Video>) {
        android.util.Log.d("DuoViewModel", "setLocalVideos called with ${videos.size} videos")
        _localVideos.value = videos
        // Update common videos if we already have common songs
        if (_combinedCommonSongs.value.isNotEmpty()) {
            updateCommonVideos()
        }
    }
    
    fun setLocalArtists(artists: List<Artist>) {
        android.util.Log.d("DuoViewModel", "setLocalArtists called with ${artists.size} artists")
        _localArtists.value = artists
        // Update common artists if we already have common songs
        if (_combinedCommonSongs.value.isNotEmpty()) {
            updateCommonArtists()
        }
    }
    
    fun setLocalAlbums(albums: List<Album>) {
        android.util.Log.d("DuoViewModel", "setLocalAlbums called with ${albums.size} albums")
        _localAlbums.value = albums
        // Update common albums if we already have common songs
        if (_combinedCommonSongs.value.isNotEmpty()) {
            updateCommonAlbums()
        }
    }
    
    fun setLocalFolders(folders: List<Folder>) {
        android.util.Log.d("DuoViewModel", "setLocalFolders called with ${folders.size} folders")
        _localFolders.value = folders
        // Update common folders if we already have common songs
        if (_combinedCommonSongs.value.isNotEmpty()) {
            updateCommonFolders()
        }
    }
    
    /**
     * Get songs for a specific artist from common songs
     */
    fun getSongsForArtist(artistName: String): List<Song> {
        return _combinedCommonSongs.value.filter { 
            it.artist.equals(artistName, ignoreCase = true) 
        }
    }
    
    /**
     * Get songs for a specific album from common songs
     */
    fun getSongsForAlbum(albumTitle: String, artistName: String): List<Song> {
        return _combinedCommonSongs.value.filter { 
            it.album.equals(albumTitle, ignoreCase = true) &&
            it.artist.equals(artistName, ignoreCase = true)
        }
    }
    
    /**
     * Get songs for a specific folder from common songs
     */
    fun getSongsForFolder(folderPath: String): List<Song> {
        val folderPathWithSep = if (folderPath.endsWith("/")) folderPath else "${folderPath}/"
        return _combinedCommonSongs.value.filter { song ->
            val songDir = song.path.substringBeforeLast("/") + "/"
            songDir.startsWith(folderPathWithSep) || songDir == folderPathWithSep.dropLast(1) + "/"
        }
    }
    
    /**
     * Update common videos based on common songs
     * Videos are considered common if they match by title and artist
     */
    private fun updateCommonVideos() {
        val commonSongs = _combinedCommonSongs.value
        val localVideos = _localVideos.value
        
        android.util.Log.d("DuoViewModel", "updateCommonVideos: commonSongs=${commonSongs.size}, localVideos=${localVideos.size}")
        
        if (commonSongs.isEmpty() || localVideos.isEmpty()) {
            _commonVideos.value = emptyList()
            _filteredVideos.value = emptyList()
            return
        }
        
        // Match videos by title (case-insensitive)
        val commonVideoTitles = commonSongs.map { it.title.lowercase() }.toSet()
        val common = localVideos.filter { video ->
            commonVideoTitles.contains(video.title.lowercase())
        }
        
        _commonVideos.value = common
        _filteredVideos.value = common
        android.util.Log.d("DuoViewModel", "Common videos updated: ${common.size}")
    }
    
    /**
     * Update common artists based on common songs
     * Artists are included if they have at least one common song
     */
    private fun updateCommonArtists() {
        val commonSongs = _combinedCommonSongs.value
        val localArtists = _localArtists.value
        
        android.util.Log.d("DuoViewModel", "updateCommonArtists: commonSongs=${commonSongs.size}, localArtists=${localArtists.size}")
        
        if (commonSongs.isEmpty() || localArtists.isEmpty()) {
            _commonArtists.value = emptyList()
            _filteredArtists.value = emptyList()
            return
        }
        
        // Get unique artist names from common songs
        val commonArtistNames = commonSongs.map { it.artist.lowercase() }.toSet()
        android.util.Log.d("DuoViewModel", "Common artist names: $commonArtistNames")
        
        // Filter local artists that have common songs
        val common = localArtists.filter { artist ->
            commonArtistNames.contains(artist.name.lowercase())
        }.map { artist ->
            // Update song count to reflect only common songs
            val songCount = commonSongs.count { it.artist.equals(artist.name, ignoreCase = true) }
            artist.copy(songCount = songCount)
        }
        
        _commonArtists.value = common
        _filteredArtists.value = common
        android.util.Log.d("DuoViewModel", "Common artists updated: ${common.size}")
    }
    
    /**
     * Update common albums based on common songs
     * Albums are included if they have at least one common song
     */
    private fun updateCommonAlbums() {
        val commonSongs = _combinedCommonSongs.value
        val localAlbums = _localAlbums.value
        
        android.util.Log.d("DuoViewModel", "updateCommonAlbums: commonSongs=${commonSongs.size}, localAlbums=${localAlbums.size}")
        
        if (commonSongs.isEmpty() || localAlbums.isEmpty()) {
            _commonAlbums.value = emptyList()
            _filteredAlbums.value = emptyList()
            return
        }
        
        // Get unique album titles from common songs
        val commonAlbumTitles = commonSongs.map { "${it.album.lowercase()}|${it.artist.lowercase()}" }.toSet()
        
        // Filter local albums that have common songs
        val common = localAlbums.filter { album ->
            commonAlbumTitles.contains("${album.title.lowercase()}|${album.artist.lowercase()}")
        }.map { album ->
            // Update song count to reflect only common songs
            val songCount = commonSongs.count { 
                it.album.equals(album.title, ignoreCase = true) && 
                it.artist.equals(album.artist, ignoreCase = true)
            }
            album.copy(songCount = songCount)
        }
        
        _commonAlbums.value = common
        _filteredAlbums.value = common
        android.util.Log.d("DuoViewModel", "Common albums updated: ${common.size}")
    }
    
    /**
     * Update common folders based on common songs
     * Folders are included if they contain at least one common song
     * Note: Folder names may differ, so we match by the songs they contain
     */
    private fun updateCommonFolders() {
        val commonSongs = _combinedCommonSongs.value
        val localFolders = _localFolders.value
        
        android.util.Log.d("DuoViewModel", "updateCommonFolders: commonSongs=${commonSongs.size}, localFolders=${localFolders.size}")
        
        if (commonSongs.isEmpty() || localFolders.isEmpty()) {
            _commonFolders.value = emptyList()
            _filteredFolders.value = emptyList()
            return
        }
        
        // Filter folders that contain at least one common song
        val common = localFolders.filter { folder ->
            // Ensure folder path ends with separator for proper matching
            val folderPathWithSep = if (folder.path.endsWith("/")) folder.path else "${folder.path}/"
            // Check if any common song is in this folder
            commonSongs.any { song ->
                val songDir = song.path.substringBeforeLast("/") + "/"
                songDir.startsWith(folderPathWithSep) || songDir == folderPathWithSep.dropLast(1) + "/"
            }
        }.map { folder ->
            // Update song count to reflect only common songs in this folder
            val folderPathWithSep = if (folder.path.endsWith("/")) folder.path else "${folder.path}/"
            val songCount = commonSongs.count { song ->
                val songDir = song.path.substringBeforeLast("/") + "/"
                songDir.startsWith(folderPathWithSep) || songDir == folderPathWithSep.dropLast(1) + "/"
            }
            folder.copy(songCount = songCount)
        }
        
        _commonFolders.value = common
        _filteredFolders.value = common
        android.util.Log.d("DuoViewModel", "Common folders updated: ${common.size}")
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: DuoSortOption) {
        _sortOption.value = option
    }

    fun showConnectionSheet() {
        _showConnectionSheet.value = true
    }

    fun hideConnectionSheet() {
        _showConnectionSheet.value = false
    }

    fun startDiscovery() {
        repository.startDiscovery(
            onSuccess = {
                viewModelScope.launch {
                    _toastMessage.emit("Searching for devices...")
                }
            },
            onFailure = { error ->
                viewModelScope.launch {
                    _toastMessage.emit(error)
                }
            }
        )
    }

    fun stopDiscovery() {
        repository.stopDiscovery()
    }

    fun connectToDevice(device: DuoDevice) {
        repository.connectToDevice(
            device = device,
            onSuccess = {
                viewModelScope.launch {
                    _toastMessage.emit("Connecting to ${device.deviceName}...")
                    hideConnectionSheet()
                }
            },
            onFailure = { error ->
                viewModelScope.launch {
                    _toastMessage.emit(error)
                }
            }
        )
    }

    fun disconnect() {
        // Disconnect both WiFi Direct and WebRTC
        repository.disconnect()
        webRTCRepository.disconnect()
        _currentSong.value = null
        _isPlaying.value = false
        _combinedCommonSongs.value = emptyList()
        _filteredSongs.value = emptyList()
        viewModelScope.launch {
            _toastMessage.emit("Disconnected")
        }
    }
    
    fun resyncLibrary() {
        // Resync via appropriate connection type
        if (webRTCRepository.isConnected()) {
            webRTCRepository.resyncLibrary()
        } else {
            repository.resyncLibrary()
        }
        viewModelScope.launch {
            _toastMessage.emit("Syncing library...")
        }
    }

    fun cancelConnection() {
        repository.cancelConnection()
        viewModelScope.launch {
            _toastMessage.emit("Connection cancelled")
        }
    }
    
    /**
     * Check if connected via any method (WiFi Direct or WebRTC)
     */
    fun isConnected(): Boolean {
        return connectionState.value is DuoConnectionState.Connected ||
                webRTCRepository.isConnected()
    }
    
    /**
     * Check if a song is in the common songs list (available to both partners)
     */
    fun isSongInCommonList(song: Song): Boolean {
        return _combinedCommonSongs.value.any { it.id == song.id }
    }

    // Playback controls

    fun playSong(song: Song) {
        _currentSong.value = song
        _isPlaying.value = true
        
        viewModelScope.launch {
            val playlist = filteredSongs.value.ifEmpty { listOf(song) }
            _playSongEvent.emit(Pair(song, playlist))
        }
        
        // Send command to partner device via appropriate channel
        viewModelScope.launch {
            val songHash = repository.getSongHash(song)
            android.util.Log.d("DuoViewModel", "Sending play command for ${song.title}, hash=$songHash")
            
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendPlay(songHash)
            } else {
                repository.sendPlay(songHash)
            }
        }
    }

    fun pause() {
        android.util.Log.d("DuoViewModel", "pause() called")
        _isPlaying.value = false
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendPause()
            } else {
                repository.sendPause()
            }
        }
    }

    fun resume() {
        android.util.Log.d("DuoViewModel", "resume() called")
        _isPlaying.value = true
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendResume()
            } else {
                repository.sendResume()
            }
        }
    }

    fun seekTo(position: Long) {
        android.util.Log.d("DuoViewModel", "seekTo($position) called")
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendSeek(position)
            } else {
                repository.sendSeek(position)
            }
        }
    }
    
    fun syncPause() {
        if (isProcessingRemoteCommand) {
            android.util.Log.d("DuoViewModel", "syncPause() skipped - processing remote command")
            return
        }
        android.util.Log.d("DuoViewModel", "syncPause() called - sending to partner only")
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendPause()
            } else {
                repository.sendPause()
            }
        }
    }
    
    fun syncResume() {
        if (isProcessingRemoteCommand) {
            android.util.Log.d("DuoViewModel", "syncResume() skipped - processing remote command")
            return
        }
        android.util.Log.d("DuoViewModel", "syncResume() called - sending to partner only")
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendResume()
            } else {
                repository.sendResume()
            }
        }
    }
    
    fun syncSeek(position: Long) {
        if (isProcessingRemoteCommand) {
            android.util.Log.d("DuoViewModel", "syncSeek() skipped - processing remote command")
            return
        }
        android.util.Log.d("DuoViewModel", "syncSeek($position) called - sending to partner only")
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendSeek(position)
            } else {
                repository.sendSeek(position)
            }
        }
    }
    
    fun syncSongChange(song: Song) {
        if (isProcessingRemoteCommand) {
            android.util.Log.d("DuoViewModel", "syncSongChange() skipped - processing remote command")
            return
        }
        android.util.Log.d("DuoViewModel", "syncSongChange(${song.title}) called - sending to partner only")
        _currentSong.value = song
        _isPlaying.value = true
        viewModelScope.launch {
            val songHash = repository.getSongHash(song)
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendPlay(songHash)
            } else {
                repository.sendPlay(songHash)
            }
        }
    }

    fun playNext() {
        val songs = filteredSongs.value
        val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
        val nextSong = if (currentIndex >= 0 && currentIndex < songs.size - 1) {
            songs[currentIndex + 1]
        } else if (_repeatMode.value == RepeatMode.ALL && songs.isNotEmpty()) {
            songs.first()
        } else {
            null
        }
        
        nextSong?.let { song ->
            _currentSong.value = song
            _isPlaying.value = true
            viewModelScope.launch {
                _playSongEvent.emit(Pair(song, songs))
            }
            
            viewModelScope.launch {
                val songHash = repository.getSongHash(song)
                android.util.Log.d("DuoViewModel", "Sending play (next) command for ${song.title}")
                if (webRTCRepository.isConnected()) {
                    webRTCRepository.sendPlay(songHash)
                } else {
                    repository.sendPlay(songHash)
                }
            }
        }
    }

    fun playPrevious() {
        val songs = filteredSongs.value
        val currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
        val prevSong = if (currentIndex > 0) {
            songs[currentIndex - 1]
        } else if (_repeatMode.value == RepeatMode.ALL && songs.isNotEmpty()) {
            songs.last()
        } else {
            null
        }
        
        prevSong?.let { song ->
            _currentSong.value = song
            _isPlaying.value = true
            viewModelScope.launch {
                _playSongEvent.emit(Pair(song, songs))
            }
            
            viewModelScope.launch {
                val songHash = repository.getSongHash(song)
                android.util.Log.d("DuoViewModel", "Sending play (previous) command for ${song.title}")
                if (webRTCRepository.isConnected()) {
                    webRTCRepository.sendPlay(songHash)
                } else {
                    repository.sendPlay(songHash)
                }
            }
        }
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendShuffle(_shuffleEnabled.value)
            } else {
                repository.sendShuffle(_shuffleEnabled.value)
            }
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        viewModelScope.launch {
            if (webRTCRepository.isConnected()) {
                webRTCRepository.sendRepeat(_repeatMode.value)
            } else {
                repository.sendRepeat(_repeatMode.value)
            }
        }
    }

    fun shufflePlay() {
        val songs = filteredSongs.value.shuffled()
        if (songs.isNotEmpty()) {
            _shuffleEnabled.value = true
            playSong(songs.first())
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.cleanup()
        webRTCRepository.cleanup()
    }
}

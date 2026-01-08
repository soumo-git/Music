package com.android.music.ui.viewmodel

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.music.data.PlayCountManager
import com.android.music.data.model.Album
import com.android.music.data.model.Artist
import com.android.music.data.model.Folder
import com.android.music.data.model.Song
import com.android.music.data.model.SortOption
import com.android.music.data.model.Video
import com.android.music.data.repository.MusicRepository
import com.android.music.service.MusicService
import kotlinx.coroutines.launch
import java.io.File

class MusicViewModel : ViewModel() {

    private var repository: MusicRepository? = null
    private var playCountManager: PlayCountManager? = null
    private var allSongs: List<Song> = emptyList()
    private var allVideos: List<Video> = emptyList()
    private var currentPlaylist: List<Song> = emptyList()
    private var currentIndex: Int = -1

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _videos = MutableLiveData<List<Video>>()
    val videos: LiveData<List<Video>> = _videos

    private val _artists = MutableLiveData<List<Artist>>()
    val artists: LiveData<List<Artist>> = _artists

    private val _albums = MutableLiveData<List<Album>>()
    val albums: LiveData<List<Album>> = _albums

    private val _folders = MutableLiveData<List<Folder>>()
    val folders: LiveData<List<Folder>> = _folders

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    // Separate event for starting playback (one-time event)
    private val _playSongEvent = MutableLiveData<Song?>()
    val playSongEvent: LiveData<Song?> = _playSongEvent

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _progress = MutableLiveData(0)

    private val _duration = MutableLiveData(0)

    private val _isLoading = MutableLiveData(false)

    private val _showPlayerBar = MutableLiveData(false)
    val showPlayerBar: LiveData<Boolean> = _showPlayerBar

    // Navigation events
    private val _navigateToSongsList = MutableLiveData<Pair<String, List<Song>>?>()
    val navigateToSongsList: LiveData<Pair<String, List<Song>>?> = _navigateToSongsList

    private var currentSortOption = SortOption.ADDING_TIME
    private var searchQuery = ""
    
    // Delete result event
    private val _deleteResult = MutableLiveData<DeleteResult?>()
    val deleteResult: LiveData<DeleteResult?> = _deleteResult
    
    data class DeleteResult(val success: Boolean, val songTitle: String)

    fun initialize(repository: MusicRepository) {
        this.repository = repository
        loadAllMedia()
    }
    
    fun initializePlayCountManager(context: Context) {
        playCountManager = PlayCountManager.getInstance(context)
    }

    fun loadAllMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository?.let { repo ->
                    allSongs = repo.getAllSongs()
                    allVideos = repo.getAllVideos()
                    
                    applySortAndFilter()
                    _videos.value = allVideos
                    updateArtists()
                    updateAlbums()
                    updateFolders()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSortOption(option: SortOption) {
        currentSortOption = option
        applySortAndFilter()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        applySortAndFilter()
    }

    private fun applySortAndFilter() {
        repository?.let { repo ->
            var result = allSongs
            
            // Apply play counts from manager
            playCountManager?.let { manager ->
                val playCounts = manager.getAllPlayCounts()
                result = result.map { song ->
                    song.copy(playCount = playCounts[song.id] ?: 0)
                }
            }
            
            if (searchQuery.isNotBlank()) {
                result = repo.searchSongs(result, searchQuery)
            }
            
            result = repo.sortSongs(result, currentSortOption)
            _songs.value = result
            currentPlaylist = result
        }
    }

    private fun updateArtists() {
        viewModelScope.launch {
            repository?.let { repo ->
                _artists.value = repo.getAllArtists(allSongs)
            }
        }
    }

    private fun updateAlbums() {
        viewModelScope.launch {
            repository?.let { repo ->
                _albums.value = repo.getAllAlbums(allSongs)
            }
        }
    }

    private fun updateFolders() {
        viewModelScope.launch {
            repository?.let { repo ->
                _folders.value = repo.getAllFolders(allSongs, allVideos)
            }
        }
    }

    fun selectArtist(artist: Artist) {
        repository?.let { repo ->
            val artistSongs = repo.getSongsForArtist(allSongs, artist.name)
            _navigateToSongsList.value = Pair(artist.name, artistSongs)
        }
    }

    fun selectAlbum(album: Album) {
        repository?.let { repo ->
            val albumSongs = repo.getSongsForAlbum(allSongs, album.title, album.artist)
            _navigateToSongsList.value = Pair(album.title, albumSongs)
        }
    }

    fun selectFolder(folder: Folder) {
        repository?.let { repo ->
            val folderSongs = repo.getSongsForFolder(allSongs, folder.path)
            _navigateToSongsList.value = Pair(folder.name, folderSongs)
        }
    }

    fun clearNavigation() {
        _navigateToSongsList.value = null
    }

    fun setPlaylist(songs: List<Song>) {
        currentPlaylist = songs
    }

    fun getCurrentPlaylist(): List<Song> = currentPlaylist

    fun playSong(song: Song) {
        _currentSong.value = song
        _playSongEvent.value = song
        _isPlaying.value = true
        _showPlayerBar.value = true
        currentIndex = currentPlaylist.indexOfFirst { it.id == song.id }
        if (currentIndex == -1) {
            currentPlaylist = listOf(song)
            currentIndex = 0
        }
    }

    fun clearPlaySongEvent() {
        _playSongEvent.value = null
    }

    fun updatePlaybackState(isPlaying: Boolean, song: Song?) {
        _isPlaying.value = isPlaying
        song?.let { 
            _currentSong.value = it
            // Update current index to match the song from service
            val index = currentPlaylist.indexOfFirst { s -> s.id == it.id }
            if (index != -1) {
                currentIndex = index
            }
        }
    }

    fun updateProgress(position: Int, duration: Int) {
        _progress.value = position
        _duration.value = duration
    }

    fun playNext() {
        if (currentPlaylist.isNotEmpty() && currentIndex < currentPlaylist.size - 1) {
            currentIndex++
            val nextSong = currentPlaylist[currentIndex]
            // Update UI state without triggering service restart
            _currentSong.value = nextSong
            _showPlayerBar.value = true
            // Don't set playSongEvent - service already handled it
        }
    }

    fun playPrevious() {
        if (currentPlaylist.isNotEmpty() && currentIndex > 0) {
            currentIndex--
            val prevSong = currentPlaylist[currentIndex]
            // Update UI state without triggering service restart
            _currentSong.value = prevSong
            _showPlayerBar.value = true
            // Don't set playSongEvent - service already handled it
        }
    }

    fun stopPlayback() {
        // Clear playback state when player is stopped
        _currentSong.value = null
        _isPlaying.value = false
        _showPlayerBar.value = false
        _progress.value = 0
        _duration.value = 0
    }

    fun shufflePlay() {
        val shuffled = allSongs.shuffled()
        if (shuffled.isNotEmpty()) {
            currentPlaylist = shuffled
            currentIndex = 0
            playSong(shuffled.first())
        }
    }

    fun shareSong(context: Context, song: Song) {
        try {
            val file = File(song.path)
            if (!file.exists()) {
                android.widget.Toast.makeText(context, "File not found", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share song"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Failed to share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    fun shareSongs(context: Context, songs: List<Song>) {
        if (songs.isEmpty()) return
        
        if (songs.size == 1) {
            shareSong(context, songs.first())
            return
        }
        
        try {
            val uris = ArrayList<android.net.Uri>()
            for (song in songs) {
                val file = File(song.path)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    uris.add(uri)
                }
            }
            
            if (uris.isEmpty()) {
                android.widget.Toast.makeText(context, "No files found", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${uris.size} songs"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Failed to share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    fun getSongsForArtist(artist: Artist): List<Song> {
        return repository?.getSongsForArtist(allSongs, artist.name) ?: emptyList()
    }
    
    fun getSongsForAlbum(album: Album): List<Song> {
        return repository?.getSongsForAlbum(allSongs, album.title, album.artist) ?: emptyList()
    }
    
    fun getSongsForFolder(folder: Folder): List<Song> {
        return repository?.getSongsForFolder(allSongs, folder.path) ?: emptyList()
    }
    
    fun getVideosForFolder(folder: Folder): List<Video> {
        return allVideos.filter { video ->
            java.io.File(video.path).parent == folder.path
        }
    }
    
    fun getAllVideos(): List<Video> = allVideos
    
    /**
     * Add a song to the playback queue
     */
    fun addToQueue(context: Context, song: Song) {
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_ADD_TO_QUEUE
            putExtra(MusicService.EXTRA_SONG, song)
        }
        context.startService(intent)
    }
    
    /**
     * Delete a song from the device
     */
    fun deleteSong(context: Context, song: Song) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    song.id
                )
                
                val deletedRows = contentResolver.delete(uri, null, null)
                
                if (deletedRows > 0) {
                    // Remove from local lists
                    allSongs = allSongs.filter { it.id != song.id }
                    applySortAndFilter()
                    updateArtists()
                    updateAlbums()
                    updateFolders()
                    
                    // Remove from current playlist if present
                    currentPlaylist = currentPlaylist.filter { it.id != song.id }
                    
                    _deleteResult.value = DeleteResult(true, song.title)
                } else {
                    // Try deleting the file directly as fallback
                    val file = File(song.path)
                    if (file.exists() && file.delete()) {
                        allSongs = allSongs.filter { it.id != song.id }
                        applySortAndFilter()
                        updateArtists()
                        updateAlbums()
                        updateFolders()
                        currentPlaylist = currentPlaylist.filter { it.id != song.id }
                        _deleteResult.value = DeleteResult(true, song.title)
                    } else {
                        _deleteResult.value = DeleteResult(false, song.title)
                    }
                }
            } catch (e: Exception) {
                _deleteResult.value = DeleteResult(false, song.title)
            }
        }
    }
    
    fun clearDeleteResult() {
        _deleteResult.value = null
    }
    
    /**
     * Get songs with play counts applied
     */
    private fun applySortAndFilterWithPlayCounts() {
        repository?.let { repo ->
            var result = allSongs
            
            // Apply play counts from manager
            playCountManager?.let { manager ->
                val playCounts = manager.getAllPlayCounts()
                result = result.map { song ->
                    song.copy(playCount = playCounts[song.id] ?: 0)
                }
            }
            
            if (searchQuery.isNotBlank()) {
                result = repo.searchSongs(result, searchQuery)
            }
            
            result = repo.sortSongs(result, currentSortOption)
            _songs.value = result
            currentPlaylist = result
        }
    }

}
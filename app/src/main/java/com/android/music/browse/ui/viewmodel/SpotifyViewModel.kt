package com.android.music.browse.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.music.browse.auth.SpotifyAuthManager
import com.android.music.browse.auth.SpotifyAuthState
import com.android.music.browse.data.model.*
import com.android.music.browse.data.repository.SpotifyRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class SpotifyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpotifyRepository(application.applicationContext)
    private val authManager = SpotifyAuthManager.getInstance(application.applicationContext)

    // Loading states
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Home content
    private val _homeContent = MutableLiveData<SpotifyHomeContent>()
    val homeContent: LiveData<SpotifyHomeContent> = _homeContent

    // Search
    private val _searchResults = MutableLiveData<SpotifySearchResult>()
    val searchResults: LiveData<SpotifySearchResult> = _searchResults

    private val _searchQuery = MutableLiveData<String>()
    val searchQuery: LiveData<String> = _searchQuery

    private val _isSearchMode = MutableLiveData(false)
    val isSearchMode: LiveData<Boolean> = _isSearchMode

    // Library
    private val _savedTracks = MutableLiveData<List<SpotifyTrack>>()
    val savedTracks: LiveData<List<SpotifyTrack>> = _savedTracks

    private val _savedAlbums = MutableLiveData<List<SpotifyAlbum>>()
    val savedAlbums: LiveData<List<SpotifyAlbum>> = _savedAlbums

    private val _userPlaylists = MutableLiveData<List<SpotifyPlaylist>>()
    val userPlaylists: LiveData<List<SpotifyPlaylist>> = _userPlaylists

    // Profile
    private val _userProfile = MutableLiveData<SpotifyUserProfile>()
    val userProfile: LiveData<SpotifyUserProfile> = _userProfile

    // Current track for player
    private val _currentTrack = MutableLiveData<SpotifyTrack?>()
    val currentTrack: LiveData<SpotifyTrack?> = _currentTrack

    // Error handling
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Auth state
    private val _isAuthenticated = MutableLiveData(false)
    val isAuthenticated: LiveData<Boolean> = _isAuthenticated

    init {
        // Observe auth state
        viewModelScope.launch {
            authManager.authState.collect { state ->
                _isAuthenticated.value = state is SpotifyAuthState.Authenticated
            }
        }
    }

    fun loadHomeContent() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getHomeContent()
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { content ->
                        _homeContent.value = content
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = SpotifySearchResult()
            _isSearchMode.value = false
            return
        }
        _searchQuery.value = query
        _isSearchMode.value = true
        viewModelScope.launch {
            _isLoading.value = true
            repository.search(query)
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { searchResult ->
                        _searchResults.value = searchResult
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = SpotifySearchResult()
        _isSearchMode.value = false
    }

    fun loadSavedTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getSavedTracks()
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { tracks ->
                        _savedTracks.value = tracks
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun loadSavedAlbums() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getSavedAlbums()
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { albums ->
                        _savedAlbums.value = albums
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun loadUserPlaylists() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getUserPlaylists()
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { playlists ->
                        _userPlaylists.value = playlists
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getUserProfile()
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { profile ->
                        _userProfile.value = profile
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun playTrack(track: SpotifyTrack) {
        _currentTrack.value = track
        // TODO: Integrate with Spotify SDK for playback
    }

    fun clearCurrentTrack() {
        _currentTrack.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun signOut() {
        authManager.signOut()
        // Clear cached data
        _homeContent.value = null
        _savedTracks.value = null
        _savedAlbums.value = null
        _userPlaylists.value = null
        _userProfile.value = null
        _currentTrack.value = null
    }

    fun getAuthManager(): SpotifyAuthManager = authManager
}

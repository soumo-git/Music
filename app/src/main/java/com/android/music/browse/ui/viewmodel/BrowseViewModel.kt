package com.android.music.browse.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.music.browse.auth.YouTubeAuthManager
import com.android.music.browse.auth.YouTubeAuthState
import com.android.music.browse.data.model.StreamingPlatform
import com.android.music.browse.data.model.YouTubeChannel
import com.android.music.browse.data.model.YouTubeHomeContent
import com.android.music.browse.data.model.YouTubePlaylist
import com.android.music.browse.data.model.YouTubeSearchResult
import com.android.music.browse.data.model.YouTubeVideo
import com.android.music.browse.data.repository.YouTubeRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = YouTubeRepository(application.applicationContext)
    private val authManager = YouTubeAuthManager.getInstance(application.applicationContext)

    // Current platform - default to YOUTUBE
    private val _currentPlatform = MutableLiveData(StreamingPlatform.YOUTUBE)
    val currentPlatform: LiveData<StreamingPlatform> = _currentPlatform

    // Loading states
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Home content
    private val _homeContent = MutableLiveData<YouTubeHomeContent>()
    val homeContent: LiveData<YouTubeHomeContent> = _homeContent

    // Search
    private val _searchResults = MutableLiveData<YouTubeSearchResult>()
    val searchResults: LiveData<YouTubeSearchResult> = _searchResults

    private val _searchQuery = MutableLiveData<String>()
    val searchQuery: LiveData<String> = _searchQuery

    private val _isSearchMode = MutableLiveData(false)
    val isSearchMode: LiveData<Boolean> = _isSearchMode

    // Shorts
    private val _shorts = MutableLiveData<List<YouTubeVideo>>()
    val shorts: LiveData<List<YouTubeVideo>> = _shorts

    // Subscriptions
    private val _subscriptions = MutableLiveData<List<YouTubeChannel>>()
    val subscriptions: LiveData<List<YouTubeChannel>> = _subscriptions

    // Playlists
    private val _playlists = MutableLiveData<List<YouTubePlaylist>>()
    val playlists: LiveData<List<YouTubePlaylist>> = _playlists

    // Current video for player
    private val _currentVideo = MutableLiveData<YouTubeVideo?>()
    val currentVideo: LiveData<YouTubeVideo?> = _currentVideo

    // Related videos
    private val _relatedVideos = MutableLiveData<List<YouTubeVideo>>()
    val relatedVideos: LiveData<List<YouTubeVideo>> = _relatedVideos

    // Channel videos
    private val _channelVideos = MutableLiveData<List<YouTubeVideo>>()
    val channelVideos: LiveData<List<YouTubeVideo>> = _channelVideos

    // Channel shorts
    private val _channelShorts = MutableLiveData<List<YouTubeVideo>>()
    val channelShorts: LiveData<List<YouTubeVideo>> = _channelShorts

    // Channel playlists
    private val _channelPlaylists = MutableLiveData<List<YouTubePlaylist>>()
    val channelPlaylists: LiveData<List<YouTubePlaylist>> = _channelPlaylists

    // Playlist videos
    private val _playlistVideos = MutableLiveData<List<YouTubeVideo>>()
    val playlistVideos: LiveData<List<YouTubeVideo>> = _playlistVideos

    // Error handling
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Current tab
    private val _currentTab = MutableLiveData(BrowseTab.HOME)
    val currentTab: LiveData<BrowseTab> = _currentTab

    // Auth state
    private val _isAuthenticated = MutableLiveData(false)
    val isAuthenticated: LiveData<Boolean> = _isAuthenticated

    init {
        // Observe auth state
        viewModelScope.launch {
            authManager.authState.collect { state ->
                _isAuthenticated.value = state is YouTubeAuthState.Authenticated
            }
        }
    }

    fun setPlatform(platform: StreamingPlatform) {
        _currentPlatform.value = platform
    }

    fun setCurrentTab(tab: BrowseTab) {
        _currentTab.value = tab
        _isSearchMode.value = false
        when (tab) {
            BrowseTab.HOME -> loadHomeContent()
            BrowseTab.SHORTS -> loadShorts()
            BrowseTab.SUBSCRIPTIONS -> loadSubscriptions()
            BrowseTab.PLAYLISTS -> loadPlaylists()
            BrowseTab.PROFILE -> { /* Load profile */ }
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
            _searchResults.value = YouTubeSearchResult()
            _isSearchMode.value = false
            return
        }
        _searchQuery.value = query
        _isSearchMode.value = true
        viewModelScope.launch {
            _isLoading.value = true
            repository.searchVideos(query)
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
        _searchResults.value = YouTubeSearchResult()
        _isSearchMode.value = false
    }

    fun loadShorts() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getShorts()
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { shortsList ->
                        _shorts.value = shortsList
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun loadSubscriptions() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getSubscriptions()
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { channels ->
                        _subscriptions.value = channels
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getPlaylists()
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { playlistList ->
                        _playlists.value = playlistList
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun playVideo(video: YouTubeVideo) {
        _currentVideo.value = video
        loadRelatedVideos(video.id)
    }

    fun loadRelatedVideos(videoId: String) {
        viewModelScope.launch {
            repository.getRelatedVideos(videoId)
                .catch { e ->
                    _error.value = e.message
                }
                .collect { result ->
                    result.onSuccess { videos ->
                        _relatedVideos.value = videos
                    }
                }
        }
    }

    fun clearCurrentVideo() {
        _currentVideo.value = null
    }

    fun clearError() {
        _error.value = null
    }

    // Channel methods
    fun loadChannelDetails(channelId: String) = repository.getChannelDetails(channelId)

    fun loadChannelVideos(channelId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getChannelVideos(channelId)
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { videos ->
                        _channelVideos.value = videos
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun loadChannelShorts(channelId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getChannelShorts(channelId)
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { shorts ->
                        _channelShorts.value = shorts
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun loadChannelPlaylists(channelId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getChannelPlaylists(channelId)
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { playlists ->
                        _channelPlaylists.value = playlists
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    fun loadPlaylistVideos(playlistId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getPlaylistVideos(playlistId)
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { result ->
                    result.onSuccess { videos ->
                        _playlistVideos.value = videos
                    }.onFailure { e ->
                        _error.value = e.message
                    }
                    _isLoading.value = false
                }
        }
    }

    // Auth methods
    fun getAuthManager(): YouTubeAuthManager = authManager

    enum class BrowseTab {
        HOME, SHORTS, SUBSCRIPTIONS, PLAYLISTS, PROFILE
    }
}

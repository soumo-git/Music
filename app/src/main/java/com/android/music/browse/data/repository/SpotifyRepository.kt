package com.android.music.browse.data.repository

import android.content.Context
import android.util.Log
import com.android.music.browse.auth.SpotifyAuthManager
import com.android.music.browse.data.api.SpotifyApiService
import com.android.music.browse.data.mapper.SpotifyMapper.toSpotifyAlbum
import com.android.music.browse.data.mapper.SpotifyMapper.toSpotifyArtist
import com.android.music.browse.data.mapper.SpotifyMapper.toSpotifyPlaylist
import com.android.music.browse.data.mapper.SpotifyMapper.toSpotifyTrack
import com.android.music.browse.data.mapper.SpotifyMapper.toSpotifyUserProfile
import com.android.music.browse.data.model.*
import com.android.music.browse.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Repository for Spotify data operations.
 * Connects to Spotify Web API for all data.
 */
class SpotifyRepository(context: Context? = null) {

    private val apiService: SpotifyApiService = NetworkModule.spotifyApiService
    private val authManager: SpotifyAuthManager? = context?.let { SpotifyAuthManager.getInstance(it) }

    /**
     * Get OAuth token from authenticated user
     */
    private suspend fun getOAuthToken(): String? {
        return try {
            authManager?.getAccessToken()
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error getting OAuth token", e)
            null
        }
    }

    /**
     * Get home content (featured playlists, new releases, recommendations)
     */
    fun getHomeContent(): Flow<Result<SpotifyHomeContent>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            // Get featured playlists
            val featuredResponse = apiService.getFeaturedPlaylists(limit = 10)
            val featuredPlaylists = if (featuredResponse.isSuccessful) {
                featuredResponse.body()?.playlists?.items?.map { it.toSpotifyPlaylist() } ?: emptyList()
            } else emptyList()

            // Get new releases
            val newReleasesResponse = apiService.getNewReleases(limit = 10)
            val newReleases = if (newReleasesResponse.isSuccessful) {
                newReleasesResponse.body()?.albums?.items?.map { it.toSpotifyAlbum() } ?: emptyList()
            } else emptyList()

            // Get recommendations (using popular genres as seeds)
            val recommendationsResponse = apiService.getRecommendations(
                seedGenres = "pop,rock,hip-hop",
                limit = 20
            )
            val recommendations = if (recommendationsResponse.isSuccessful) {
                recommendationsResponse.body()?.tracks?.map { it.toSpotifyTrack() } ?: emptyList()
            } else emptyList()

            emit(Result.success(SpotifyHomeContent(
                featuredPlaylists = featuredPlaylists,
                newReleases = newReleases,
                recommendations = recommendations
            )))
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading home content", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Search for tracks, albums, artists, playlists
     */
    fun search(query: String): Flow<Result<SpotifySearchResult>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.search(query = query, limit = 20)

            if (response.isSuccessful) {
                val searchResponse = response.body()
                val tracks = searchResponse?.tracks?.items?.map { it.toSpotifyTrack() } ?: emptyList()
                val albums = searchResponse?.albums?.items?.map { it.toSpotifyAlbum() } ?: emptyList()
                val artists = searchResponse?.artists?.items?.map { it.toSpotifyArtist() } ?: emptyList()
                val playlists = searchResponse?.playlists?.items?.map { it.toSpotifyPlaylist() } ?: emptyList()

                emit(Result.success(SpotifySearchResult(
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    playlists = playlists
                )))
            } else {
                emit(Result.failure(Exception("Search failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error searching", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user's library (saved tracks)
     */
    fun getSavedTracks(): Flow<Result<List<SpotifyTrack>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getSavedTracks(limit = 50)

            if (response.isSuccessful) {
                val tracks = response.body()?.items?.map { it.track.toSpotifyTrack() } ?: emptyList()
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Failed to load saved tracks: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading saved tracks", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user's saved albums
     */
    fun getSavedAlbums(): Flow<Result<List<SpotifyAlbum>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getSavedAlbums(limit = 50)

            if (response.isSuccessful) {
                val albums = response.body()?.items?.map { it.album.toSpotifyAlbum() } ?: emptyList()
                emit(Result.success(albums))
            } else {
                emit(Result.failure(Exception("Failed to load saved albums: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading saved albums", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user's playlists
     */
    fun getUserPlaylists(): Flow<Result<List<SpotifyPlaylist>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getUserPlaylists(limit = 50)

            if (response.isSuccessful) {
                val playlists = response.body()?.items?.map { it.toSpotifyPlaylist() } ?: emptyList()
                emit(Result.success(playlists))
            } else {
                emit(Result.failure(Exception("Failed to load playlists: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading playlists", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user profile
     */
    fun getUserProfile(): Flow<Result<SpotifyUserProfile>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getCurrentUserProfile()

            if (response.isSuccessful) {
                val profile = response.body()?.toSpotifyUserProfile()
                if (profile != null) {
                    emit(Result.success(profile))
                } else {
                    emit(Result.failure(Exception("Profile not found")))
                }
            } else {
                emit(Result.failure(Exception("Failed to load profile: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading profile", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get playlist tracks
     */
    fun getPlaylistTracks(playlistId: String): Flow<Result<List<SpotifyTrack>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getPlaylistTracks(playlistId, limit = 100)

            if (response.isSuccessful) {
                val tracks = response.body()?.items?.mapNotNull { it.track?.toSpotifyTrack() } ?: emptyList()
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Failed to load playlist tracks: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading playlist tracks", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get album tracks
     */
    fun getAlbumTracks(albumId: String): Flow<Result<List<SpotifyTrack>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getAlbumTracks(albumId, limit = 50)

            if (response.isSuccessful) {
                val tracks = response.body()?.items?.map { it.toSpotifyTrack() } ?: emptyList()
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Failed to load album tracks: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading album tracks", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get artist top tracks
     */
    fun getArtistTopTracks(artistId: String): Flow<Result<List<SpotifyTrack>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getArtistTopTracks(artistId)

            if (response.isSuccessful) {
                val tracks = response.body()?.tracks?.map { it.toSpotifyTrack() } ?: emptyList()
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Failed to load artist tracks: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading artist tracks", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user's top tracks
     */
    fun getUserTopTracks(): Flow<Result<List<SpotifyTrack>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            NetworkModule.spotifyOauthToken = token

            val response = apiService.getUserTopTracks(timeRange = "medium_term", limit = 20)

            if (response.isSuccessful) {
                val tracks = response.body()?.items?.map { it.toSpotifyTrack() } ?: emptyList()
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Failed to load top tracks: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepository", "Error loading top tracks", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
}

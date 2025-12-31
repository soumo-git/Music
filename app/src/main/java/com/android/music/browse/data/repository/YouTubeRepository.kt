package com.android.music.browse.data.repository

import android.content.Context
import android.util.Log
import com.android.music.browse.auth.YouTubeAuthManager
import com.android.music.browse.data.api.YouTubeApiService
import com.android.music.browse.data.mapper.YouTubeMapper.toYouTubeChannel
import com.android.music.browse.data.mapper.YouTubeMapper.toYouTubePlaylist
import com.android.music.browse.data.mapper.YouTubeMapper.toYouTubeVideo
import com.android.music.browse.data.model.YouTubeChannel
import com.android.music.browse.data.model.YouTubeHomeContent
import com.android.music.browse.data.model.YouTubePlaylist
import com.android.music.browse.data.model.YouTubeSearchResult
import com.android.music.browse.data.model.YouTubeVideo
import com.android.music.browse.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Repository for YouTube data operations.
 * Connects to YouTube Data API v3 for real data only.
 */
class YouTubeRepository(private val context: Context? = null) {

    private val apiService: YouTubeApiService = NetworkModule.youtubeApiService
    private val authManager: YouTubeAuthManager? = context?.let { YouTubeAuthManager.getInstance(it) }

    /**
     * Get OAuth token from authenticated user's Google account
     */
    private suspend fun getOAuthToken(): String? {
        return try {
            authManager?.getAccessToken()
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "Error getting OAuth token", e)
            null
        }
    }

    /**
     * Get home content (trending + recommended videos)
     */
    fun getHomeContent(): Flow<Result<YouTubeHomeContent>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            // Set OAuth token in network module
            NetworkModule.oauthToken = token
            
            // Get trending videos
            val trendingResponse = apiService.getTrendingVideos(
                maxResults = 10
            )

            if (trendingResponse.isSuccessful) {
                val trendingVideos = trendingResponse.body()?.items?.map { it.toYouTubeVideo() } ?: emptyList()
                
                // Get music category videos for recommended
                val musicResponse = apiService.getTrendingVideos(
                    maxResults = 10,
                    categoryId = "10" // Music category
                )
                val recommendedVideos = musicResponse.body()?.items?.map { it.toYouTubeVideo() } ?: emptyList()

                emit(Result.success(YouTubeHomeContent(
                    trendingVideos = trendingVideos,
                    recommendedVideos = recommendedVideos,
                    recentVideos = trendingVideos.shuffled().take(5)
                )))
            } else {
                val errorBody = trendingResponse.errorBody()?.string() ?: "Unknown error"
                Log.e("YouTubeRepository", "API Error: ${trendingResponse.code()} - $errorBody")
                emit(Result.failure(Exception("API Error: ${trendingResponse.code()}")))
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "Error loading home content", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Search for videos
     */
    fun searchVideos(query: String, pageToken: String? = null): Flow<Result<YouTubeSearchResult>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            NetworkModule.oauthToken = token
            
            val response = apiService.search(
                query = query,
                maxResults = 20,
                pageToken = pageToken
            )

            if (response.isSuccessful) {
                val searchResponse = response.body()
                val videos = searchResponse?.items?.mapNotNull { it.toYouTubeVideo() } ?: emptyList()
                
                // Get video details for duration and view count
                if (videos.isNotEmpty()) {
                    val videoIds = videos.map { it.id }.joinToString(",")
                    val detailsResponse = apiService.getVideos(ids = videoIds)
                    
                    if (detailsResponse.isSuccessful) {
                        val detailedVideos = detailsResponse.body()?.items?.map { it.toYouTubeVideo() } ?: videos
                        emit(Result.success(YouTubeSearchResult(
                            videos = detailedVideos,
                            nextPageToken = searchResponse?.nextPageToken
                        )))
                    } else {
                        emit(Result.success(YouTubeSearchResult(
                            videos = videos,
                            nextPageToken = searchResponse?.nextPageToken
                        )))
                    }
                } else {
                    emit(Result.success(YouTubeSearchResult(videos = emptyList())))
                }
            } else {
                emit(Result.failure(Exception("Search failed: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get Shorts (short-form videos)
     */
    fun getShorts(): Flow<Result<List<YouTubeVideo>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            NetworkModule.oauthToken = token
            
            val response = apiService.searchShorts()

            if (response.isSuccessful) {
                val videos = response.body()?.items?.mapNotNull { 
                    it.toYouTubeVideo()?.copy(isShort = true) 
                } ?: emptyList()
                
                emit(Result.success(videos))
            } else {
                emit(Result.failure(Exception("Failed to load shorts: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user's subscriptions (requires authentication)
     */
    fun getSubscriptions(): Flow<Result<List<YouTubeChannel>>> = flow {
        if (authManager == null || !authManager.isAuthenticated()) {
            emit(Result.failure(Exception("Not authenticated")))
            return@flow
        }

        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("Failed to get access token")))
                return@flow
            }

            NetworkModule.oauthToken = token
            
            val response = apiService.getSubscriptions()

            if (response.isSuccessful) {
                val channels = response.body()?.items?.map { it.toYouTubeChannel() } ?: emptyList()
                emit(Result.success(channels))
            } else {
                emit(Result.failure(Exception("Failed to load subscriptions: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get user's playlists (requires authentication)
     */
    fun getPlaylists(): Flow<Result<List<YouTubePlaylist>>> = flow {
        if (authManager == null || !authManager.isAuthenticated()) {
            emit(Result.failure(Exception("Not authenticated")))
            return@flow
        }

        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("Failed to get access token")))
                return@flow
            }

            NetworkModule.oauthToken = token
            
            val response = apiService.getPlaylists()

            if (response.isSuccessful) {
                val playlists = response.body()?.items?.map { it.toYouTubePlaylist() } ?: emptyList()
                emit(Result.success(playlists))
            } else {
                emit(Result.failure(Exception("Failed to load playlists: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get video details by ID
     */
    fun getVideoDetails(videoId: String): Flow<Result<YouTubeVideo>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            NetworkModule.oauthToken = token
            
            val response = apiService.getVideos(ids = videoId)

            if (response.isSuccessful) {
                val video = response.body()?.items?.firstOrNull()?.toYouTubeVideo()
                if (video != null) {
                    emit(Result.success(video))
                } else {
                    emit(Result.failure(Exception("Video not found")))
                }
            } else {
                emit(Result.failure(Exception("Failed to load video: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get related videos - Using search with video title instead of deprecated relatedToVideoId
     */
    fun getRelatedVideos(videoId: String): Flow<Result<List<YouTubeVideo>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            NetworkModule.oauthToken = token
            
            // First get the video details to get its title/category
            val videoResponse = apiService.getVideos(ids = videoId)
            
            if (videoResponse.isSuccessful) {
                val video = videoResponse.body()?.items?.firstOrNull()
                val searchQuery = video?.snippet?.title?.split(" ")?.take(3)?.joinToString(" ") ?: "music"
                
                // Search for similar videos
                val response = apiService.search(
                    query = searchQuery,
                    maxResults = 15
                )

                if (response.isSuccessful) {
                    val videos = response.body()?.items?.mapNotNull { it.toYouTubeVideo() }
                        ?.filter { it.id != videoId } // Exclude current video
                        ?: emptyList()
                    emit(Result.success(videos))
                } else {
                    emit(Result.failure(Exception("Failed to load related videos: ${response.code()}")))
                }
            } else {
                emit(Result.failure(Exception("Failed to get video details")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get channel details by ID
     */
    fun getChannelDetails(channelId: String): Flow<Result<YouTubeChannel>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            NetworkModule.oauthToken = token
            
            val response = apiService.getChannels(ids = channelId)

            if (response.isSuccessful) {
                val channel = response.body()?.items?.firstOrNull()?.toYouTubeChannel()
                if (channel != null) {
                    emit(Result.success(channel))
                } else {
                    emit(Result.failure(Exception("Channel not found")))
                }
            } else {
                emit(Result.failure(Exception("Failed to load channel: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get videos from a specific channel
     */
    fun getChannelVideos(channelId: String, pageToken: String? = null): Flow<Result<List<YouTubeVideo>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            NetworkModule.oauthToken = token
            
            // Search for videos from this channel
            val response = apiService.searchChannelVideos(
                channelId = channelId,
                maxResults = 20,
                pageToken = pageToken
            )

            if (response.isSuccessful) {
                val videoIds = response.body()?.items?.mapNotNull { 
                    it.id?.videoId 
                }?.joinToString(",") ?: ""
                
                if (videoIds.isNotEmpty()) {
                    // Get full video details
                    val detailsResponse = apiService.getVideos(ids = videoIds)
                    if (detailsResponse.isSuccessful) {
                        val videos = detailsResponse.body()?.items?.map { it.toYouTubeVideo() } ?: emptyList()
                        emit(Result.success(videos))
                    } else {
                        emit(Result.success(emptyList()))
                    }
                } else {
                    emit(Result.success(emptyList()))
                }
            } else {
                emit(Result.failure(Exception("Failed to load channel videos: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get shorts from a specific channel
     */
    fun getChannelShorts(channelId: String): Flow<Result<List<YouTubeVideo>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            NetworkModule.oauthToken = token
            
            val response = apiService.searchChannelShorts(
                channelId = channelId,
                maxResults = 20
            )

            if (response.isSuccessful) {
                val videos = response.body()?.items?.mapNotNull { 
                    it.toYouTubeVideo()?.copy(isShort = true) 
                } ?: emptyList()
                emit(Result.success(videos))
            } else {
                emit(Result.failure(Exception("Failed to load channel shorts: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get playlists from a specific channel
     */
    fun getChannelPlaylists(channelId: String): Flow<Result<List<YouTubePlaylist>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            NetworkModule.oauthToken = token
            
            val response = apiService.getChannelPlaylists(
                channelId = channelId,
                maxResults = 20
            )

            if (response.isSuccessful) {
                val playlists = response.body()?.items?.map { it.toYouTubePlaylist() } ?: emptyList()
                emit(Result.success(playlists))
            } else {
                emit(Result.failure(Exception("Failed to load channel playlists: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get videos from a playlist
     */
    fun getPlaylistVideos(playlistId: String, pageToken: String? = null): Flow<Result<List<YouTubeVideo>>> = flow {
        try {
            val token = getOAuthToken()
            if (token == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            NetworkModule.oauthToken = token
            
            val response = apiService.getPlaylistItems(
                playlistId = playlistId,
                maxResults = 50,
                pageToken = pageToken
            )

            if (response.isSuccessful) {
                val videoIds = response.body()?.items?.mapNotNull { 
                    it.contentDetails?.videoId 
                }?.joinToString(",") ?: ""
                
                if (videoIds.isNotEmpty()) {
                    // Get full video details
                    val detailsResponse = apiService.getVideos(ids = videoIds)
                    if (detailsResponse.isSuccessful) {
                        val videos = detailsResponse.body()?.items?.map { it.toYouTubeVideo() } ?: emptyList()
                        emit(Result.success(videos))
                    } else {
                        emit(Result.success(emptyList()))
                    }
                } else {
                    emit(Result.success(emptyList()))
                }
            } else {
                emit(Result.failure(Exception("Failed to load playlist videos: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
}

package com.android.music.browse.data.api

import com.android.music.browse.data.api.model.YouTubeChannelListResponse
import com.android.music.browse.data.api.model.YouTubePlaylistItemListResponse
import com.android.music.browse.data.api.model.YouTubePlaylistListResponse
import com.android.music.browse.data.api.model.YouTubeSearchResponse
import com.android.music.browse.data.api.model.YouTubeSubscriptionListResponse
import com.android.music.browse.data.api.model.YouTubeVideoListResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * YouTube Data API v3 Service Interface
 * Documentation: https://developers.google.com/youtube/v3/docs
 */
interface YouTubeApiService {

    companion object {
        const val BASE_URL = "https://www.googleapis.com/youtube/v3/"
    }

    /**
     * Search for videos, channels, or playlists
     */
    @GET("search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20,
        @Query("pageToken") pageToken: String? = null,
        @Query("videoDuration") videoDuration: String? = null,
        @Query("order") order: String = "relevance"
    ): Response<YouTubeSearchResponse>

    /**
     * Get video details by ID(s)
     */
    @GET("videos")
    suspend fun getVideos(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("id") ids: String
    ): Response<YouTubeVideoListResponse>

    /**
     * Get trending/popular videos
     */
    @GET("videos")
    suspend fun getTrendingVideos(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("chart") chart: String = "mostPopular",
        @Query("regionCode") regionCode: String = "US",
        @Query("maxResults") maxResults: Int = 20,
        @Query("videoCategoryId") categoryId: String? = null,
        @Query("pageToken") pageToken: String? = null
    ): Response<YouTubeVideoListResponse>

    /**
     * Get channel details
     */
    @GET("channels")
    suspend fun getChannels(
        @Query("part") part: String = "snippet,statistics,contentDetails",
        @Query("id") ids: String? = null,
        @Query("mine") mine: Boolean? = null
    ): Response<YouTubeChannelListResponse>

    /**
     * Get user's subscriptions (requires OAuth)
     */
    @GET("subscriptions")
    suspend fun getSubscriptions(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null,
        @Query("order") order: String = "alphabetical"
    ): Response<YouTubeSubscriptionListResponse>

    /**
     * Get user's playlists (requires OAuth)
     */
    @GET("playlists")
    suspend fun getPlaylists(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): Response<YouTubePlaylistListResponse>

    /**
     * Get playlist items
     */
    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): Response<YouTubePlaylistItemListResponse>

    /**
     * Get related videos (using search with relatedToVideoId)
     */
    @GET("search")
    suspend fun getRelatedVideos(
        @Query("part") part: String = "snippet",
        @Query("relatedToVideoId") videoId: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 15
    ): Response<YouTubeSearchResponse>

    /**
     * Search for Shorts (short-form videos)
     */
    @GET("search")
    suspend fun searchShorts(
        @Query("part") part: String = "snippet",
        @Query("q") query: String = "#shorts",
        @Query("type") type: String = "video",
        @Query("videoDuration") videoDuration: String = "short",
        @Query("maxResults") maxResults: Int = 20,
        @Query("pageToken") pageToken: String? = null,
        @Query("order") order: String = "viewCount"
    ): Response<YouTubeSearchResponse>

    /**
     * Search for videos from a specific channel
     */
    @GET("search")
    suspend fun searchChannelVideos(
        @Query("part") part: String = "snippet",
        @Query("channelId") channelId: String,
        @Query("type") type: String = "video",
        @Query("order") order: String = "date",
        @Query("maxResults") maxResults: Int = 20,
        @Query("pageToken") pageToken: String? = null
    ): Response<YouTubeSearchResponse>

    /**
     * Search for shorts from a specific channel
     */
    @GET("search")
    suspend fun searchChannelShorts(
        @Query("part") part: String = "snippet",
        @Query("channelId") channelId: String,
        @Query("type") type: String = "video",
        @Query("videoDuration") videoDuration: String = "short",
        @Query("order") order: String = "date",
        @Query("maxResults") maxResults: Int = 20
    ): Response<YouTubeSearchResponse>

    /**
     * Get playlists from a specific channel
     */
    @GET("playlists")
    suspend fun getChannelPlaylists(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("channelId") channelId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): Response<YouTubePlaylistListResponse>
}

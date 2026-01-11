package com.android.music.browse.data.api.model

import com.google.gson.annotations.SerializedName

/**
 * YouTube Data API v3 Response Models
 * These models map directly to the YouTube API JSON responses
 */

// ============ Common Models ============

data class PageInfo(
    @SerializedName("totalResults") val totalResults: Int,
    @SerializedName("resultsPerPage") val resultsPerPage: Int
)

data class Thumbnail(
    @SerializedName("url") val url: String,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)

data class Thumbnails(
    @SerializedName("default") val default: Thumbnail?,
    @SerializedName("medium") val medium: Thumbnail?,
    @SerializedName("high") val high: Thumbnail?,
    @SerializedName("standard") val standard: Thumbnail?,
    @SerializedName("maxres") val maxres: Thumbnail?
) {

    fun getMediumQuality(): String? {
        return high?.url ?: medium?.url ?: default?.url
    }
}

// ============ Search Response ============

data class YouTubeSearchResponse(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("prevPageToken") val prevPageToken: String?,
    @SerializedName("regionCode") val regionCode: String?,
    @SerializedName("pageInfo") val pageInfo: PageInfo,
    @SerializedName("items") val items: List<SearchItem>
)

data class SearchItem(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("id") val id: SearchItemId,
    @SerializedName("snippet") val snippet: SearchSnippet
)

data class SearchItemId(
    @SerializedName("kind") val kind: String,
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("channelId") val channelId: String?,
    @SerializedName("playlistId") val playlistId: String?
)

data class SearchSnippet(
    @SerializedName("publishedAt") val publishedAt: String,
    @SerializedName("channelId") val channelId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("thumbnails") val thumbnails: Thumbnails,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("liveBroadcastContent") val liveBroadcastContent: String?
)

// ============ Video Response ============

data class YouTubeVideoListResponse(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("prevPageToken") val prevPageToken: String?,
    @SerializedName("pageInfo") val pageInfo: PageInfo,
    @SerializedName("items") val items: List<VideoItem>
)

data class VideoItem(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("id") val id: String,
    @SerializedName("snippet") val snippet: VideoSnippet?,
    @SerializedName("contentDetails") val contentDetails: VideoContentDetails?,
    @SerializedName("statistics") val statistics: VideoStatistics?
)

data class VideoSnippet(
    @SerializedName("publishedAt") val publishedAt: String,
    @SerializedName("channelId") val channelId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("thumbnails") val thumbnails: Thumbnails,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("tags") val tags: List<String>?,
    @SerializedName("categoryId") val categoryId: String?,
    @SerializedName("liveBroadcastContent") val liveBroadcastContent: String?,
    @SerializedName("defaultLanguage") val defaultLanguage: String?,
    @SerializedName("defaultAudioLanguage") val defaultAudioLanguage: String?
)

data class VideoContentDetails(
    @SerializedName("duration") val duration: String,
    @SerializedName("dimension") val dimension: String?,
    @SerializedName("definition") val definition: String?,
    @SerializedName("caption") val caption: String?,
    @SerializedName("licensedContent") val licensedContent: Boolean?,
    @SerializedName("projection") val projection: String?
)

data class VideoStatistics(
    @SerializedName("viewCount") val viewCount: String?,
    @SerializedName("likeCount") val likeCount: String?,
    @SerializedName("dislikeCount") val dislikeCount: String?,
    @SerializedName("favoriteCount") val favoriteCount: String?,
    @SerializedName("commentCount") val commentCount: String?
)

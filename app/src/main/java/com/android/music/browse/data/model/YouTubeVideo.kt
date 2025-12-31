package com.android.music.browse.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class YouTubeVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val channelThumbnail: String?,
    val thumbnailUrl: String,
    val duration: String,
    val viewCount: String,
    val likeCount: String?,
    val publishedAt: String,
    val description: String?,
    val isShort: Boolean = false
) : Parcelable

@Parcelize
data class YouTubeChannel(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val subscriberCount: String?,
    val videoCount: String?
) : Parcelable

@Parcelize
data class YouTubePlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val channelName: String,
    val channelId: String,
    val videoCount: Int,
    val description: String?
) : Parcelable

data class YouTubeSearchResult(
    val videos: List<YouTubeVideo> = emptyList(),
    val channels: List<YouTubeChannel> = emptyList(),
    val playlists: List<YouTubePlaylist> = emptyList(),
    val nextPageToken: String? = null
)

data class YouTubeHomeContent(
    val trendingVideos: List<YouTubeVideo> = emptyList(),
    val recommendedVideos: List<YouTubeVideo> = emptyList(),
    val recentVideos: List<YouTubeVideo> = emptyList()
)

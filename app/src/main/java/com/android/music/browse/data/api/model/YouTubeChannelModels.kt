package com.android.music.browse.data.api.model

import com.google.gson.annotations.SerializedName

// ============ Channel Response ============

data class YouTubeChannelListResponse(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("pageInfo") val pageInfo: PageInfo,
    @SerializedName("items") val items: List<ChannelItem>
)

data class ChannelItem(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("id") val id: String,
    @SerializedName("snippet") val snippet: ChannelSnippet?,
    @SerializedName("statistics") val statistics: ChannelStatistics?,
    @SerializedName("contentDetails") val contentDetails: ChannelContentDetails?
)

data class ChannelSnippet(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("customUrl") val customUrl: String?,
    @SerializedName("publishedAt") val publishedAt: String,
    @SerializedName("thumbnails") val thumbnails: Thumbnails,
    @SerializedName("country") val country: String?
)

data class ChannelStatistics(
    @SerializedName("viewCount") val viewCount: String?,
    @SerializedName("subscriberCount") val subscriberCount: String?,
    @SerializedName("hiddenSubscriberCount") val hiddenSubscriberCount: Boolean?,
    @SerializedName("videoCount") val videoCount: String?
)

data class ChannelContentDetails(
    @SerializedName("relatedPlaylists") val relatedPlaylists: RelatedPlaylists?
)

data class RelatedPlaylists(
    @SerializedName("likes") val likes: String?,
    @SerializedName("uploads") val uploads: String?
)

// ============ Subscription Response ============

data class YouTubeSubscriptionListResponse(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("prevPageToken") val prevPageToken: String?,
    @SerializedName("pageInfo") val pageInfo: PageInfo,
    @SerializedName("items") val items: List<SubscriptionItem>
)

data class SubscriptionItem(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("id") val id: String,
    @SerializedName("snippet") val snippet: SubscriptionSnippet?,
    @SerializedName("contentDetails") val contentDetails: SubscriptionContentDetails?
)

data class SubscriptionSnippet(
    @SerializedName("publishedAt") val publishedAt: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("resourceId") val resourceId: SubscriptionResourceId,
    @SerializedName("channelId") val channelId: String,
    @SerializedName("thumbnails") val thumbnails: Thumbnails
)

data class SubscriptionResourceId(
    @SerializedName("kind") val kind: String,
    @SerializedName("channelId") val channelId: String
)

data class SubscriptionContentDetails(
    @SerializedName("totalItemCount") val totalItemCount: Int?,
    @SerializedName("newItemCount") val newItemCount: Int?,
    @SerializedName("activityType") val activityType: String?
)

// ============ Playlist Response ============

data class YouTubePlaylistListResponse(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("prevPageToken") val prevPageToken: String?,
    @SerializedName("pageInfo") val pageInfo: PageInfo,
    @SerializedName("items") val items: List<PlaylistItem>
)

data class PlaylistItem(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("id") val id: String,
    @SerializedName("snippet") val snippet: PlaylistSnippet?,
    @SerializedName("contentDetails") val contentDetails: PlaylistContentDetails?
)

data class PlaylistSnippet(
    @SerializedName("publishedAt") val publishedAt: String,
    @SerializedName("channelId") val channelId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("thumbnails") val thumbnails: Thumbnails,
    @SerializedName("channelTitle") val channelTitle: String
)

data class PlaylistContentDetails(
    @SerializedName("itemCount") val itemCount: Int
)

// ============ Playlist Items Response ============

data class YouTubePlaylistItemListResponse(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("prevPageToken") val prevPageToken: String?,
    @SerializedName("pageInfo") val pageInfo: PageInfo,
    @SerializedName("items") val items: List<PlaylistItemEntry>
)

data class PlaylistItemEntry(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("id") val id: String,
    @SerializedName("snippet") val snippet: PlaylistItemSnippet?,
    @SerializedName("contentDetails") val contentDetails: PlaylistItemContentDetails?
)

data class PlaylistItemSnippet(
    @SerializedName("publishedAt") val publishedAt: String,
    @SerializedName("channelId") val channelId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("thumbnails") val thumbnails: Thumbnails,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("playlistId") val playlistId: String,
    @SerializedName("position") val position: Int,
    @SerializedName("resourceId") val resourceId: PlaylistItemResourceId,
    @SerializedName("videoOwnerChannelTitle") val videoOwnerChannelTitle: String?,
    @SerializedName("videoOwnerChannelId") val videoOwnerChannelId: String?
)

data class PlaylistItemResourceId(
    @SerializedName("kind") val kind: String,
    @SerializedName("videoId") val videoId: String
)

data class PlaylistItemContentDetails(
    @SerializedName("videoId") val videoId: String,
    @SerializedName("videoPublishedAt") val videoPublishedAt: String?
)

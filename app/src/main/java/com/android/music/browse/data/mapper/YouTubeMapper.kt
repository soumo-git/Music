package com.android.music.browse.data.mapper

import com.android.music.browse.data.api.model.ChannelItem
import com.android.music.browse.data.api.model.PlaylistItem
import com.android.music.browse.data.api.model.SearchItem
import com.android.music.browse.data.api.model.SubscriptionItem
import com.android.music.browse.data.api.model.VideoItem
import com.android.music.browse.data.model.YouTubeChannel
import com.android.music.browse.data.model.YouTubePlaylist
import com.android.music.browse.data.model.YouTubeVideo
import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Mapper class to convert YouTube API responses to domain models.
 * Handles all data transformation and formatting.
 */
object YouTubeMapper {

    /**
     * Convert SearchItem to YouTubeVideo
     */
    fun SearchItem.toYouTubeVideo(): YouTubeVideo? {
        val videoId = id.videoId ?: return null
        return YouTubeVideo(
            id = videoId,
            title = snippet.title,
            channelName = snippet.channelTitle,
            channelId = snippet.channelId,
            channelThumbnail = null,
            thumbnailUrl = snippet.thumbnails.getMediumQuality() ?: "",
            duration = "", // Duration not available in search results
            viewCount = "", // View count not available in search results
            likeCount = null,
            publishedAt = formatPublishedDate(snippet.publishedAt),
            description = snippet.description,
            isShort = false
        )
    }

    /**
     * Convert VideoItem to YouTubeVideo
     */
    fun VideoItem.toYouTubeVideo(): YouTubeVideo {
        val isShort = contentDetails?.duration?.let { parseDuration(it) }?.let { it <= 60 } ?: false
        return YouTubeVideo(
            id = id,
            title = snippet?.title ?: "",
            channelName = snippet?.channelTitle ?: "",
            channelId = snippet?.channelId ?: "",
            channelThumbnail = null,
            thumbnailUrl = snippet?.thumbnails?.getMediumQuality() ?: "",
            duration = contentDetails?.duration?.let { formatDuration(it) } ?: "",
            viewCount = statistics?.viewCount?.let { formatViewCount(it) } ?: "",
            likeCount = statistics?.likeCount?.let { formatCount(it) },
            publishedAt = snippet?.publishedAt?.let { formatPublishedDate(it) } ?: "",
            description = snippet?.description,
            isShort = isShort
        )
    }

    /**
     * Convert ChannelItem to YouTubeChannel
     */
    fun ChannelItem.toYouTubeChannel(): YouTubeChannel {
        return YouTubeChannel(
            id = id,
            name = snippet?.title ?: "",
            thumbnailUrl = snippet?.thumbnails?.getMediumQuality(),
            subscriberCount = statistics?.subscriberCount?.let { formatCount(it) },
            videoCount = statistics?.videoCount
        )
    }

    /**
     * Convert SubscriptionItem to YouTubeChannel
     */
    fun SubscriptionItem.toYouTubeChannel(): YouTubeChannel {
        return YouTubeChannel(
            id = snippet?.resourceId?.channelId ?: "",
            name = snippet?.title ?: "",
            thumbnailUrl = snippet?.thumbnails?.getMediumQuality(),
            subscriberCount = null,
            videoCount = contentDetails?.totalItemCount?.toString()
        )
    }

    /**
     * Convert PlaylistItem to YouTubePlaylist
     */
    fun PlaylistItem.toYouTubePlaylist(): YouTubePlaylist {
        return YouTubePlaylist(
            id = id,
            title = snippet?.title ?: "",
            thumbnailUrl = snippet?.thumbnails?.getMediumQuality(),
            channelName = snippet?.channelTitle ?: "",
            channelId = snippet?.channelId ?: "",
            videoCount = contentDetails?.itemCount ?: 0,
            description = snippet?.description
        )
    }

    /**
     * Format ISO 8601 duration (PT4M13S) to human readable (4:13)
     */
    fun formatDuration(isoDuration: String): String {
        return try {
            val duration = Duration.parse(isoDuration)
            val hours = duration.toHours()
            val minutes = duration.toMinutesPart()
            val seconds = duration.toSecondsPart()

            when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
                else -> String.format("%d:%02d", minutes, seconds)
            }
        } catch (_: Exception) {
            isoDuration
        }
    }

    /**
     * Parse ISO 8601 duration to seconds
     */
    fun parseDuration(isoDuration: String): Long {
        return try {
            Duration.parse(isoDuration).seconds
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Format view count (1234567 -> 1.2M views)
     */
    fun formatViewCount(count: String): String {
        return try {
            "${formatCount(count)} views"
        } catch (_: Exception) {
            count
        }
    }

    /**
     * Format large numbers (1234567 -> 1.2M)
     */
    fun formatCount(count: String): String {
        return try {
            val num = count.toLong()
            when {
                num >= 1_000_000_000 -> "${DecimalFormat("#.#").format(num / 1_000_000_000.0)}B"
                num >= 1_000_000 -> "${DecimalFormat("#.#").format(num / 1_000_000.0)}M"
                num >= 1_000 -> "${DecimalFormat("#.#").format(num / 1_000.0)}K"
                else -> count
            }
        } catch (_: Exception) {
            count
        }
    }

    /**
     * Format published date to relative time (2 days ago, 1 week ago, etc.)
     */
    fun formatPublishedDate(isoDate: String): String {
        return try {
            val instant = Instant.parse(isoDate)
            val now = Instant.now()
            
            val minutes = ChronoUnit.MINUTES.between(instant, now)
            val hours = ChronoUnit.HOURS.between(instant, now)
            val days = ChronoUnit.DAYS.between(instant, now)
            val weeks = days / 7
            val months = days / 30
            val years = days / 365

            when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "$minutes minutes ago"
                hours < 24 -> "$hours hours ago"
                days < 7 -> "$days days ago"
                weeks < 4 -> "$weeks weeks ago"
                months < 12 -> "$months months ago"
                else -> "$years years ago"
            }
        } catch (_: Exception) {
            isoDate
        }
    }
}

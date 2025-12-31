package com.android.music.browse.data.mapper

import com.android.music.browse.data.api.model.spotify.*
import com.android.music.browse.data.model.*
import java.text.DecimalFormat

/**
 * Mapper class to convert Spotify API responses to domain models.
 */
object SpotifyMapper {

    fun SpotifyTrackResponse.toSpotifyTrack(): SpotifyTrack {
        return SpotifyTrack(
            id = id,
            name = name,
            artistName = artists.firstOrNull()?.name ?: "Unknown Artist",
            artistId = artists.firstOrNull()?.id ?: "",
            albumName = album.name,
            albumId = album.id,
            albumArtUrl = album.images.firstOrNull()?.url,
            duration = formatDuration(durationMs),
            previewUrl = previewUrl,
            uri = uri,
            explicit = explicit,
            popularity = popularity
        )
    }

    fun SpotifyAlbumResponse.toSpotifyAlbum(): SpotifyAlbum {
        return SpotifyAlbum(
            id = id,
            name = name,
            artistName = artists.firstOrNull()?.name ?: "Unknown Artist",
            artistId = artists.firstOrNull()?.id ?: "",
            imageUrl = images.firstOrNull()?.url,
            releaseDate = formatReleaseDate(releaseDate),
            totalTracks = totalTracks,
            uri = uri
        )
    }

    fun SpotifyArtistResponse.toSpotifyArtist(): SpotifyArtist {
        return SpotifyArtist(
            id = id,
            name = name,
            imageUrl = images.firstOrNull()?.url,
            followers = followers?.total?.let { formatFollowers(it) },
            genres = genres,
            uri = uri
        )
    }

    fun SpotifyPlaylistResponse.toSpotifyPlaylist(): SpotifyPlaylist {
        return SpotifyPlaylist(
            id = id,
            name = name,
            description = description,
            imageUrl = images.firstOrNull()?.url,
            owner = owner.displayName ?: owner.id,
            trackCount = tracks.total,
            uri = uri,
            isPublic = public ?: true
        )
    }

    fun SpotifyUserProfileResponse.toSpotifyUserProfile(): SpotifyUserProfile {
        return SpotifyUserProfile(
            id = id,
            displayName = displayName,
            email = email,
            imageUrl = images.firstOrNull()?.url,
            followers = followers?.total?.let { formatFollowers(it) },
            country = country,
            product = product
        )
    }

    /**
     * Format duration from milliseconds to MM:SS
     */
    private fun formatDuration(durationMs: Int): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    /**
     * Format followers count (1234567 -> 1.2M)
     */
    private fun formatFollowers(count: Int): String {
        return when {
            count >= 1_000_000 -> "${DecimalFormat("#.#").format(count / 1_000_000.0)}M"
            count >= 1_000 -> "${DecimalFormat("#.#").format(count / 1_000.0)}K"
            else -> count.toString()
        }
    }

    /**
     * Format release date to readable format
     */
    private fun formatReleaseDate(date: String): String {
        return try {
            // Spotify returns dates in YYYY-MM-DD or YYYY format
            when {
                date.length == 4 -> date // Just year
                date.length >= 7 -> {
                    val parts = date.split("-")
                    val year = parts[0]
                    val month = parts.getOrNull(1)?.toIntOrNull()
                    val monthName = month?.let { getMonthName(it) } ?: ""
                    if (monthName.isNotEmpty()) "$monthName $year" else year
                }
                else -> date
            }
        } catch (e: Exception) {
            date
        }
    }

    private fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "Jan"
            2 -> "Feb"
            3 -> "Mar"
            4 -> "Apr"
            5 -> "May"
            6 -> "Jun"
            7 -> "Jul"
            8 -> "Aug"
            9 -> "Sep"
            10 -> "Oct"
            11 -> "Nov"
            12 -> "Dec"
            else -> ""
        }
    }
}

package com.android.music.browse.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SpotifyTrack(
    val id: String,
    val name: String,
    val artistName: String,
    val artistId: String,
    val albumName: String,
    val albumId: String,
    val albumArtUrl: String?,
    val duration: String,
    val previewUrl: String?,
    val uri: String,
    val explicit: Boolean = false,
    val popularity: Int = 0
) : Parcelable

@Parcelize
data class SpotifyAlbum(
    val id: String,
    val name: String,
    val artistName: String,
    val artistId: String,
    val imageUrl: String?,
    val releaseDate: String,
    val totalTracks: Int,
    val uri: String
) : Parcelable

@Parcelize
data class SpotifyArtist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val followers: String?,
    val genres: List<String> = emptyList(),
    val uri: String
) : Parcelable

@Parcelize
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val owner: String,
    val trackCount: Int,
    val uri: String,
    val isPublic: Boolean = true
) : Parcelable

data class SpotifySearchResult(
    val tracks: List<SpotifyTrack> = emptyList(),
    val albums: List<SpotifyAlbum> = emptyList(),
    val artists: List<SpotifyArtist> = emptyList(),
    val playlists: List<SpotifyPlaylist> = emptyList()
)

data class SpotifyHomeContent(
    val featuredPlaylists: List<SpotifyPlaylist> = emptyList(),
    val newReleases: List<SpotifyAlbum> = emptyList(),
    val recommendations: List<SpotifyTrack> = emptyList()
)

data class SpotifyUserProfile(
    val id: String,
    val displayName: String?,
    val email: String?,
    val imageUrl: String?,
    val followers: String?,
    val country: String?,
    val product: String? // free, premium
)

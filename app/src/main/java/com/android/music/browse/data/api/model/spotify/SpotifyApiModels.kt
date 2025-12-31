package com.android.music.browse.data.api.model.spotify

import com.google.gson.annotations.SerializedName

// ========== Common Models ==========
data class SpotifyImage(
    @SerializedName("url") val url: String?,
    @SerializedName("height") val height: Int?,
    @SerializedName("width") val width: Int?
)

data class SpotifyExternalUrls(
    @SerializedName("spotify") val spotify: String?
)

data class SpotifyFollowers(
    @SerializedName("total") val total: Int?
)

// ========== Track Models ==========
data class SpotifyTrackResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("artists") val artists: List<SpotifySimpleArtist>,
    @SerializedName("album") val album: SpotifySimpleAlbum,
    @SerializedName("duration_ms") val durationMs: Int,
    @SerializedName("explicit") val explicit: Boolean,
    @SerializedName("preview_url") val previewUrl: String?,
    @SerializedName("uri") val uri: String,
    @SerializedName("popularity") val popularity: Int
)

data class SpotifySimpleArtist(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("uri") val uri: String
)

data class SpotifySimpleAlbum(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("images") val images: List<SpotifyImage>,
    @SerializedName("release_date") val releaseDate: String,
    @SerializedName("total_tracks") val totalTracks: Int,
    @SerializedName("uri") val uri: String
)

// ========== Album Models ==========
data class SpotifyAlbumResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("artists") val artists: List<SpotifySimpleArtist>,
    @SerializedName("images") val images: List<SpotifyImage>,
    @SerializedName("release_date") val releaseDate: String,
    @SerializedName("total_tracks") val totalTracks: Int,
    @SerializedName("uri") val uri: String,
    @SerializedName("tracks") val tracks: SpotifyPagingObject<SpotifyTrackResponse>?
)

data class SpotifyAlbumTracksResponse(
    @SerializedName("items") val items: List<SpotifyTrackResponse>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int
)

// ========== Artist Models ==========
data class SpotifyArtistResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("images") val images: List<SpotifyImage>,
    @SerializedName("followers") val followers: SpotifyFollowers?,
    @SerializedName("genres") val genres: List<String>,
    @SerializedName("uri") val uri: String,
    @SerializedName("popularity") val popularity: Int
)

data class SpotifyArtistTopTracksResponse(
    @SerializedName("tracks") val tracks: List<SpotifyTrackResponse>
)

data class SpotifyArtistAlbumsResponse(
    @SerializedName("items") val items: List<SpotifyAlbumResponse>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int
)

// ========== Playlist Models ==========
data class SpotifyPlaylistResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("images") val images: List<SpotifyImage>,
    @SerializedName("owner") val owner: SpotifyOwner,
    @SerializedName("tracks") val tracks: SpotifyPlaylistTracksInfo,
    @SerializedName("uri") val uri: String,
    @SerializedName("public") val public: Boolean?
)

data class SpotifyOwner(
    @SerializedName("id") val id: String,
    @SerializedName("display_name") val displayName: String?
)

data class SpotifyPlaylistTracksInfo(
    @SerializedName("total") val total: Int
)

data class SpotifyPlaylistTracksResponse(
    @SerializedName("items") val items: List<SpotifyPlaylistTrackItem>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int
)

data class SpotifyPlaylistTrackItem(
    @SerializedName("track") val track: SpotifyTrackResponse?,
    @SerializedName("added_at") val addedAt: String?
)

// ========== Search Models ==========
data class SpotifySearchResponse(
    @SerializedName("tracks") val tracks: SpotifyPagingObject<SpotifyTrackResponse>?,
    @SerializedName("albums") val albums: SpotifyPagingObject<SpotifyAlbumResponse>?,
    @SerializedName("artists") val artists: SpotifyPagingObject<SpotifyArtistResponse>?,
    @SerializedName("playlists") val playlists: SpotifyPagingObject<SpotifyPlaylistResponse>?
)

data class SpotifyPagingObject<T>(
    @SerializedName("items") val items: List<T>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("previous") val previous: String?
)

// ========== User Library Models ==========
data class SpotifySavedTracksResponse(
    @SerializedName("items") val items: List<SpotifySavedTrackItem>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int
)

data class SpotifySavedTrackItem(
    @SerializedName("track") val track: SpotifyTrackResponse,
    @SerializedName("added_at") val addedAt: String
)

data class SpotifySavedAlbumsResponse(
    @SerializedName("items") val items: List<SpotifySavedAlbumItem>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int
)

data class SpotifySavedAlbumItem(
    @SerializedName("album") val album: SpotifyAlbumResponse,
    @SerializedName("added_at") val addedAt: String
)

data class SpotifyPlaylistsResponse(
    @SerializedName("items") val items: List<SpotifyPlaylistResponse>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int
)

data class SpotifyFollowedArtistsResponse(
    @SerializedName("artists") val artists: SpotifyPagingObject<SpotifyArtistResponse>
)

// ========== Browse Models ==========
data class SpotifyFeaturedPlaylistsResponse(
    @SerializedName("message") val message: String?,
    @SerializedName("playlists") val playlists: SpotifyPagingObject<SpotifyPlaylistResponse>
)

data class SpotifyNewReleasesResponse(
    @SerializedName("albums") val albums: SpotifyPagingObject<SpotifyAlbumResponse>
)

data class SpotifyRecommendationsResponse(
    @SerializedName("tracks") val tracks: List<SpotifyTrackResponse>
)

// ========== User Profile Models ==========
data class SpotifyUserProfileResponse(
    @SerializedName("id") val id: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("images") val images: List<SpotifyImage>,
    @SerializedName("followers") val followers: SpotifyFollowers?,
    @SerializedName("country") val country: String?,
    @SerializedName("product") val product: String?
)

// ========== Personalization Models ==========
data class SpotifyTopTracksResponse(
    @SerializedName("items") val items: List<SpotifyTrackResponse>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int
)

data class SpotifyTopArtistsResponse(
    @SerializedName("items") val items: List<SpotifyArtistResponse>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int
)

data class SpotifyRecentlyPlayedResponse(
    @SerializedName("items") val items: List<SpotifyPlayHistoryItem>,
    @SerializedName("next") val next: String?,
    @SerializedName("cursors") val cursors: SpotifyCursors?
)

data class SpotifyPlayHistoryItem(
    @SerializedName("track") val track: SpotifyTrackResponse,
    @SerializedName("played_at") val playedAt: String
)

data class SpotifyCursors(
    @SerializedName("after") val after: String?,
    @SerializedName("before") val before: String?
)

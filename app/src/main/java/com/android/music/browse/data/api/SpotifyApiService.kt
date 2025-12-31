package com.android.music.browse.data.api

import com.android.music.browse.data.api.model.spotify.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Spotify Web API Service Interface
 * Documentation: https://developer.spotify.com/documentation/web-api
 */
interface SpotifyApiService {

    companion object {
        const val BASE_URL = "https://api.spotify.com/v1/"
    }

    // ========== User Profile ==========
    @GET("me")
    suspend fun getCurrentUserProfile(): Response<SpotifyUserProfileResponse>

    // ========== Browse ==========
    @GET("browse/featured-playlists")
    suspend fun getFeaturedPlaylists(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyFeaturedPlaylistsResponse>

    @GET("browse/new-releases")
    suspend fun getNewReleases(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyNewReleasesResponse>

    @GET("recommendations")
    suspend fun getRecommendations(
        @Query("seed_tracks") seedTracks: String? = null,
        @Query("seed_artists") seedArtists: String? = null,
        @Query("seed_genres") seedGenres: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<SpotifyRecommendationsResponse>

    // ========== Search ==========
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "track,album,artist,playlist",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<SpotifySearchResponse>

    // ========== Library ==========
    @GET("me/tracks")
    suspend fun getSavedTracks(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<SpotifySavedTracksResponse>

    @GET("me/albums")
    suspend fun getSavedAlbums(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<SpotifySavedAlbumsResponse>

    @GET("me/playlists")
    suspend fun getUserPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyPlaylistsResponse>

    @GET("me/following")
    suspend fun getFollowedArtists(
        @Query("type") type: String = "artist",
        @Query("limit") limit: Int = 50
    ): Response<SpotifyFollowedArtistsResponse>

    // ========== Playlists ==========
    @GET("playlists/{playlist_id}")
    suspend fun getPlaylist(
        @Path("playlist_id") playlistId: String
    ): Response<SpotifyPlaylistResponse>

    @GET("playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyPlaylistTracksResponse>

    // ========== Albums ==========
    @GET("albums/{id}")
    suspend fun getAlbum(
        @Path("id") albumId: String
    ): Response<SpotifyAlbumResponse>

    @GET("albums/{id}/tracks")
    suspend fun getAlbumTracks(
        @Path("id") albumId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyAlbumTracksResponse>

    // ========== Artists ==========
    @GET("artists/{id}")
    suspend fun getArtist(
        @Path("id") artistId: String
    ): Response<SpotifyArtistResponse>

    @GET("artists/{id}/top-tracks")
    suspend fun getArtistTopTracks(
        @Path("id") artistId: String,
        @Query("market") market: String = "US"
    ): Response<SpotifyArtistTopTracksResponse>

    @GET("artists/{id}/albums")
    suspend fun getArtistAlbums(
        @Path("id") artistId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyArtistAlbumsResponse>

    // ========== Tracks ==========
    @GET("tracks/{id}")
    suspend fun getTrack(
        @Path("id") trackId: String
    ): Response<SpotifyTrackResponse>

    // ========== Personalization ==========
    @GET("me/top/tracks")
    suspend fun getUserTopTracks(
        @Query("time_range") timeRange: String = "medium_term",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyTopTracksResponse>

    @GET("me/top/artists")
    suspend fun getUserTopArtists(
        @Query("time_range") timeRange: String = "medium_term",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyTopArtistsResponse>

    @GET("me/player/recently-played")
    suspend fun getRecentlyPlayed(
        @Query("limit") limit: Int = 50
    ): Response<SpotifyRecentlyPlayedResponse>
}

package com.android.music.browse.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages Spotify OAuth2 authentication using PKCE flow.
 * Handles authorization, token management, and refresh for Spotify API access.
 */
class SpotifyAuthManager(context: Context) {

    companion object {
        private const val TAG = "SpotifyAuthManager"

        // OAuth configuration
        private const val CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID" // TODO: Replace with actual client ID
        private const val REDIRECT_URI = "com.android.music://spotify-callback"
        
        // Scopes for Spotify API access
        private const val SCOPES = "user-read-private user-read-email " +
                "user-library-read user-library-modify " +
                "playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private " +
                "user-top-read user-read-recently-played " +
                "streaming user-read-playback-state user-modify-playback-state"
        
        // SharedPreferences keys
        private const val PREFS_NAME = "spotify_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_CODE_VERIFIER = "code_verifier"
        
        @Volatile
        private var instance: SpotifyAuthManager? = null

        fun getInstance(context: Context): SpotifyAuthManager {
            return instance ?: synchronized(this) {
                instance ?: SpotifyAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _authState = MutableStateFlow<SpotifyAuthState>(SpotifyAuthState.NotAuthenticated)
    val authState: StateFlow<SpotifyAuthState> = _authState.asStateFlow()

    init {
        checkExistingAuth()
    }

    private fun checkExistingAuth() {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val tokenExpiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            _authState.value = SpotifyAuthState.Authenticated(accessToken)
        } else if (prefs.getString(KEY_REFRESH_TOKEN, null) != null) {
            // Token expired but we have refresh token
            _authState.value = SpotifyAuthState.TokenExpired
        }
    }

    /**
     * Handle authorization callback with code
     */
    suspend fun handleAuthorizationCode(code: String): Result<String> {
        return try {
            val codeVerifier = prefs.getString(KEY_CODE_VERIFIER, null)
                ?: return Result.failure(Exception("Code verifier not found"))
            
            // Exchange code for access token
            val response = exchangeCodeForToken(code, codeVerifier)
            
            response.fold(
                onSuccess = { tokenData ->
                    saveTokenData(tokenData)
                    _authState.value = SpotifyAuthState.Authenticated(tokenData.accessToken)
                    Result.success(tokenData.accessToken)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling authorization code", e)
            _authState.value = SpotifyAuthState.Error("Authorization failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Exchange authorization code for access token
     */
    private fun exchangeCodeForToken(code: String, codeVerifier: String): Result<TokenData> {
        // TODO: Implement actual token exchange with Spotify API
        // This is a placeholder - implement with Retrofit or OkHttp
        return Result.failure(Exception("Token exchange not implemented"))
    }

    /**
     * Refresh access token using refresh token
     */
    suspend fun refreshAccessToken(): Result<String> {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            ?: return Result.failure(Exception("No refresh token available"))
        
        return try {
            // TODO: Implement token refresh with Spotify API
            Result.failure(Exception("Token refresh not implemented"))
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            Result.failure(e)
        }
    }

    /**
     * Get current access token, refreshing if needed
     */
    suspend fun getAccessToken(): String? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val tokenExpiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        
        return if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            accessToken
        } else {
            // Try to refresh
            refreshAccessToken().getOrNull()
        }
    }

    /**
     * Sign out and clear tokens
     */
    fun signOut() {
        prefs.edit().clear().apply()
        _authState.value = SpotifyAuthState.NotAuthenticated
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is SpotifyAuthState.Authenticated
    }

    private fun saveTokenData(tokenData: TokenData) {
        val expiryTime = System.currentTimeMillis() + (tokenData.expiresIn * 1000)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokenData.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokenData.refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryTime)
            .apply()
    }

    // PKCE helper methods
    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    data class TokenData(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Long
    )
}

/**
 * Represents the authentication state for Spotify
 */
sealed class SpotifyAuthState {
    object NotAuthenticated : SpotifyAuthState()
    object Loading : SpotifyAuthState()
    object TokenExpired : SpotifyAuthState()
    data class Authenticated(val accessToken: String) : SpotifyAuthState()
    data class Error(val message: String) : SpotifyAuthState()
}

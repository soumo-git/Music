package com.android.music.browse.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Manages YouTube OAuth2 authentication using Google Sign-In.
 * Handles sign-in, sign-out, and token management for YouTube API access.
 */
class YouTubeAuthManager(private val context: Context) {

    companion object {
        // YouTube Data API v3 scopes
        const val SCOPE_YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
        const val SCOPE_YOUTUBE = "https://www.googleapis.com/auth/youtube"
        @Suppress("unused")
        const val SCOPE_YOUTUBE_FORCE_SSL = "https://www.googleapis.com/auth/youtube.force-ssl"
        
        @Volatile
        private var instance: YouTubeAuthManager? = null

        fun getInstance(context: Context): YouTubeAuthManager {
            return instance ?: synchronized(this) {
                instance ?: YouTubeAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _authState = MutableStateFlow<YouTubeAuthState>(YouTubeAuthState.NotAuthenticated)
    val authState: StateFlow<YouTubeAuthState> = _authState.asStateFlow()

    private val _currentAccount = MutableStateFlow<GoogleSignInAccount?>(null)

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(
                Scope(SCOPE_YOUTUBE_READONLY),
                Scope(SCOPE_YOUTUBE)
            )
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    init {
        // Check for existing sign-in
        checkExistingSignIn()
    }

    private fun checkExistingSignIn() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && hasYouTubeScopes(account)) {
            _currentAccount.value = account
            _authState.value = YouTubeAuthState.Authenticated(account)
        }
    }

    private fun hasYouTubeScopes(account: GoogleSignInAccount): Boolean {
        val grantedScopes = account.grantedScopes
        return grantedScopes.any { it.scopeUri == SCOPE_YOUTUBE_READONLY || it.scopeUri == SCOPE_YOUTUBE }
    }

    /**
     * Get the sign-in intent to launch
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Handle the sign-in result from activity result
     */
    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            _currentAccount.value = account
            _authState.value = YouTubeAuthState.Authenticated(account)
            Result.success(account)
        } catch (e: ApiException) {
            _authState.value = YouTubeAuthState.Error("Sign-in failed: ${e.statusCode}")
            Result.failure(e)
        }
    }

    /**
     * Sign out from YouTube/Google
     */
    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
            _currentAccount.value = null
            _authState.value = YouTubeAuthState.NotAuthenticated
        } catch (e: Exception) {
            _authState.value = YouTubeAuthState.Error("Sign-out failed: ${e.message}")
        }
    }

    /**
     * Get the OAuth2 access token for API calls
     */
    fun getAccessToken(): String? {
        val account = _currentAccount.value ?: return null
        return try {
            // Get fresh token using GoogleAuthUtil
            com.google.android.gms.auth.GoogleAuthUtil.getToken(
                context,
                account.account!!,
                "oauth2:$SCOPE_YOUTUBE_READONLY $SCOPE_YOUTUBE"
            )
        } catch (e: Exception) {
            android.util.Log.e("YouTubeAuthManager", "Failed to get access token", e)
            null
        }
    }

    /**
     * Check if user is currently authenticated
     */
    fun isAuthenticated(): Boolean {
        return _currentAccount.value != null
    }

    /**
     * Get current user's display name
     */
    fun getDisplayName(): String? {
        return _currentAccount.value?.displayName
    }

    /**
     * Get current user's email
     */
    fun getEmail(): String? {
        return _currentAccount.value?.email
    }

    /**
     * Get current user's profile photo URL
     */
    fun getPhotoUrl(): String? {
        return _currentAccount.value?.photoUrl?.toString()
    }
}

/**
 * Represents the authentication state for YouTube
 */
sealed class YouTubeAuthState {
    object NotAuthenticated : YouTubeAuthState()
    object Loading : YouTubeAuthState()
    data class Authenticated(val account: GoogleSignInAccount) : YouTubeAuthState()
    data class Error(val message: String) : YouTubeAuthState()
}

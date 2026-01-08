package com.android.music.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.music.auth.AuthManager
import com.android.music.data.model.User
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    
    private var authManager: AuthManager? = null
    
    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser
    
    private val _signInState = MutableLiveData<SignInState>()
    val signInState: LiveData<SignInState> = _signInState
    
    private val _showSignInPrompt = MutableLiveData(false)
    val showSignInPrompt: LiveData<Boolean> = _showSignInPrompt
    
    fun initialize(authManager: AuthManager) {
        this.authManager = authManager
        checkCurrentUser()
    }
    
    private fun checkCurrentUser() {
        val user = authManager?.getCurrentUser()
        _currentUser.value = user
        
        // Show sign-in prompt if user is not signed in
        if (user == null) {
            _showSignInPrompt.value = true
        }
    }
    
    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _signInState.value = SignInState.Loading
            
            authManager?.signInWithGoogle(account)?.let { result ->
                result.onSuccess { user ->
                    _currentUser.value = user
                    _signInState.value = SignInState.Success(user)
                    _showSignInPrompt.value = false
                }.onFailure { exception ->
                    _signInState.value = SignInState.Error(exception.message ?: "Sign in failed")
                }
            }
        }
    }

    fun dismissSignInPrompt() {
        _showSignInPrompt.value = false
    }
    
    fun refreshAuthState() {
        checkCurrentUser()
    }
    
    sealed class SignInState {
        object Loading : SignInState()
        data class Success(val user: User) : SignInState()
        data class Error(val message: String) : SignInState()
    }
}

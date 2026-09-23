package com.example.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.StudioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSignUpMode: Boolean = false,
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val showSupabaseConfig: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    val repository = StudioRepository(application)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val url = repository.getSetting("supabase_url") ?: "https://iuixhbuhrmmgunkttwem.supabase.co"
            val key = repository.getSetting("supabase_anon_key") ?: ""
            _uiState.value = _uiState.value.copy(supabaseUrl = url, supabaseKey = key)
        }
    }

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isSignUpMode = !_uiState.value.isSignUpMode,
            errorMessage = null
        )
    }

    fun toggleSupabaseConfig(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSupabaseConfig = show)
    }

    fun onSupabaseUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(supabaseUrl = url)
    }

    fun onSupabaseKeyChange(key: String) {
        _uiState.value = _uiState.value.copy(supabaseKey = key)
    }

    fun saveSupabaseConfig() {
        viewModelScope.launch {
            repository.saveSetting("supabase_url", _uiState.value.supabaseUrl)
            repository.saveSetting("supabase_anon_key", _uiState.value.supabaseKey)
            _uiState.value = _uiState.value.copy(showSupabaseConfig = false)
        }
    }

    fun loginWithProvider(provider: String, context: android.content.Context) {
        val url = repository.supabaseClient.getOAuthUrl(provider)
        if (url.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please configure Supabase in settings first.")
            return
        }
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = "Failed to open browser for $provider.")
        }
    }

    fun authenticate(onSuccess: () -> Unit) {
        val email = _uiState.value.email.trim()
        val pass = _uiState.value.password.trim()

        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter email and password")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // If Supabase is configured, use real Supabase Auth
            if (repository.supabaseClient.isConfigured()) {
                val res = if (_uiState.value.isSignUpMode) {
                    repository.supabaseClient.signUp(email, pass)
                } else {
                    val loginRes = repository.supabaseClient.signIn(email, pass)
                    if (loginRes.isSuccess) {
                        val (user, token) = loginRes.getOrThrow()
                        repository.saveSetting("supabase_token", token)
                        Result.success(user)
                    } else {
                        Result.failure(loginRes.exceptionOrNull() ?: Exception("Login error"))
                    }
                }

                if (res.isSuccess) {
                    val user = res.getOrThrow()
                    repository.saveUser(user)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = res.exceptionOrNull()?.message ?: "Authentication failed"
                    )
                }
            } else {
                // Direct local authentication with persistent Room storage
                val user = UserProfileEntity(
                    id = email.hashCode().toString(),
                    email = email,
                    username = email.substringBefore("@"),
                    planTier = "Unlimited VIP",
                    credits = 1000000
                )
                repository.saveUser(user)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            }
        }
    }
}

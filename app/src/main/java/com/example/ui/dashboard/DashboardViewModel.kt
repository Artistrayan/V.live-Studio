package com.example.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.StudioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isCreatingProject: Boolean = false,
    val isSyncing: Boolean = false,
    val showSettings: Boolean = false,
    val geminiKey: String = "",
    val geminiModel: String = "gemini-2.5-pro",
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val syncStatusMessage: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    val repository = StudioRepository(application)

    val projects: StateFlow<List<ProjectEntity>> = repository.getProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUser: StateFlow<UserProfileEntity?> = repository.getCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            val key = repository.getSetting("gemini_api_key") ?: ""
            val model = repository.getSetting("gemini_model") ?: "gemini-2.5-pro"
            val url = repository.getSetting("supabase_url") ?: "https://iuixhbuhrmmgunkttwem.supabase.co"
            val sbKey = repository.getSetting("supabase_anon_key") ?: ""
            _uiState.value = _uiState.value.copy(
                geminiKey = key,
                geminiModel = model,
                supabaseUrl = url,
                supabaseKey = sbKey
            )
        }
    }

    fun toggleSettings(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSettings = show)
        if (show) loadSettings()
    }

    fun saveSettings(key: String, model: String, url: String, sbKey: String) {
        viewModelScope.launch {
            repository.saveSetting("gemini_api_key", key)
            repository.saveSetting("gemini_model", model)
            repository.saveSetting("supabase_url", url)
            repository.saveSetting("supabase_anon_key", sbKey)
            _uiState.value = _uiState.value.copy(
                showSettings = false,
                geminiKey = key,
                geminiModel = model,
                supabaseUrl = url,
                supabaseKey = sbKey
            )
        }
    }

    fun testDatabaseConnection(url: String, key: String, onResult: (isSuccess: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            repository.saveSetting("supabase_url", url)
            repository.saveSetting("supabase_anon_key", key)
            val res = repository.testDatabaseConnection()
            if (res.isSuccess) {
                onResult(true, res.getOrThrow())
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Connection failed")
            }
        }
    }

    fun createProject(name: String, description: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingProject = true)
            val project = repository.createProject(name, description)
            _uiState.value = _uiState.value.copy(isCreatingProject = false)
            onCreated(project.id)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    fun syncWithSupabase() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncStatusMessage = null)
            val res = repository.syncWithSupabase()
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncStatusMessage = "Cloud Sync completed successfully"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncStatusMessage = "Sync failed: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            repository.clearUser()
            onLogout()
        }
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncStatusMessage = null)
    }
}

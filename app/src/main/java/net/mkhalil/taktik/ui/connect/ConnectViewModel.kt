package net.mkhalil.taktik.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.mkhalil.taktik.data.remote.TikTokUserApi
import net.mkhalil.taktik.data.repo.LiveSessionManager

class ConnectViewModel(
    private val sessionManager: LiveSessionManager,
    private val userApi: TikTokUserApi,
) : ViewModel() {

    data class UiState(
        val handle: String = "",
        val checking: Boolean = false,
        val starting: Boolean = false,
        val preview: TikTokUserApi.Result? = null,
        val error: String? = null,
        val startedSessionId: Long? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onHandleChange(value: String) {
        _state.update { it.copy(handle = value, error = null, preview = null) }
    }

    fun checkProfile() {
        val handle = _state.value.handle.trim().removePrefix("@")
        if (handle.isBlank()) {
            _state.update { it.copy(error = "Enter a TikTok @handle") }
            return
        }
        _state.update { it.copy(checking = true, error = null, preview = null) }
        viewModelScope.launch {
            val result = runCatching { userApi.fetchProfile(handle) }.getOrNull()
            _state.update {
                if (result == null) it.copy(checking = false, error = "Couldn't find @$handle")
                else it.copy(checking = false, preview = result, error = null)
            }
        }
    }

    fun startSession() {
        val handle = _state.value.handle.trim().removePrefix("@")
        if (handle.isBlank()) {
            _state.update { it.copy(error = "Enter a TikTok @handle") }
            return
        }
        _state.update { it.copy(starting = true, error = null) }
        viewModelScope.launch {
            val result = sessionManager.start(handle)
            _state.update { current ->
                result.fold(
                    onSuccess = { id -> current.copy(starting = false, startedSessionId = id) },
                    onFailure = { e -> current.copy(starting = false, error = e.message ?: "Failed to start") },
                )
            }
        }
    }

    fun consumedNavigation() {
        _state.update { it.copy(startedSessionId = null) }
    }
}

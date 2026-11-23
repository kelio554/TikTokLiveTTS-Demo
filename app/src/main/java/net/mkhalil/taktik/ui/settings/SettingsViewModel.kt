package net.mkhalil.taktik.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.mkhalil.taktik.data.repo.SettingsRepository

class SettingsViewModel(
    private val repo: SettingsRepository,
) : ViewModel() {

    val state = repo.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsRepository.Settings(),
    )

    fun setCoinRate(rate: Double) = viewModelScope.launch { repo.setCoinRate(rate) }
    fun setAutoReconnect(enabled: Boolean) = viewModelScope.launch { repo.setAutoReconnect(enabled) }
    fun setDarkTheme(value: Boolean?) = viewModelScope.launch { repo.setDarkTheme(value) }
    fun setLiveSourceMode(mode: SettingsRepository.LiveSourceMode) =
        viewModelScope.launch { repo.setLiveSourceMode(mode) }
    fun setSignApiKey(key: String) = viewModelScope.launch { repo.setSignApiKey(key) }
    fun setSignBaseUrl(url: String) = viewModelScope.launch { repo.setSignBaseUrl(url) }
    fun setBackgroundTracking(enabled: Boolean) =
        viewModelScope.launch { repo.setBackgroundTracking(enabled) }
    fun setTtsEnabled(enabled: Boolean) = viewModelScope.launch { repo.setTtsEnabled(enabled) }
}

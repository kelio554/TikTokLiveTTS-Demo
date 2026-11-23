package net.mkhalil.taktik.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "taktik_settings")

class SettingsRepository(
    private val context: Context,
) {
    enum class LiveSourceMode { MOCK, WEBCAST }

    data class Settings(
        val coinToUsd: Double = DEFAULT_COIN_USD,
        val autoReconnect: Boolean = true,
        val darkTheme: Boolean? = null,
        val liveSourceMode: LiveSourceMode = LiveSourceMode.WEBCAST,
        val signApiKey: String = "",
        val signBaseUrl: String = "",
        val backgroundTracking: Boolean = true,
        val ttsEnabled: Boolean = true,
    )

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            coinToUsd = p[KEY_COIN_USD] ?: DEFAULT_COIN_USD,
            autoReconnect = p[KEY_AUTO_RECONNECT] ?: true,
            darkTheme = p[KEY_DARK_THEME],
            liveSourceMode = p[KEY_LIVE_SOURCE]?.let { runCatching { LiveSourceMode.valueOf(it) }.getOrNull() }
                ?: LiveSourceMode.WEBCAST,
            signApiKey = p[KEY_SIGN_API_KEY].orEmpty(),
            signBaseUrl = p[KEY_SIGN_BASE_URL].orEmpty(),
            backgroundTracking = p[KEY_BACKGROUND_TRACKING] ?: true,
            ttsEnabled = p[KEY_TTS_ENABLED] ?: true,
        )
    }

    suspend fun setCoinRate(usdPerCoin: Double) = edit { it[KEY_COIN_USD] = usdPerCoin }
    suspend fun setAutoReconnect(enabled: Boolean) = edit { it[KEY_AUTO_RECONNECT] = enabled }
    suspend fun setDarkTheme(value: Boolean?) = edit {
        if (value == null) it.remove(KEY_DARK_THEME) else it[KEY_DARK_THEME] = value
    }
    suspend fun setLiveSourceMode(mode: LiveSourceMode) = edit { it[KEY_LIVE_SOURCE] = mode.name }
    suspend fun setSignApiKey(key: String) = edit { it[KEY_SIGN_API_KEY] = key }
    suspend fun setSignBaseUrl(url: String) = edit { it[KEY_SIGN_BASE_URL] = url }
    suspend fun setBackgroundTracking(enabled: Boolean) = edit { it[KEY_BACKGROUND_TRACKING] = enabled }
    suspend fun setTtsEnabled(enabled: Boolean) = edit { it[KEY_TTS_ENABLED] = enabled }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    companion object {
        const val DEFAULT_COIN_USD = 0.014

        private val KEY_COIN_USD: Preferences.Key<Double> = doublePreferencesKey("coin_usd")
        private val KEY_AUTO_RECONNECT: Preferences.Key<Boolean> = booleanPreferencesKey("auto_reconnect")
        private val KEY_DARK_THEME: Preferences.Key<Boolean> = booleanPreferencesKey("dark_theme")
        private val KEY_LIVE_SOURCE: Preferences.Key<String> = stringPreferencesKey("live_source_mode")
        private val KEY_SIGN_API_KEY: Preferences.Key<String> = stringPreferencesKey("sign_api_key")
        private val KEY_SIGN_BASE_URL: Preferences.Key<String> = stringPreferencesKey("sign_base_url")
        private val KEY_BACKGROUND_TRACKING: Preferences.Key<Boolean> = booleanPreferencesKey("bg_tracking")
        private val KEY_TTS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tts_enabled")
    }
}

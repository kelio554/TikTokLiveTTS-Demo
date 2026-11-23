package net.mkhalil.taktik.di

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.mkhalil.taktik.data.db.TaktikDatabase
import net.mkhalil.taktik.data.live.LiveEventSource
import net.mkhalil.taktik.data.live.MockLiveEventSource
import net.mkhalil.taktik.data.live.SwappableLiveEventSource
import net.mkhalil.taktik.data.live.webcast.EulerstreamSignClient
import net.mkhalil.taktik.data.live.webcast.WebcastLiveEventSource
import net.mkhalil.taktik.data.remote.TikTokUserApi
import net.mkhalil.taktik.data.repo.LiveSessionManager
import net.mkhalil.taktik.data.repo.SettingsRepository
import net.mkhalil.taktik.data.tts.AndroidTtsReader
import net.mkhalil.taktik.service.TaktikLiveService
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ServiceLocator(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: TaktikDatabase = Room.databaseBuilder(
        appContext,
        TaktikDatabase::class.java,
        TaktikDatabase.DB_NAME,
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    val sessionDao get() = database.sessionDao()
    val viewerDao get() = database.viewerDao()
    val eventDao get() = database.eventDao()
    val giftDao get() = database.giftDao()

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // 0 = no read timeout, required for long-lived WS
        .pingInterval(20, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val userApi = TikTokUserApi(httpClient)

    val settingsRepo = SettingsRepository(appContext)
    val ttsReader = AndroidTtsReader(appContext)

    private val cachedSettings = settingsRepo.settings.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsRepository.Settings(),
    )

    private val mockSource = MockLiveEventSource()

    private val signClient = EulerstreamSignClient(
        client = httpClient,
        baseUrlProvider = { cachedSettings.value.signBaseUrl },
        apiKeyProvider = { cachedSettings.value.signApiKey },
    )

    private val webcastSource = WebcastLiveEventSource(
        client = httpClient,
        signClient = signClient,
        userApi = userApi,
        autoReconnect = { cachedSettings.value.autoReconnect },
    )

    private val activeSource: LiveEventSource =
        SwappableLiveEventSource(initial = pickSource(cachedSettings.value.liveSourceMode))

    init {
        scope.launch {
            cachedSettings.collect { s ->
                (activeSource as SwappableLiveEventSource).swap(pickSource(s.liveSourceMode))
            }
        }
    }

    private fun pickSource(mode: SettingsRepository.LiveSourceMode): LiveEventSource = when (mode) {
        SettingsRepository.LiveSourceMode.MOCK -> mockSource
        SettingsRepository.LiveSourceMode.WEBCAST -> webcastSource
    }

    val sessionManager = LiveSessionManager(
        sessionDao = sessionDao,
        viewerDao = viewerDao,
        eventDao = eventDao,
        source = activeSource,
        userApi = userApi,
        ttsReader = ttsReader,
        ttsEnabled = { cachedSettings.value.ttsEnabled },
        onSessionStarted = { host ->
            if (cachedSettings.value.backgroundTracking) {
                TaktikLiveService.start(appContext, host)
            }
        },
        onSessionStopped = { TaktikLiveService.stop(appContext) },
    )
}

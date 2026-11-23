package net.mkhalil.taktik.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.mkhalil.taktik.data.db.dao.EventDao
import net.mkhalil.taktik.data.db.dao.SessionDao
import net.mkhalil.taktik.data.db.dao.ViewerDao
import net.mkhalil.taktik.data.db.entities.EventEntity
import net.mkhalil.taktik.data.db.entities.SessionEntity
import net.mkhalil.taktik.data.db.entities.ViewerEntity
import net.mkhalil.taktik.data.live.LiveEventSource
import net.mkhalil.taktik.data.remote.TikTokUserApi
import net.mkhalil.taktik.domain.model.ConnectionState
import net.mkhalil.taktik.domain.model.EventType
import net.mkhalil.taktik.domain.model.HostProfile
import net.mkhalil.taktik.domain.model.LiveEvent
import net.mkhalil.taktik.domain.model.Viewer
import net.mkhalil.taktik.data.tts.AndroidTtsReader

/**
 * Owns the current live session: starts the event source, persists events
 * and viewers as they arrive, resolves host + viewer regions, and exposes
 * the active session id + connection state to the UI.
 */
class LiveSessionManager(
    private val sessionDao: SessionDao,
    private val viewerDao: ViewerDao,
    private val eventDao: EventDao,
    private val source: LiveEventSource,
    private val userApi: TikTokUserApi,
    private val ttsReader: AndroidTtsReader,
    private val ttsEnabled: () -> Boolean,
    /** Called when a session starts so a foreground service can be brought up. */
    private val onSessionStarted: (host: String) -> Unit = {},
    /** Called when a session stops so the foreground service can be torn down. */
    private val onSessionStopped: () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    val connectionState: kotlinx.coroutines.flow.Flow<ConnectionState> = source.state
    val roomInfo: kotlinx.coroutines.flow.Flow<net.mkhalil.taktik.domain.model.LiveRoomInfo> = source.roomInfo

    private var pumpJob: Job? = null
    private var totalsJob: Job? = null

    suspend fun start(handle: String): Result<Long> = runCatching {
        stop()

        val profile = userApi.fetchProfile(handle)
        val host = HostProfile(
            uniqueId = profile?.uniqueId ?: handle.removePrefix("@"),
            nickname = profile?.nickname,
            avatarUrl = profile?.avatarUrl,
            roomId = profile?.roomId,
            isLive = profile?.isLive == true,
        )

        val sessionId = sessionDao.insert(
            SessionEntity(
                host_unique_id = host.uniqueId,
                host_nickname = host.nickname,
                host_avatar_url = host.avatarUrl,
                room_id = host.roomId,
                started_at = System.currentTimeMillis(),
            )
        )
        _activeSessionId.value = sessionId

        pumpJob = scope.launch { pumpEvents(sessionId) }
        totalsJob = scope.launch {
            // Recompute totals periodically so the dashboard counters stay accurate.
            while (true) {
                kotlinx.coroutines.delay(1500)
                sessionDao.recomputeTotals(sessionId)
            }
        }

        source.connect(host)
        onSessionStarted(host.uniqueId)
        sessionId
    }

    suspend fun stop() {
        ttsReader.stop()
        pumpJob?.cancel(); pumpJob = null
        totalsJob?.cancel(); totalsJob = null
        source.disconnect()
        _activeSessionId.value?.let {
            sessionDao.recomputeTotals(it)
            sessionDao.markEnded(it, System.currentTimeMillis())
        }
        _activeSessionId.value = null
        onSessionStopped()
    }

    private suspend fun pumpEvents(sessionId: Long) {
        source.events.collect { event ->
            if (event is LiveEvent.Comment && ttsEnabled()) {
                ttsReader.speakComment(event.text)
            }
            persistViewer(event.viewer)
            persistEvent(sessionId, event)
        }
    }

    private suspend fun persistViewer(viewer: Viewer) {
        val now = System.currentTimeMillis()
        val existing = viewerDao.getById(viewer.userId)
        if (existing == null) {
            viewerDao.upsert(
                ViewerEntity(
                    user_id = viewer.userId,
                    unique_id = viewer.uniqueId,
                    nickname = viewer.nickname,
                    avatar_url = viewer.avatarUrl,
                    region = viewer.region,
                    region_code = viewer.regionCode,
                    verified = viewer.verified,
                    first_seen_at = now,
                    last_seen_at = now,
                    region_fetched_at = if (viewer.region != null) now else null,
                )
            )
            // If the live event didn't carry a region, kick off an async lookup.
            if (viewer.region == null) {
                scope.launch { fetchAndStoreRegion(viewer) }
            }
        } else {
            viewerDao.touch(viewer.userId, now)
        }
    }

    private suspend fun fetchAndStoreRegion(viewer: Viewer) {
        val profile = runCatching { userApi.fetchProfile(viewer.uniqueId) }.getOrNull() ?: return
        viewerDao.setRegion(
            id = viewer.userId,
            region = profile.region,
            code = profile.regionCode,
            ts = System.currentTimeMillis(),
        )
    }

    private suspend fun persistEvent(sessionId: Long, event: LiveEvent) {
        val base = EventEntity(
            session_id = sessionId,
            viewer_user_id = event.viewer.userId,
            type = event.toType().name,
            created_at = event.timestamp,
        )
        val row = when (event) {
            is LiveEvent.Comment -> base.copy(comment_text = event.text)
            is LiveEvent.Gift -> base.copy(
                gift_id = event.giftId,
                gift_name = event.giftName,
                gift_count = event.count,
                gift_coins = event.coins.toLong(),
            )
            is LiveEvent.Like -> base.copy(like_count = event.count)
            is LiveEvent.Follow, is LiveEvent.Share, is LiveEvent.Join -> base
        }
        eventDao.insert(row)
    }

    private fun LiveEvent.toType(): EventType = when (this) {
        is LiveEvent.Comment -> EventType.COMMENT
        is LiveEvent.Gift -> EventType.GIFT
        is LiveEvent.Like -> EventType.LIKE
        is LiveEvent.Follow -> EventType.FOLLOW
        is LiveEvent.Share -> EventType.SHARE
        is LiveEvent.Join -> EventType.JOIN
    }
}

package net.mkhalil.taktik.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.mkhalil.taktik.data.db.dao.EventDao
import net.mkhalil.taktik.data.db.dao.SessionDao
import net.mkhalil.taktik.data.db.dao.ViewerDao
import net.mkhalil.taktik.data.db.entities.EventEntity
import net.mkhalil.taktik.data.db.entities.SessionEntity
import net.mkhalil.taktik.data.db.entities.ViewerEntity
import net.mkhalil.taktik.data.repo.LiveSessionManager
import net.mkhalil.taktik.data.repo.SettingsRepository
import net.mkhalil.taktik.domain.model.ConnectionState
import net.mkhalil.taktik.domain.model.LiveRoomInfo

@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModel(
    private val sessionManager: LiveSessionManager,
    private val sessionDao: SessionDao,
    private val eventDao: EventDao,
    private val viewerDao: ViewerDao,
    settings: SettingsRepository,
) : ViewModel() {

    data class EventWithViewer(val event: EventEntity, val viewer: ViewerEntity?)
    data class RankRow(val viewer: ViewerEntity?, val score: Long, val count: Int)

    data class EventBuckets(
        val comments: List<EventWithViewer> = emptyList(),
        val gifts: List<EventWithViewer> = emptyList(),
        val likes: List<EventWithViewer> = emptyList(),
        val follows: List<EventWithViewer> = emptyList(),
        val shares: List<EventWithViewer> = emptyList(),
    )

    data class RankBuckets(
        val gifters: List<RankRow> = emptyList(),
        val commenters: List<RankRow> = emptyList(),
        val likers: List<RankRow> = emptyList(),
    )

    data class Snapshot(
        val session: SessionEntity? = null,
        val events: EventBuckets = EventBuckets(),
        val ranks: RankBuckets = RankBuckets(),
        val connection: ConnectionState = ConnectionState.Idle,
        val coinToUsd: Double = SettingsRepository.DEFAULT_COIN_USD,
        val roomInfo: LiveRoomInfo = LiveRoomInfo(),
    )

    val activeSessionId = sessionManager.activeSessionId

    private val sessionFlow: Flow<SessionEntity?> =
        sessionManager.activeSessionId.flatMapLatest { id ->
            if (id == null) flowOf(null) else sessionDao.observeById(id)
        }

    private fun eventTypeFlow(type: String): Flow<List<EventWithViewer>> =
        sessionManager.activeSessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else eventDao.observeBySessionAndType(id, type, 200).map { bind(it) }
        }

    private val eventsFlow: Flow<EventBuckets> = combine(
        eventTypeFlow("COMMENT"),
        eventTypeFlow("GIFT"),
        eventTypeFlow("LIKE"),
        eventTypeFlow("FOLLOW"),
        eventTypeFlow("SHARE"),
    ) { c, g, l, f, s -> EventBuckets(c, g, l, f, s) }

    private val ranksFlow: Flow<RankBuckets> = combine(
        sessionManager.activeSessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else eventDao.topGifters(id, 20).map { bindRanks(it) }
        },
        sessionManager.activeSessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else eventDao.topCommenters(id, 20).map { bindRanks(it) }
        },
        sessionManager.activeSessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else eventDao.topLikers(id, 20).map { bindRanks(it) }
        },
    ) { g, c, l -> RankBuckets(g, c, l) }

    private val connectionAndRoom = combine(
        sessionManager.connectionState,
        sessionManager.roomInfo,
    ) { conn, room -> conn to room }

    val state = combine(
        sessionFlow,
        eventsFlow,
        ranksFlow,
        connectionAndRoom,
        settings.settings,
    ) { session, events, ranks, cr, sets ->
        Snapshot(
            session = session,
            events = events,
            ranks = ranks,
            connection = cr.first,
            roomInfo = cr.second,
            coinToUsd = sets.coinToUsd,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Snapshot())

    private suspend fun bind(rows: List<EventEntity>): List<EventWithViewer> = rows.map { e ->
        EventWithViewer(e, e.viewer_user_id?.let { viewerDao.getById(it) })
    }

    private suspend fun bindRanks(ranks: List<EventDao.ViewerRank>): List<RankRow> = ranks.map {
        RankRow(viewerDao.getById(it.viewer_user_id), it.score, it.event_count)
    }

    fun stop() {
        viewModelScope.launch { sessionManager.stop() }
    }
}

package net.mkhalil.taktik.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.mkhalil.taktik.data.db.dao.EventDao
import net.mkhalil.taktik.data.db.dao.SessionDao
import net.mkhalil.taktik.data.db.dao.ViewerDao
import net.mkhalil.taktik.data.db.entities.EventEntity
import net.mkhalil.taktik.data.db.entities.SessionEntity
import net.mkhalil.taktik.data.db.entities.ViewerEntity
import net.mkhalil.taktik.data.repo.SettingsRepository

class SessionDetailViewModel(
    handle: SavedStateHandle,
    private val sessionDao: SessionDao,
    private val viewerDao: ViewerDao,
    private val eventDao: EventDao,
    settings: SettingsRepository,
) : ViewModel() {

    data class EventRow(val event: EventEntity, val viewer: ViewerEntity?)

    data class UiState(
        val session: SessionEntity? = null,
        val comments: List<EventRow> = emptyList(),
        val gifts: List<EventRow> = emptyList(),
        val likes: List<EventRow> = emptyList(),
        val follows: List<EventRow> = emptyList(),
        val shares: List<EventRow> = emptyList(),
        val regions: List<EventDao.RegionRow> = emptyList(),
        val timeline: List<EventDao.TimelineRow> = emptyList(),
        val coinToUsd: Double = SettingsRepository.DEFAULT_COIN_USD,
    )

    val sessionId: Long = handle.get<String>("sessionId")?.toLongOrNull() ?: -1L

    private val sessionFlow = if (sessionId < 0) flowOf<SessionEntity?>(null) else sessionDao.observeById(sessionId)

    private fun typeFlow(type: String): Flow<List<EventRow>> =
        if (sessionId < 0) flowOf(emptyList())
        else eventDao.observeBySessionAndType(sessionId, type, 500).map { bind(it) }

    private val regionsFlow = if (sessionId < 0) flowOf(emptyList()) else eventDao.topRegions(sessionId, 12)
    private val timelineFlow = if (sessionId < 0) flowOf(emptyList()) else eventDao.timeline(sessionId, 60_000)

    val state = combine(
        sessionFlow,
        combine(typeFlow("COMMENT"), typeFlow("GIFT"), typeFlow("LIKE")) { c, g, l -> Triple(c, g, l) },
        combine(typeFlow("FOLLOW"), typeFlow("SHARE")) { f, s -> f to s },
        combine(regionsFlow, timelineFlow) { r, t -> r to t },
        settings.settings,
    ) { session, cgl, fs, rt, sets ->
        UiState(
            session = session,
            comments = cgl.first,
            gifts = cgl.second,
            likes = cgl.third,
            follows = fs.first,
            shares = fs.second,
            regions = rt.first,
            timeline = rt.second,
            coinToUsd = sets.coinToUsd,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    suspend fun loadEventsForExport(): List<EventEntity> =
        if (sessionId < 0) emptyList() else eventDao.getAllForExport(sessionId)

    private suspend fun bind(rows: List<EventEntity>): List<EventRow> = rows.map {
        EventRow(it, it.viewer_user_id?.let { id -> viewerDao.getById(id) })
    }
}

package net.mkhalil.taktik.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.mkhalil.taktik.data.db.dao.SessionDao
import net.mkhalil.taktik.data.db.dao.ViewerDao
import net.mkhalil.taktik.data.db.entities.SessionEntity
import net.mkhalil.taktik.data.db.entities.ViewerEntity
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val sessionDao: SessionDao,
    private val viewerDao: ViewerDao,
) : ViewModel() {

    enum class Tab { SESSIONS, VIEWERS }
    enum class DateFilter { ALL, TODAY, LAST_7, LAST_30 }

    data class UiState(
        val tab: Tab = Tab.SESSIONS,
        val date: DateFilter = DateFilter.ALL,
        val query: String = "",
        val sessions: List<SessionEntity> = emptyList(),
        val viewers: List<ViewerEntity> = emptyList(),
        val selectMode: Boolean = false,
        val selectedSessionIds: Set<Long> = emptySet(),
        val selectedViewerIds: Set<String> = emptySet(),
    ) {
        val selectionCount: Int
            get() = if (tab == Tab.SESSIONS) selectedSessionIds.size else selectedViewerIds.size
    }

    private val tab = MutableStateFlow(Tab.SESSIONS)
    private val date = MutableStateFlow(DateFilter.ALL)
    private val query = MutableStateFlow("")
    private val selectMode = MutableStateFlow(false)
    private val selectedSessionIds = MutableStateFlow<Set<Long>>(emptySet())
    private val selectedViewerIds = MutableStateFlow<Set<String>>(emptySet())

    private val sessions = date.flatMapLatest { df ->
        when (df) {
            DateFilter.ALL -> sessionDao.observeAll()
            DateFilter.TODAY -> sessionDao.observeByDateRange(startOfDay(0), endOfDay(0))
            DateFilter.LAST_7 -> sessionDao.observeByDateRange(startOfDay(-6), endOfDay(0))
            DateFilter.LAST_30 -> sessionDao.observeByDateRange(startOfDay(-29), endOfDay(0))
        }
    }

    private val viewers = viewerDao.observeRecent(300)

    val state = combine(
        combine(tab, date, query) { t, d, q -> Triple(t, d, q) },
        sessions,
        viewers,
        combine(selectMode, selectedSessionIds, selectedViewerIds) { sm, ss, sv -> Triple(sm, ss, sv) },
    ) { tdq, s, v, sel ->
        val (t, d, q) = tdq
        val (sm, ss, sv) = sel
        val filteredSessions = if (q.isBlank()) s else s.filter {
            it.host_unique_id.contains(q, true) ||
                (it.host_nickname?.contains(q, true) == true)
        }
        val filteredViewers = if (q.isBlank()) v else v.filter {
            it.unique_id.contains(q, true) ||
                (it.nickname?.contains(q, true) == true) ||
                (it.region?.contains(q, true) == true)
        }
        // Drop selected ids that no longer exist (e.g., after a delete).
        val validSessionIds = filteredSessions.mapTo(HashSet()) { it.id }
        val validViewerIds = filteredViewers.mapTo(HashSet()) { it.user_id }
        UiState(
            tab = t,
            date = d,
            query = q,
            sessions = filteredSessions,
            viewers = filteredViewers,
            selectMode = sm,
            selectedSessionIds = ss intersect validSessionIds,
            selectedViewerIds = sv intersect validViewerIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setTab(t: Tab) {
        tab.update { t }
        // Reset selection when switching tabs to avoid carrying stale state.
        exitSelectMode()
    }
    fun setDate(d: DateFilter) { date.update { d } }
    fun setQuery(s: String) { query.update { s } }

    fun enterSelectMode() { selectMode.update { true } }

    fun exitSelectMode() {
        selectMode.update { false }
        selectedSessionIds.update { emptySet() }
        selectedViewerIds.update { emptySet() }
    }

    fun toggleSelectSession(id: Long) {
        selectMode.update { true }
        selectedSessionIds.update { if (id in it) it - id else it + id }
    }

    fun toggleSelectViewer(id: String) {
        selectMode.update { true }
        selectedViewerIds.update { if (id in it) it - id else it + id }
    }

    fun selectAll() {
        val s = state.value
        if (s.tab == Tab.SESSIONS) {
            selectedSessionIds.update { s.sessions.mapTo(HashSet()) { it.id } }
        } else {
            selectedViewerIds.update { s.viewers.mapTo(HashSet()) { it.user_id } }
        }
        selectMode.update { true }
    }

    fun deleteSelected() {
        val s = state.value
        viewModelScope.launch {
            if (s.tab == Tab.SESSIONS) {
                val ids = s.selectedSessionIds.toList()
                if (ids.isNotEmpty()) sessionDao.deleteByIds(ids)
            } else {
                val ids = s.selectedViewerIds.toList()
                if (ids.isNotEmpty()) viewerDao.deleteByIds(ids)
            }
            exitSelectMode()
        }
    }

    fun deleteAll() {
        val s = state.value
        viewModelScope.launch {
            if (s.tab == Tab.SESSIONS) sessionDao.deleteAll() else viewerDao.deleteAll()
            exitSelectMode()
        }
    }

    private fun startOfDay(offsetDays: Int): Long {
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, offsetDays)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    private fun endOfDay(offsetDays: Int): Long {
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, offsetDays)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }
        return c.timeInMillis
    }
}

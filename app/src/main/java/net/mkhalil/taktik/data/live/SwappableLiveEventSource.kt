package net.mkhalil.taktik.data.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.mkhalil.taktik.domain.model.ConnectionState
import net.mkhalil.taktik.domain.model.HostProfile
import net.mkhalil.taktik.domain.model.LiveEvent
import net.mkhalil.taktik.domain.model.LiveRoomInfo

/**
 * A [LiveEventSource] facade that delegates to a swappable underlying source.
 *
 * Lets the rest of the app hold a single [LiveEventSource] reference while
 * the user changes the active mode (mock ↔ webcast) in Settings.
 *
 * When the underlying source is swapped, an in-flight connection on the old
 * source is torn down; the new source must be reconnected by the caller
 * (typically [net.mkhalil.taktik.data.repo.LiveSessionManager]).
 */
class SwappableLiveEventSource(initial: LiveEventSource) : LiveEventSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: Flow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 256)
    override val events: Flow<LiveEvent> = _events.asSharedFlow()

    private val _roomInfo = MutableStateFlow(LiveRoomInfo())
    override val roomInfo: Flow<LiveRoomInfo> = _roomInfo.asStateFlow()

    @Volatile private var current: LiveEventSource = initial
    private var stateJob: Job? = null
    private var eventJob: Job? = null
    private var roomJob: Job? = null
    private var lastHost: HostProfile? = null

    init { rewire(initial) }

    fun swap(source: LiveEventSource) {
        if (source === current) return
        val host = lastHost
        scope.launch {
            current.disconnect()
            current = source
            rewire(source)
            if (host != null) source.connect(host)
        }
    }

    private fun rewire(source: LiveEventSource) {
        stateJob?.cancel()
        eventJob?.cancel()
        roomJob?.cancel()
        stateJob = scope.launch { source.state.collect { _state.value = it } }
        eventJob = scope.launch { source.events.collect { _events.tryEmit(it) } }
        roomJob = scope.launch { source.roomInfo.collect { _roomInfo.value = it } }
    }

    override suspend fun connect(host: HostProfile) {
        lastHost = host
        current.connect(host)
    }

    override suspend fun disconnect() {
        lastHost = null
        current.disconnect()
    }
}

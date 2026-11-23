package net.mkhalil.taktik.data.live

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import net.mkhalil.taktik.domain.model.ConnectionState
import net.mkhalil.taktik.domain.model.HostProfile
import net.mkhalil.taktik.domain.model.LiveEvent
import net.mkhalil.taktik.domain.model.LiveRoomInfo

interface LiveEventSource {
    val state: Flow<ConnectionState>
    val events: Flow<LiveEvent>

    /** Live room snapshot (viewer count, etc.). Defaults to an empty stream. */
    val roomInfo: Flow<LiveRoomInfo>
        get() = MutableStateFlow(LiveRoomInfo())

    suspend fun connect(host: HostProfile)
    suspend fun disconnect()
}

package net.mkhalil.taktik.data.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.mkhalil.taktik.domain.model.ConnectionState
import net.mkhalil.taktik.domain.model.HostProfile
import net.mkhalil.taktik.domain.model.LiveEvent
import net.mkhalil.taktik.domain.model.LiveRoomInfo
import net.mkhalil.taktik.domain.model.Viewer
import kotlin.random.Random

/**
 * Synthetic event source used to develop and validate the pipeline end-to-end
 * without a real TikTok WebSocket. Emits comments, gifts, likes, follows,
 * shares, and joins at randomised intervals.
 */
class MockLiveEventSource : LiveEventSource {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    private val _roomInfo = MutableStateFlow(LiveRoomInfo())
    override val roomInfo = _roomInfo.asStateFlow()

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    override suspend fun connect(host: HostProfile) {
        disconnect()
        _state.value = ConnectionState.Connecting
        delay(400)
        _state.value = ConnectionState.Connected

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        job = s.launch {
            while (true) {
                delay(Random.nextLong(250, 900))
                _events.tryEmit(randomEvent())
            }
        }
        // Synthetic viewer-count drift so the UI always has something to show.
        s.launch {
            var count = Random.nextInt(300, 900)
            var total = count.toLong() * 4
            while (true) {
                _roomInfo.value = LiveRoomInfo(viewerCount = count, totalViewers = total)
                delay(Random.nextLong(2500, 5000))
                count = (count + Random.nextInt(-25, 35)).coerceAtLeast(10)
                total += Random.nextInt(0, 12)
            }
        }
    }

    override suspend fun disconnect() {
        job?.cancel()
        scope?.cancel()
        job = null
        scope = null
        if (_state.value !is ConnectionState.Idle) {
            _state.value = ConnectionState.Disconnected("Mock stopped")
        }
    }

    private fun randomEvent(): LiveEvent {
        val viewer = viewers.random()
        val ts = System.currentTimeMillis()
        return when (Random.nextInt(100)) {
            in 0..49 -> LiveEvent.Comment(viewer, ts, comments.random())
            in 50..74 -> LiveEvent.Like(viewer, ts, Random.nextInt(1, 25))
            in 75..89 -> {
                val gift = gifts.random()
                LiveEvent.Gift(
                    viewer = viewer,
                    timestamp = ts,
                    giftId = gift.first,
                    giftName = gift.second,
                    count = Random.nextInt(1, 4),
                    coins = gift.third,
                )
            }
            in 90..95 -> LiveEvent.Follow(viewer, ts)
            in 96..98 -> LiveEvent.Share(viewer, ts)
            else -> LiveEvent.Join(viewer, ts)
        }
    }

    private val viewers = listOf(
        Viewer("u_1001", "amine.dev", "Amine", null, region = "Morocco", regionCode = "MA"),
        Viewer("u_1002", "lina.live", "Lina", null, region = "France", regionCode = "FR"),
        Viewer("u_1003", "khaled77", "Khaled", null, region = "United Arab Emirates", regionCode = "AE"),
        Viewer("u_1004", "mariam_x", "Mariam", null, region = "Egypt", regionCode = "EG"),
        Viewer("u_1005", "noah.tv", "Noah", null, region = "United States", regionCode = "US"),
        Viewer("u_1006", "yara_q", "Yara", null, region = "Saudi Arabia", regionCode = "SA"),
        Viewer("u_1007", "sami.beat", "Sami", null, region = "Lebanon", regionCode = "LB"),
        Viewer("u_1008", "elena.fr", "Elena", null, region = "Italy", regionCode = "IT"),
        Viewer("u_1009", "tariq.dz", "Tariq", null, region = "Algeria", regionCode = "DZ"),
        Viewer("u_1010", "nora_uk", "Nora", null, region = "United Kingdom", regionCode = "GB"),
    )

    private val comments = listOf(
        "Salam from Morocco!", "Love this stream", "First time here",
        "GG", "Hi!", "Sending love", "Where are you from?", "Play more please",
        "Awesome content", "LOL", "Drop a follow", "Hello everyone",
        "Allez allez", "Yala 3lik", "Big fan!"
    )

    private val gifts = listOf(
        Triple(5655L, "Rose", 1),
        Triple(5827L, "TikTok", 1),
        Triple(5586L, "Heart Me", 1),
        Triple(5879L, "Finger Heart", 5),
        Triple(5485L, "Perfume", 20),
        Triple(5972L, "Doughnut", 30),
        Triple(6034L, "Hand Hearts", 50),
        Triple(7787L, "Sunglasses", 199),
        Triple(7934L, "Galaxy", 1000),
        Triple(8913L, "Lion", 29999),
    )
}

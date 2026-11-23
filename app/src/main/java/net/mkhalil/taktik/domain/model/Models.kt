package net.mkhalil.taktik.domain.model

enum class EventType { COMMENT, GIFT, LIKE, FOLLOW, SHARE, JOIN }

data class Viewer(
    val userId: String,
    val uniqueId: String,
    val nickname: String?,
    val avatarUrl: String?,
    val region: String? = null,
    val regionCode: String? = null,
    val verified: Boolean = false,
)

sealed class LiveEvent {
    abstract val viewer: Viewer
    abstract val timestamp: Long

    data class Comment(
        override val viewer: Viewer,
        override val timestamp: Long,
        val text: String,
    ) : LiveEvent()

    data class Gift(
        override val viewer: Viewer,
        override val timestamp: Long,
        val giftId: Long,
        val giftName: String,
        val count: Int,
        val coins: Int,
    ) : LiveEvent()

    data class Like(
        override val viewer: Viewer,
        override val timestamp: Long,
        val count: Int,
    ) : LiveEvent()

    data class Follow(
        override val viewer: Viewer,
        override val timestamp: Long,
    ) : LiveEvent()

    data class Share(
        override val viewer: Viewer,
        override val timestamp: Long,
    ) : LiveEvent()

    data class Join(
        override val viewer: Viewer,
        override val timestamp: Long,
    ) : LiveEvent()
}

data class HostProfile(
    val uniqueId: String,
    val nickname: String?,
    val avatarUrl: String?,
    val roomId: String?,
    val isLive: Boolean,
)

data class SessionStats(
    val sessionId: Long,
    val comments: Int,
    val likes: Long,
    val gifts: Int,
    val giftCoins: Long,
    val follows: Int,
    val shares: Int,
    val uniqueViewers: Int,
)

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    data class Disconnected(val reason: String?) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

/** Out-of-band info pushed by some message types (currently: viewer count). */
data class LiveRoomInfo(
    val viewerCount: Int? = null,
    val totalViewers: Long? = null,
)

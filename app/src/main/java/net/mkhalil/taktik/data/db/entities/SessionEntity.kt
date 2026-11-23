package net.mkhalil.taktik.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [Index("host_unique_id"), Index("started_at")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host_unique_id: String,
    val host_nickname: String? = null,
    val host_avatar_url: String? = null,
    val room_id: String? = null,
    val started_at: Long,
    val ended_at: Long? = null,
    val total_comments: Int = 0,
    val total_likes: Long = 0,
    val total_gifts: Int = 0,
    val total_gift_coins: Long = 0,
    val total_follows: Int = 0,
    val total_shares: Int = 0,
    val peak_viewers: Int = 0,
    val unique_viewers: Int = 0,
)

package net.mkhalil.taktik.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "viewers",
    indices = [
        Index(value = ["unique_id"], unique = true),
        Index("region_code"),
    ],
)
data class ViewerEntity(
    @PrimaryKey val user_id: String,
    val unique_id: String,
    val nickname: String? = null,
    val avatar_url: String? = null,
    val region: String? = null,
    val region_code: String? = null,
    val signature: String? = null,
    val follower_count: Long? = null,
    val following_count: Long? = null,
    val verified: Boolean = false,
    val first_seen_at: Long,
    val last_seen_at: Long,
    val region_fetched_at: Long? = null,
)

package net.mkhalil.taktik.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("session_id"),
        Index("viewer_user_id"),
        Index("created_at"),
        Index("type"),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session_id: Long,
    val viewer_user_id: String?,
    val type: String,
    val created_at: Long,
    // Comment
    val comment_text: String? = null,
    // Gift
    val gift_id: Long? = null,
    val gift_name: String? = null,
    val gift_count: Int? = null,
    val gift_coins: Long? = null,
    // Like
    val like_count: Int? = null,
    // Share / follow / join — type-only events use null payload fields
)

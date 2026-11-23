package net.mkhalil.taktik.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gifts")
data class GiftEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val coins: Int,
    val icon_url: String? = null,
)

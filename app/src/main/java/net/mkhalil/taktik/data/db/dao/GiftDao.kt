package net.mkhalil.taktik.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.mkhalil.taktik.data.db.entities.GiftEntity

@Dao
interface GiftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(gifts: List<GiftEntity>)

    @Query("SELECT * FROM gifts WHERE id = :id")
    suspend fun getById(id: Long): GiftEntity?

    @Query("SELECT * FROM gifts ORDER BY coins ASC")
    suspend fun getAll(): List<GiftEntity>

    @Query("SELECT COUNT(*) FROM gifts")
    suspend fun count(): Int
}

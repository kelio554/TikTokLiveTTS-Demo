package net.mkhalil.taktik.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.mkhalil.taktik.data.db.entities.ViewerEntity

@Dao
interface ViewerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(viewer: ViewerEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(viewer: ViewerEntity): Long

    @Query("SELECT * FROM viewers WHERE user_id = :id")
    suspend fun getById(id: String): ViewerEntity?

    @Query("SELECT * FROM viewers WHERE user_id = :id")
    fun observeById(id: String): Flow<ViewerEntity?>

    @Query("UPDATE viewers SET last_seen_at = :ts WHERE user_id = :id")
    suspend fun touch(id: String, ts: Long)

    @Query("UPDATE viewers SET region = :region, region_code = :code, region_fetched_at = :ts WHERE user_id = :id")
    suspend fun setRegion(id: String, region: String?, code: String?, ts: Long)

    @Query("SELECT * FROM viewers ORDER BY last_seen_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ViewerEntity>>

    @Query("SELECT * FROM viewers WHERE region_code IS NULL OR region_fetched_at IS NULL LIMIT :limit")
    suspend fun viewersMissingRegion(limit: Int = 25): List<ViewerEntity>

    @Query("DELETE FROM viewers WHERE user_id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM viewers")
    suspend fun deleteAll()
}

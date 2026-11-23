package net.mkhalil.taktik.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.mkhalil.taktik.data.db.entities.SessionEntity

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY started_at DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE host_unique_id = :host ORDER BY started_at DESC")
    fun observeByHost(host: String): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM sessions WHERE started_at >= :from AND started_at < :to ORDER BY started_at DESC"
    )
    fun observeByDateRange(from: Long, to: Long): Flow<List<SessionEntity>>

    @Query("UPDATE sessions SET ended_at = :endedAt WHERE id = :id")
    suspend fun markEnded(id: Long, endedAt: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query(
        """
        UPDATE sessions SET
          total_comments   = (SELECT COUNT(*) FROM events WHERE session_id = :id AND type = 'COMMENT'),
          total_likes      = COALESCE((SELECT SUM(like_count) FROM events WHERE session_id = :id AND type = 'LIKE'), 0),
          total_gifts      = (SELECT COUNT(*) FROM events WHERE session_id = :id AND type = 'GIFT'),
          total_gift_coins = COALESCE((SELECT SUM(gift_coins * gift_count) FROM events WHERE session_id = :id AND type = 'GIFT'), 0),
          total_follows    = (SELECT COUNT(*) FROM events WHERE session_id = :id AND type = 'FOLLOW'),
          total_shares     = (SELECT COUNT(*) FROM events WHERE session_id = :id AND type = 'SHARE'),
          unique_viewers   = (SELECT COUNT(DISTINCT viewer_user_id) FROM events WHERE session_id = :id AND viewer_user_id IS NOT NULL)
        WHERE id = :id
        """
    )
    suspend fun recomputeTotals(id: Long)
}

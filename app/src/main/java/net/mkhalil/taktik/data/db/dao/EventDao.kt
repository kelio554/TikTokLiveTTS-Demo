package net.mkhalil.taktik.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.mkhalil.taktik.data.db.entities.EventEntity

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Query("SELECT * FROM events WHERE session_id = :sessionId ORDER BY created_at DESC LIMIT :limit")
    fun observeBySession(sessionId: Long, limit: Int = 500): Flow<List<EventEntity>>

    @Query(
        "SELECT * FROM events WHERE session_id = :sessionId AND type = :type ORDER BY created_at DESC LIMIT :limit"
    )
    fun observeBySessionAndType(
        sessionId: Long,
        type: String,
        limit: Int = 500,
    ): Flow<List<EventEntity>>

    @Query(
        "SELECT * FROM events WHERE viewer_user_id = :viewerId ORDER BY created_at DESC LIMIT :limit"
    )
    fun observeByViewer(viewerId: String, limit: Int = 500): Flow<List<EventEntity>>

    data class ViewerRank(
        val viewer_user_id: String,
        val score: Long,
        val event_count: Int,
    )

    @Query(
        """
        SELECT viewer_user_id AS viewer_user_id,
               COALESCE(SUM(gift_coins * gift_count), 0) AS score,
               COUNT(*) AS event_count
        FROM events
        WHERE session_id = :sessionId AND type = 'GIFT' AND viewer_user_id IS NOT NULL
        GROUP BY viewer_user_id
        ORDER BY score DESC
        LIMIT :limit
        """
    )
    fun topGifters(sessionId: Long, limit: Int = 50): Flow<List<ViewerRank>>

    @Query(
        """
        SELECT viewer_user_id AS viewer_user_id,
               COUNT(*) AS score,
               COUNT(*) AS event_count
        FROM events
        WHERE session_id = :sessionId AND type = 'COMMENT' AND viewer_user_id IS NOT NULL
        GROUP BY viewer_user_id
        ORDER BY score DESC
        LIMIT :limit
        """
    )
    fun topCommenters(sessionId: Long, limit: Int = 50): Flow<List<ViewerRank>>

    @Query(
        """
        SELECT viewer_user_id AS viewer_user_id,
               COALESCE(SUM(like_count), 0) AS score,
               COUNT(*) AS event_count
        FROM events
        WHERE session_id = :sessionId AND type = 'LIKE' AND viewer_user_id IS NOT NULL
        GROUP BY viewer_user_id
        ORDER BY score DESC
        LIMIT :limit
        """
    )
    fun topLikers(sessionId: Long, limit: Int = 50): Flow<List<ViewerRank>>

    data class RegionRow(
        val region_code: String?,
        val region: String?,
        val viewers: Int,
        val events: Int,
    )

    @Query(
        """
        SELECT v.region_code AS region_code,
               v.region AS region,
               COUNT(DISTINCT v.user_id) AS viewers,
               COUNT(e.id) AS events
        FROM events e JOIN viewers v ON v.user_id = e.viewer_user_id
        WHERE e.session_id = :sessionId
        GROUP BY v.region_code, v.region
        ORDER BY viewers DESC, events DESC
        LIMIT :limit
        """
    )
    fun topRegions(sessionId: Long, limit: Int = 12): Flow<List<RegionRow>>

    data class TimelineRow(
        val bucket_ms: Long,
        val type: String,
        val count: Int,
        val coins: Long,
    )

    /**
     * Buckets events into fixed-size windows (e.g. 60_000 ms = 1 minute) per type.
     * Likes are summed by their like_count (one "like" event typically batches
     * multiple individual taps); other types are simple row counts.
     */
    @Query(
        """
        SELECT (created_at / :bucketMs) * :bucketMs AS bucket_ms,
               type AS type,
               CASE WHEN type='LIKE' THEN COALESCE(SUM(like_count), 0) ELSE COUNT(*) END AS count,
               COALESCE(SUM(CASE WHEN type='GIFT' THEN gift_coins * gift_count ELSE 0 END), 0) AS coins
        FROM events
        WHERE session_id = :sessionId
        GROUP BY bucket_ms, type
        ORDER BY bucket_ms ASC
        """
    )
    fun timeline(sessionId: Long, bucketMs: Long = 60_000): Flow<List<TimelineRow>>

    @Query("SELECT * FROM events WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun getAllForExport(sessionId: Long): List<EventEntity>
}

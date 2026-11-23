package net.mkhalil.taktik.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import net.mkhalil.taktik.data.db.dao.EventDao
import net.mkhalil.taktik.data.db.dao.GiftDao
import net.mkhalil.taktik.data.db.dao.SessionDao
import net.mkhalil.taktik.data.db.dao.ViewerDao
import net.mkhalil.taktik.data.db.entities.EventEntity
import net.mkhalil.taktik.data.db.entities.GiftEntity
import net.mkhalil.taktik.data.db.entities.SessionEntity
import net.mkhalil.taktik.data.db.entities.ViewerEntity

@Database(
    entities = [
        SessionEntity::class,
        ViewerEntity::class,
        EventEntity::class,
        GiftEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TaktikDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun viewerDao(): ViewerDao
    abstract fun eventDao(): EventDao
    abstract fun giftDao(): GiftDao

    companion object {
        const val DB_NAME = "taktik.db"
    }
}

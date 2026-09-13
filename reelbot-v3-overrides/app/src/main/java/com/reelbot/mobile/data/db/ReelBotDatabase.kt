package com.reelbot.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.reelbot.mobile.data.db.dao.ReelJobDao
import com.reelbot.mobile.data.db.dao.SourceVideoDao
import com.reelbot.mobile.data.db.entity.ReelJobEntity
import com.reelbot.mobile.data.db.entity.SourceVideoEntity

@Database(
    entities = [ReelJobEntity::class, SourceVideoEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ReelBotDatabase : RoomDatabase() {
    abstract fun reelJobDao(): ReelJobDao
    abstract fun sourceVideoDao(): SourceVideoDao

    companion object {
        @Volatile private var instance: ReelBotDatabase? = null

        fun getInstance(context: Context): ReelBotDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReelBotDatabase::class.java,
                    "reelbot.db"
                ).build().also { instance = it }
            }
    }
}

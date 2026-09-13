package com.reelbot.mobile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reelbot.mobile.data.db.entity.SourceVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceVideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: SourceVideoEntity)

    @Query("SELECT * FROM source_videos ORDER BY importedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<SourceVideoEntity>>

    @Query("SELECT * FROM source_videos WHERE importId = :importId")
    suspend fun getById(importId: String): SourceVideoEntity?
}

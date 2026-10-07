package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vita.healthtracker.data.local.entity.CustomMood
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomMoodDao {
    /** 含已归档 (历史记录仍需解析名字/颜色/价)。 */
    @Query("SELECT * FROM custom_mood ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<CustomMood>>

    @Query("SELECT * FROM custom_mood")
    suspend fun all(): List<CustomMood>

    @Upsert
    suspend fun upsert(mood: CustomMood)

    @Upsert
    suspend fun upsertAll(moods: List<CustomMood>)

    @Query("UPDATE custom_mood SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)
}

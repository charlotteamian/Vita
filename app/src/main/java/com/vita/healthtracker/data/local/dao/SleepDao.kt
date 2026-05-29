package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vita.healthtracker.data.local.entity.SleepSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<SleepSession>)

    @Query("SELECT * FROM sleep_session WHERE startEpochMs BETWEEN :from AND :to ORDER BY startEpochMs")
    fun rangeFlow(from: Long, to: Long): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_session WHERE startEpochMs <= :to AND endEpochMs >= :from ORDER BY startEpochMs")
    suspend fun overlapping(from: Long, to: Long): List<SleepSession>

    @Query("DELETE FROM sleep_session WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM sleep_session ORDER BY startEpochMs DESC LIMIT 1")
    fun latestFlow(): Flow<SleepSession?>

    @Query("SELECT * FROM sleep_session ORDER BY startEpochMs")
    suspend fun all(): List<SleepSession>
}

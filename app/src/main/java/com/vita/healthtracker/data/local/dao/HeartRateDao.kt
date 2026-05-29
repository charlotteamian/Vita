package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vita.healthtracker.data.local.entity.HeartRateSample
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<HeartRateSample>)

    @Query("SELECT * FROM heart_rate_sample WHERE timeEpochMs BETWEEN :from AND :to ORDER BY timeEpochMs")
    fun rangeFlow(from: Long, to: Long): Flow<List<HeartRateSample>>

    @Query("SELECT AVG(bpm) FROM heart_rate_sample WHERE timeEpochMs BETWEEN :from AND :to")
    suspend fun averageBetween(from: Long, to: Long): Double?

    @Query("SELECT MIN(bpm) FROM heart_rate_sample WHERE timeEpochMs BETWEEN :from AND :to")
    suspend fun minBetween(from: Long, to: Long): Double?

    @Query("SELECT * FROM heart_rate_sample ORDER BY timeEpochMs")
    suspend fun all(): List<HeartRateSample>
}

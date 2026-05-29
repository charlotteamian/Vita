package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vita.healthtracker.data.local.entity.BodyBatterySample
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyBatteryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<BodyBatterySample>)

    @Query("SELECT * FROM body_battery_sample WHERE timeEpochMs BETWEEN :from AND :to ORDER BY timeEpochMs")
    fun rangeFlow(from: Long, to: Long): Flow<List<BodyBatterySample>>

    @Query("SELECT * FROM body_battery_sample ORDER BY timeEpochMs")
    suspend fun all(): List<BodyBatterySample>
}

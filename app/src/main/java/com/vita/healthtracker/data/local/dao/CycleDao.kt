package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vita.healthtracker.data.local.entity.CycleEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CycleEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<CycleEntry>)

    @Query("DELETE FROM cycle_entry WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Query("SELECT * FROM cycle_entry ORDER BY date DESC")
    fun allDescFlow(): Flow<List<CycleEntry>>

    @Query("SELECT * FROM cycle_entry WHERE isPeriodStart = 1 ORDER BY date DESC")
    suspend fun periodStarts(): List<CycleEntry>

    @Query("SELECT * FROM cycle_entry WHERE date BETWEEN :from AND :to ORDER BY date")
    fun rangeFlow(from: String, to: String): Flow<List<CycleEntry>>

    @Query("SELECT * FROM cycle_entry ORDER BY date")
    suspend fun all(): List<CycleEntry>
}

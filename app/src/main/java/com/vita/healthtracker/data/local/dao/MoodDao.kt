package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vita.healthtracker.data.local.entity.MoodEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_entry WHERE date BETWEEN :fromDate AND :toDate ORDER BY date ASC")
    fun observeRange(fromDate: String, toDate: String): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entry ORDER BY date DESC")
    fun observeAll(): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entry")
    suspend fun all(): List<MoodEntry>

    @Upsert
    suspend fun upsert(entry: MoodEntry)

    @Upsert
    suspend fun upsertAll(entries: List<MoodEntry>)

    @Query("DELETE FROM mood_entry WHERE date = :date")
    suspend fun delete(date: String)
}

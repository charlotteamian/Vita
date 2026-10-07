package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vita.healthtracker.data.local.entity.MoodEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_entry WHERE date BETWEEN :fromDate AND :toDate ORDER BY recordedAtEpochMs ASC")
    fun observeRange(fromDate: String, toDate: String): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entry ORDER BY recordedAtEpochMs DESC")
    fun observeAll(): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entry")
    suspend fun all(): List<MoodEntry>

    @Upsert
    suspend fun upsert(entry: MoodEntry)

    @Upsert
    suspend fun upsertAll(entries: List<MoodEntry>)

    @Query("UPDATE mood_entry SET moodId = :moodId, note = :note, weatherId = :weatherId, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, moodId: String, note: String, weatherId: String?, updatedAt: Long)

    @Query("DELETE FROM mood_entry WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM mood_entry WHERE date = :date")
    suspend fun deleteByDate(date: String)
}

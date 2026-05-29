package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vita.healthtracker.data.local.entity.ExerciseSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<ExerciseSession>)

    @Query("SELECT * FROM exercise_session WHERE isDeleted = 0 AND startEpochMs BETWEEN :from AND :to ORDER BY startEpochMs DESC")
    fun rangeFlow(from: Long, to: Long): Flow<List<ExerciseSession>>

    @Query("SELECT * FROM exercise_session WHERE startEpochMs <= :to AND endEpochMs >= :from ORDER BY startEpochMs DESC")
    suspend fun overlapping(from: Long, to: Long): List<ExerciseSession>

    @Query("DELETE FROM exercise_session WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM exercise_session WHERE isDeleted = 0 ORDER BY startEpochMs DESC LIMIT :limit")
    fun recentFlow(limit: Int): Flow<List<ExerciseSession>>

    @Query("SELECT * FROM exercise_session WHERE id = :id AND isDeleted = 0 LIMIT 1")
    fun getByIdFlow(id: String): Flow<ExerciseSession?>

    @Query("SELECT * FROM exercise_session WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<ExerciseSession>

    @Query("SELECT * FROM exercise_session ORDER BY startEpochMs")
    suspend fun all(): List<ExerciseSession>

    @Query("UPDATE exercise_session SET customTitle = :customTitle, customCategory = :customCategory, note = :note WHERE id = :id")
    suspend fun updateUserFields(id: String, customTitle: String?, customCategory: String?, note: String?)

    @Query("UPDATE exercise_session SET isDeleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: String)
}

package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit_definition WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAtEpochMs ASC")
    fun observeActiveHabits(): Flow<List<HabitDefinition>>

    @Query("SELECT * FROM habit_check_in WHERE date BETWEEN :fromDate AND :toDate")
    fun observeCheckIns(fromDate: String, toDate: String): Flow<List<HabitCheckIn>>

    @Query("SELECT COUNT(*) FROM habit_definition")
    suspend fun countHabits(): Int

    @Query("SELECT * FROM habit_definition")
    suspend fun allHabits(): List<HabitDefinition>

    @Query("SELECT * FROM habit_check_in")
    suspend fun allCheckIns(): List<HabitCheckIn>

    @Upsert
    suspend fun upsertHabit(habit: HabitDefinition)

    @Upsert
    suspend fun upsertHabits(habits: List<HabitDefinition>)

    @Upsert
    suspend fun upsertCheckIn(checkIn: HabitCheckIn)

    @Upsert
    suspend fun upsertCheckIns(checkIns: List<HabitCheckIn>)

    @Query("DELETE FROM habit_check_in WHERE habitId = :habitId AND date = :date")
    suspend fun clearCheckIn(habitId: String, date: String)

    @Query("UPDATE habit_definition SET isArchived = 1 WHERE id = :habitId")
    suspend fun archiveHabit(habitId: String)

    @Query("UPDATE habit_definition SET name = :name WHERE id = :habitId")
    suspend fun renameHabit(habitId: String, name: String)
}

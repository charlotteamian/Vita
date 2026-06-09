package com.vita.healthtracker.data.repository

import com.vita.healthtracker.data.local.dao.HabitDao
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class HabitRepository(
    private val habitDao: HabitDao,
) {
    fun observeActiveHabits(): Flow<List<HabitDefinition>> =
        habitDao.observeActiveHabits()

    fun observeCheckIns(fromDate: LocalDate, toDate: LocalDate): Flow<List<HabitCheckIn>> =
        habitDao.observeCheckIns(fromDate.toString(), toDate.toString())

    suspend fun createHabit(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val order = habitDao.countHabits()
        habitDao.upsertHabit(
            HabitDefinition(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                colorHex = DefaultHabitColors[order % DefaultHabitColors.size],
                sortOrder = order,
            ),
        )
    }

    suspend fun setStatus(habitId: String, date: LocalDate, status: Int) {
        habitDao.upsertCheckIn(
            HabitCheckIn(
                habitId = habitId,
                date = date.toString(),
                status = status,
            ),
        )
    }

    suspend fun clearStatus(habitId: String, date: LocalDate) {
        habitDao.clearCheckIn(habitId, date.toString())
    }

    suspend fun archiveHabit(habitId: String) {
        habitDao.archiveHabit(habitId)
    }

    suspend fun renameHabit(habitId: String, name: String) {
        val cleanName = name.trim()
        if (cleanName.isNotBlank()) habitDao.renameHabit(habitId, cleanName)
    }

    private companion object {
        val DefaultHabitColors = listOf(
            0xFF74C0FC,
            0xFF8CE99A,
            0xFFFFD166,
            0xFFFF6B9A,
            0xFFB197FC,
            0xFF63E6BE,
        )
    }
}

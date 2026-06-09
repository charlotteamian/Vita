package com.vita.healthtracker.data.repository

import com.vita.healthtracker.data.local.dao.MoodDao
import com.vita.healthtracker.data.local.entity.MoodEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class MoodRepository(
    private val moodDao: MoodDao,
) {
    fun observeRange(fromDate: LocalDate, toDate: LocalDate): Flow<List<MoodEntry>> =
        moodDao.observeRange(fromDate.toString(), toDate.toString())

    fun observeAll(): Flow<List<MoodEntry>> = moodDao.observeAll()

    suspend fun setMood(date: LocalDate, moodId: String, note: String = "") {
        moodDao.upsert(
            MoodEntry(
                date = date.toString(),
                moodId = moodId,
                note = note.trim(),
            ),
        )
    }

    suspend fun clearMood(date: LocalDate) {
        moodDao.delete(date.toString())
    }
}

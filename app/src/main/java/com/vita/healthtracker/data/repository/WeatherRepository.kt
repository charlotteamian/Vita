package com.vita.healthtracker.data.repository

import com.vita.healthtracker.data.local.dao.WeatherDao
import com.vita.healthtracker.data.local.entity.WeatherEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class WeatherRepository(
    private val dao: WeatherDao,
) {
    fun observeRange(fromDate: LocalDate, toDate: LocalDate): Flow<List<WeatherEntry>> =
        dao.observeRange(fromDate.toString(), toDate.toString())

    /** 自动获取写入: 跳过用户手动记录过的日期, 不覆盖手动数据。 */
    suspend fun upsertAuto(entries: List<WeatherEntry>) {
        if (entries.isEmpty()) return
        val manual = dao.manualDates().toSet()
        val toWrite = entries.filter { it.date !in manual }
        if (toWrite.isNotEmpty()) dao.upsertAll(toWrite)
    }

    suspend fun setWeather(date: LocalDate, weatherId: String) {
        dao.upsert(WeatherEntry(date = date.toString(), weatherId = weatherId, source = "manual"))
    }

    suspend fun clearWeather(date: LocalDate) {
        dao.delete(date.toString())
    }
}

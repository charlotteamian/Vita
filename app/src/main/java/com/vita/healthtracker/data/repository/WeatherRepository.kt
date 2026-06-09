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

    suspend fun setWeather(date: LocalDate, weatherId: String) {
        dao.upsert(WeatherEntry(date = date.toString(), weatherId = weatherId))
    }

    suspend fun clearWeather(date: LocalDate) {
        dao.delete(date.toString())
    }
}

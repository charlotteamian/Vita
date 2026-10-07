package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vita.healthtracker.data.local.entity.WeatherEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_entry WHERE date BETWEEN :fromDate AND :toDate ORDER BY date ASC")
    fun observeRange(fromDate: String, toDate: String): Flow<List<WeatherEntry>>

    @Query("SELECT * FROM weather_entry")
    suspend fun all(): List<WeatherEntry>

    /** 用户手动记录的天气日期; 自动获取时跳过这些天, 不覆盖手动记录。 */
    @Query("SELECT date FROM weather_entry WHERE source = 'manual'")
    suspend fun manualDates(): List<String>

    @Upsert
    suspend fun upsert(entry: WeatherEntry)

    @Upsert
    suspend fun upsertAll(entries: List<WeatherEntry>)

    @Query("DELETE FROM weather_entry WHERE date = :date")
    suspend fun delete(date: String)
}

package com.vita.healthtracker.data.weather

import com.vita.healthtracker.data.local.entity.WeatherEntry
import com.vita.healthtracker.data.location.LocationProvider
import com.vita.healthtracker.data.prefs.SettingsPreferences
import com.vita.healthtracker.data.repository.WeatherRepository
import com.vita.healthtracker.domain.WeatherCodeMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 天气自动获取的协调器。跑在 app scope (离开页面不中断)。
 * 仅当用户在设置里开启「自动获取天气」且授予了大致位置权限时才工作; 用最后已知位置拉
 * 最近 7 天天气, 写库时跳过用户手动记录过的日期。
 */
class WeatherSyncManager(
    private val scope: CoroutineScope,
    private val locationProvider: LocationProvider,
    private val client: OpenMeteoClient,
    private val weatherRepo: WeatherRepository,
    private val prefs: SettingsPreferences,
) {
    fun refreshIfEnabled() {
        scope.launch {
            if (!prefs.weatherAutoEnabled.first()) return@launch
            val location = locationProvider.lastKnown() ?: return@launch
            val daily = client.recentDaily(location.first, location.second, pastDays = 7)
            if (daily.isEmpty()) return@launch
            val now = System.currentTimeMillis()
            val entries = daily.mapNotNull { day ->
                val weatherId = WeatherCodeMapper.toWeatherId(day.code) ?: return@mapNotNull null
                WeatherEntry(
                    date = day.date,
                    weatherId = weatherId,
                    source = "auto",
                    tempMaxC = day.tMax,
                    tempMinC = day.tMin,
                    weatherCode = day.code,
                    updatedAtEpochMs = now,
                )
            }
            weatherRepo.upsertAuto(entries)
        }
    }
}

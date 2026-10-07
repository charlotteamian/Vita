package com.vita.healthtracker.data.weather

import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Open-Meteo 天气客户端。免费、无需 API key、无需注册。
 * 只取按天的天气代码 + 当日最高/最低气温, 含最近若干天 (past_days), 用于补齐近几天。
 */
class OpenMeteoClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    data class DailyWeather(
        val date: String,
        val code: Int,
        val tMax: Double?,
        val tMin: Double?,
    )

    suspend fun recentDaily(lat: Double, lon: Double, pastDays: Int): List<DailyWeather> =
        withContext(Dispatchers.IO) {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&past_days=%d&forecast_days=1&timezone=auto",
                lat, lon, pastDays.coerceIn(0, 92),
            )
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<DailyWeather>()
                    parse(resp.body?.string().orEmpty())
                }
            }.getOrDefault(emptyList())
        }

    private fun parse(json: String): List<DailyWeather> {
        if (json.isBlank()) return emptyList()
        val daily = JSONObject(json).optJSONObject("daily") ?: return emptyList()
        val times = daily.optJSONArray("time") ?: return emptyList()
        val codes = daily.optJSONArray("weather_code")
        val tMax = daily.optJSONArray("temperature_2m_max")
        val tMin = daily.optJSONArray("temperature_2m_min")
        return (0 until times.length()).mapNotNull { i ->
            val date = times.optString(i).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val code = codes?.optInt(i, -1) ?: -1
            if (code < 0) return@mapNotNull null
            DailyWeather(
                date = date,
                code = code,
                tMax = tMax?.optDouble(i)?.takeIf { !it.isNaN() },
                tMin = tMin?.optDouble(i)?.takeIf { !it.isNaN() },
            )
        }
    }
}

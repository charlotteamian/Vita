package com.vita.healthtracker.domain

/**
 * WMO weather interpretation code (Open-Meteo 返回) → [WeatherCatalog] 的 weatherId。
 * 参考 https://open-meteo.com/en/docs (WMO Weather interpretation codes)。
 * 不认识的 code 返回 null, 这天就不写天气 (宁可缺, 不乱填)。
 */
object WeatherCodeMapper {
    fun toWeatherId(code: Int): String? = when (code) {
        0 -> "clear"
        1, 2 -> "partly_cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55, 56, 57 -> "light_rain"   // 毛毛雨 / 冻毛毛雨
        61, 66 -> "light_rain"                // 小雨 / 冻小雨
        63, 67 -> "rain"                      // 中雨 / 冻雨
        65 -> "heavy_rain"                    // 大雨
        71, 77 -> "light_snow"                // 小雪 / 雪粒
        73 -> "snow"                          // 中雪
        75 -> "heavy_snow"                    // 大雪
        80 -> "light_rain"                    // 小阵雨
        81 -> "rain"                          // 中阵雨
        82 -> "heavy_rain"                    // 强阵雨
        85 -> "light_snow"                    // 小阵雪
        86 -> "heavy_snow"                    // 大阵雪
        95 -> "thunderstorm"                  // 雷暴
        96, 99 -> "hail"                      // 雷暴伴冰雹
        else -> null
    }
}

package com.vita.healthtracker.domain

/** 天气日记分类。severity 仅用于天气与情绪分组，不代表灾害预警等级。 */
data class WeatherKind(
    val id: String,
    val label: String,
    val shortLabel: String,
    val severity: Int,
)

object WeatherCatalog {
    val kinds = listOf(
        WeatherKind("clear", "晴", "晴", 0),
        WeatherKind("partly_cloudy", "多云", "多云", 0),
        WeatherKind("overcast", "阴", "阴", 0),
        WeatherKind("light_rain", "小雨", "小雨", 1),
        WeatherKind("rain", "雨", "雨", 1),
        WeatherKind("heavy_rain", "暴雨", "暴雨", 2),
        WeatherKind("thunderstorm", "雷暴", "雷暴", 2),
        WeatherKind("hail", "冰雹", "冰雹", 2),
        WeatherKind("light_snow", "小雪", "小雪", 1),
        WeatherKind("snow", "雪", "雪", 1),
        WeatherKind("heavy_snow", "暴雪", "暴雪", 2),
        WeatherKind("fog", "雾", "雾", 1),
        WeatherKind("haze", "霾", "霾", 1),
        WeatherKind("sandstorm", "沙尘暴", "沙尘", 2),
        WeatherKind("gale", "大风", "大风", 2),
        WeatherKind("typhoon", "台风", "台风", 2),
        WeatherKind("heatwave", "高温热浪", "热浪", 2),
        WeatherKind("cold_wave", "寒潮", "寒潮", 2),
    )

    private val byId = kinds.associateBy { it.id }

    fun byId(id: String?): WeatherKind? = id?.let(byId::get)
}

package com.vita.healthtracker.domain

/**
 * 把数据来源标识 (DailyHealthSnapshot.source / SleepSession.source) 映射成中文标签。
 *
 * source 可能是:
 *  - 内部约定值: "health_connect" / "garmin_api" / "phone" / "apple_health" / "manual" / "sensor"(旧)
 *  - Health Connect 透传的原始 app 包名 (如 com.garmin.android.apps.connectmobile)
 *  - 合并来源: 多个来源用 "+" 连接 (见 HealthRepository.mergeSource)
 */
fun healthSourceLabel(source: String?): String {
    if (source.isNullOrBlank()) return "未知来源"
    return source.split("+")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { token ->
            val t = token.lowercase()
            when {
                t == "phone" || t == "sensor" -> "手机"
                t.contains("apple") -> "Apple 健康"
                t.contains("garmin") -> "佳明"
                t.contains("healthdata") || t.contains("health_connect") || t.contains("healthconnect") -> "Health Connect"
                t == "manual" -> "手动记录"
                t.contains("samsung") || t.contains("shealth") -> "三星"
                t.contains("xiaomi") || t.contains("mi.health") || t.contains("hm.health") || t.contains("huami") -> "小米"
                t.contains("fitbit") -> "Fitbit"
                t.contains("mock") -> "示例数据"
                else -> token
            }
        }
        .distinct()
        .joinToString(" · ")
}

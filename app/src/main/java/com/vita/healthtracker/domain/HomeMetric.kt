package com.vita.healthtracker.domain

/**
 * 首页(今日)可展示的数据项。key 用于持久化(DataStore), label 用于设置页里的开关文案。
 * 想新增首页指标时, 在这里加一项, 然后在 TodayScreen 的 metric 映射里补上图标/取值即可。
 */
enum class HomeMetric(val key: String, val label: String) {
    STEPS("steps", "步数"),
    DISTANCE("distance", "距离"),
    ACTIVE_CALORIES("active_cal", "活跃卡路里"),
    TOTAL_CALORIES("total_cal", "总消耗"),
    ACTIVE_MINUTES("active_min", "活跃分钟"),
    FLOORS("floors", "爬楼"),
    AVG_HEART_RATE("avg_hr", "平均心率"),
    RESTING_HEART_RATE("resting_hr", "静息心率"),
    HRV("hrv", "心率变异性"),
    STRESS("stress", "压力"),
    BODY_BATTERY("body_battery", "身体电量"),
    SPO2("spo2", "血氧"),
    RESPIRATION("respiration", "呼吸率"),
    WEIGHT("weight", "体重"),
    SLEEP("sleep", "睡眠"),
    SLEEP_SCORE("sleep_score", "睡眠评分"),
    EXERCISE("exercise", "运动记录");

    companion object {
        /**
         * 默认开启的首页卡片 (基础项)。
         * 进阶维度(压力/电量/血氧/呼吸/HRV/体重/睡眠评分)默认不显示, 由用户在设置里按需开启,
         * 避免首页卡片过多。
         */
        val DEFAULT_KEYS: Set<String> = setOf(
            STEPS.key, DISTANCE.key, ACTIVE_CALORIES.key, TOTAL_CALORIES.key,
            ACTIVE_MINUTES.key, FLOORS.key, AVG_HEART_RATE.key, RESTING_HEART_RATE.key,
            SLEEP.key, EXERCISE.key,
        )

        /** 设置页按枚举声明顺序展示 (含全部可选维度)。 */
        val ORDERED: List<HomeMetric> = entries.toList()
    }
}

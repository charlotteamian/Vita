package com.vita.healthtracker.domain

/**
 * ExerciseSessionRecord.EXERCISE_TYPE_* 到中文名的映射。
 * 值来自 androidx.health.connect.client.records.ExerciseSessionRecord。
 */
object ExerciseTypeNames {

    private val names = mapOf(
        0 to "其他运动",
        2 to "羽毛球",
        4 to "棒球",
        5 to "篮球",
        8 to "骑行",
        10 to "板球",
        11 to "跳舞",
        13 to "椭圆机",
        14 to "击剑",
        15 to "美式足球",
        16 to "高尔夫",
        17 to "引导呼吸",
        18 to "体操",
        19 to "手球",
        20 to "徒步",
        21 to "冰球",
        22 to "溜冰",
        25 to "武术",
        27 to "冥想",
        28 to "综合格斗",
        29 to "帆板",
        31 to "普拉提",
        32 to "球拍运动",
        33 to "攀岩",
        34 to "划船",
        35 to "划船机",
        36 to "橄榄球",
        37 to "跑步",
        38 to "跑步 (跑步机)",
        39 to "帆船",
        40 to "滑板",
        41 to "滑雪",
        42 to "雪板",
        43 to "壁球",
        44 to "楼梯机",
        45 to "冲浪",
        46 to "游泳 (开放水域)",
        47 to "游泳 (泳池)",
        48 to "乒乓球",
        49 to "网球",
        51 to "排球",
        52 to "步行",
        53 to "水球",
        54 to "举重",
        55 to "轮椅",
        56 to "瑜伽",
        57 to "高强度间歇",
        58 to "登山",       // EXERCISE_TYPE_HIKING 和 EXERCISE_TYPE_MOUNTAINEERING 可能重叠
        74 to "跳绳",
        75 to "力量训练",
        76 to "拉伸",
    )

    private val garminNames = mapOf(
        "running" to "跑步",
        "street_running" to "跑步",
        "track_running" to "跑步",
        "trail_running" to "越野跑",
        "treadmill_running" to "跑步机",
        "walking" to "步行",
        "casual_walking" to "步行",
        "speed_walking" to "健走",
        "hiking" to "徒步",
        "cycling" to "骑行",
        "road_biking" to "公路骑行",
        "mountain_biking" to "山地骑行",
        "indoor_cycling" to "室内骑行",
        "strength_training" to "力量训练",
        "cardio_training" to "有氧训练",
        "hiit" to "高强度间歇",
        "fitness_equipment" to "器械训练",
        "elliptical" to "椭圆机",
        "indoor_rowing" to "划船机",
        "rowing" to "划船",
        "pool_swimming" to "泳池游泳",
        "open_water_swimming" to "开放水域游泳",
        "yoga" to "瑜伽",
        "pilates" to "普拉提",
        "breathwork" to "呼吸训练",
        "floor_climbing" to "楼梯机",
        "mountaineering" to "登山",
        "skiing" to "滑雪",
        "snowboarding" to "单板滑雪",
        "golf" to "高尔夫",
    )

    fun nameOf(type: Int): String = names[type] ?: "运动"

    fun nameOf(typeString: String): String {
        runCatching { typeString.toInt() }.getOrNull()?.let { return nameOf(it) }
        val key = typeString.trim().lowercase()
        garminNames[key]?.let { return it }
        return key
            .split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
            .ifBlank { "运动" }
    }
}

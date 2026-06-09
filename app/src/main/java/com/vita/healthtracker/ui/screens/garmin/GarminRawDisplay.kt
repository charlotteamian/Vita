package com.vita.healthtracker.ui.screens.garmin

import com.vita.healthtracker.data.local.entity.GarminRawRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

data class GarminDisplayBlock(
    val headline: String,
    val meaning: String,
    val rows: List<GarminDisplayRow>,
    val hiddenCount: Int,
    val chart: GarminDisplayChart? = null,
)

data class GarminDisplayRow(
    val label: String,
    val value: String,
)

data class GarminDisplayChart(
    val title: String,
    val points: List<GarminDisplayPoint>,
    val unit: String? = null,
) {
    val values: List<Double> get() = points.map { it.value }
}

data class GarminDisplayPoint(
    val hour: Double,
    val value: Double,
)

object GarminRawDisplay {
    private const val MaxRows = 56
    private const val MinChartPoints = 6
    private const val MinChartSpanHours = 0.35

    fun describe(record: GarminRawRecord): GarminDisplayBlock {
        if (shouldHideCategory(record.categoryKey)) {
            return GarminDisplayBlock(
                headline = "",
                meaning = meaningFor(record.categoryKey),
                rows = emptyList(),
                hiddenCount = 0,
            )
        }
        val root = parse(record.payloadJson) ?: return GarminDisplayBlock(
            headline = "这类数据暂时看不懂",
            meaning = "记录已经保存在设备上，只是目前还不能整理成清晰的指标。",
            rows = emptyList(),
            hiddenCount = 0,
        )
        val chart = chartFor(record, root)
        val allRows = if (chart != null) {
            chartSummaryRows(chart)
        } else {
            buildList {
                addAll(arrayRows(root))
                addAll(scalarRows(root))
            }.distinctBy { it.label to it.value }
        }

        val visible = allRows.take(MaxRows)
        val headline = if (chart != null) {
            chartHeadline(chart)
        } else {
            visible
                .take(3)
                .joinToString(" · ") { "${it.label} ${it.value}" }
                .ifBlank { "" }
        }

        return GarminDisplayBlock(
            headline = headline,
            meaning = meaningFor(record.categoryKey),
            rows = visible,
            hiddenCount = (allRows.size - visible.size).coerceAtLeast(0),
            chart = chart,
        )
    }

    private fun shouldHideCategory(categoryKey: String): Boolean {
        val key = categoryKey.lowercase()
        return key == "fitness_age" ||
            key.contains("fitness_age") ||
            key == "daily_events" ||
            key == "body_battery_events" ||
            (key.startsWith("activity_") && !key.startsWith("activity_summary_"))
    }

    private fun parse(text: String): Any? = runCatching {
        val clean = text.trim()
        when {
            clean.startsWith("{") -> JSONObject(clean)
            clean.startsWith("[") -> JSONArray(clean)
            else -> null
        }
    }.getOrNull()

    private fun scalarRows(root: Any): List<GarminDisplayRow> {
        val scalars = mutableListOf<ScalarField>()
        collectScalars(root, emptyList(), scalars)
        return scalars
            .asSequence()
            .filterNot { shouldSkipScalar(it) }
            .mapNotNull { field ->
                val label = labelFor(field.key, field.path) ?: return@mapNotNull null
                GarminDisplayRow(label, formatValue(field.key, field.value))
            }
            .filter { it.value.isNotBlank() }
            .distinctBy { it.label }
            .toList()
    }

    private fun arrayRows(root: Any): List<GarminDisplayRow> {
        val arrays = mutableListOf<ArrayField>()
        collectArrays(root, emptyList(), arrays)
        return arrays
            .asSequence()
            .filter { it.value.length() > 0 }
            .mapNotNull { array ->
                val label = arrayLabel(array.key, array.path) ?: return@mapNotNull null
                if (label.contains("采样") || label.contains("曲线")) return@mapNotNull null
                val stats = numericSeries(array.value)
                val value = if (stats.isNotEmpty()) {
                    val min = stats.minOrNull() ?: 0.0
                    val max = stats.maxOrNull() ?: 0.0
                    val avg = stats.average()
                    "${array.value.length()} 个采样点 · 平均 ${formatCompact(avg)} · 范围 ${formatCompact(min)}-${formatCompact(max)}"
                } else {
                    "${array.value.length()} 项"
                }
                GarminDisplayRow(label, value)
            }
            .distinctBy { it.label }
            .toList()
    }

    private fun chartFor(record: GarminRawRecord, root: Any): GarminDisplayChart? {
        val key = record.categoryKey.lowercase()
        val date = runCatching { LocalDate.parse(record.date) }.getOrDefault(LocalDate.now())
        val arrays = mutableListOf<ArrayField>()
        collectArrays(root, emptyList(), arrays)

        fun pick(vararg needles: String, range: ClosedFloatingPointRange<Double>? = null): List<GarminDisplayPoint>? =
            arrays.firstNotNullOfOrNull { array ->
                val normalized = (array.path.joinToString(".") + "." + array.key)
                    .lowercase()
                    .replace("_", "")
                    .replace("-", "")
                if (needles.none { normalized.contains(it.lowercase().replace("_", "")) }) return@firstNotNullOfOrNull null
                chartPoints(array.value, date)
                    .filter { range == null || it.value in range }
                    .takeIf { points ->
                        points.size >= MinChartPoints &&
                            ((points.lastOrNull()?.hour ?: 0.0) - (points.firstOrNull()?.hour ?: 0.0)) >= MinChartSpanHours
                    }
            }

        return when {
            "stress" in key -> pick("stress", range = 0.0..100.0)
                ?.let { GarminDisplayChart("全天压力", it) }
            "body_battery" in key -> pick("bodybattery", "body_battery", range = 0.0..100.0)
                ?.let { GarminDisplayChart("身体电量", it) }
            "heart" in key -> pick("heartrate", "heart_rate", "hr", range = 20.0..240.0)
                ?.let { GarminDisplayChart("全天心率", it, "bpm") }
            "spo2" in key -> pick("spo2", "oxygen", range = 50.0..100.0)
                ?.let { GarminDisplayChart("血氧", it, "%") }
            "respiration" in key -> pick("respiration", "breath", range = 1.0..80.0)
                ?.let { GarminDisplayChart("呼吸率", it, "次/分") }
            else -> null
        }
    }

    private fun chartSummaryRows(chart: GarminDisplayChart): List<GarminDisplayRow> {
        val values = chart.values.filter { it.isFinite() }
        if (values.isEmpty()) return emptyList()
        val unit = chart.unit.orEmpty()
        return listOf(
            GarminDisplayRow("平均", "${formatCompact(values.average())}$unit"),
            GarminDisplayRow("范围", "${formatCompact(values.minOrNull() ?: 0.0)}-${formatCompact(values.maxOrNull() ?: 0.0)}$unit"),
        )
    }

    private fun chartHeadline(chart: GarminDisplayChart): String {
        val values = chart.values.filter { it.isFinite() }
        if (values.isEmpty()) return chart.title
        val unit = chart.unit.orEmpty()
        return "${chart.title} ${formatCompact(values.average())}$unit"
    }

    private fun chartPoints(array: JSONArray, date: LocalDate): List<GarminDisplayPoint> {
        val out = mutableListOf<GarminDisplayPoint>()
        for (index in 0 until array.length()) {
            val item = array.opt(index)
            val value = numericValue(item) ?: continue
            val hour = timeHour(item, date) ?: continue
            if (hour in 0.0..24.0 && value.isFinite()) {
                out.add(GarminDisplayPoint(hour, value))
            }
        }
        return out.distinctBy { "%.3f".format(it.hour) }.sortedBy { it.hour }
    }

    private fun collectScalars(node: Any?, path: List<String>, out: MutableList<ScalarField>, depth: Int = 0) {
        if (node == null || depth > 5) return
        when (node) {
            is JSONObject -> node.keys().forEach { key ->
                val value = node.opt(key)
                collectScalars(value, path + key, out, depth + 1)
            }
            is JSONArray -> {
                // 大数组通常是采样点, 在 arrayRows 中做摘要; 小数组展开前 3 项用于展示分段/评分等结构。
                if (node.length() <= 3) {
                    for (index in 0 until node.length()) {
                        collectScalars(node.opt(index), path + (index + 1).toString(), out, depth + 1)
                    }
                }
            }
            is Number, is String, is Boolean -> {
                val key = path.lastOrNull().orEmpty()
                out.add(ScalarField(path, key, node))
            }
        }
    }

    private fun collectArrays(node: Any?, path: List<String>, out: MutableList<ArrayField>, depth: Int = 0) {
        if (node == null || depth > 5) return
        when (node) {
            is JSONObject -> node.keys().forEach { key ->
                collectArrays(node.opt(key), path + key, out, depth + 1)
            }
            is JSONArray -> {
                val key = path.lastOrNull().orEmpty()
                out.add(ArrayField(path, key, node))
                if (node.length() <= 8) {
                    for (index in 0 until node.length()) {
                        collectArrays(node.opt(index), path + (index + 1).toString(), out, depth + 1)
                    }
                }
            }
        }
    }

    private fun numericSeries(array: JSONArray): List<Double> {
        val out = mutableListOf<Double>()
        for (index in 0 until array.length()) {
            val value = numericValue(array.opt(index))
            if (value != null && value.isFinite()) out.add(value)
        }
        return out
    }

    private fun numericValue(item: Any?): Double? = when (item) {
        is Number -> item.toDouble()
        is JSONArray -> {
            // Garmin 多数曲线是 [timestamp, value] 或 [timestamp, value, ...]。
            (1 until item.length()).firstNotNullOfOrNull { valueIndex ->
                (item.opt(valueIndex) as? Number)?.toDouble()?.takeIf { it.isFinite() }
            }
        }
        is JSONObject -> firstNumber(
            item,
            "value",
            "level",
            "heartRate",
            "bpm",
            "stressLevel",
            "bodyBattery",
            "bodyBatteryLevel",
            "spo2",
            "respirationValue",
            "steps",
            "calories",
            "watts",
            "speed",
        )
        else -> null
    }

    private fun timeHour(item: Any?, date: LocalDate): Double? = when (item) {
        is JSONArray -> item.opt(0).toHourOfDay(date)
        is JSONObject -> {
            val candidates = listOf(
                "timestamp",
                "startTimestampGMT",
                "startTimeGMT",
                "startTimeLocal",
                "startGMT",
                "startTime",
                "time",
                "localTimestamp",
            )
            candidates.firstNotNullOfOrNull { key -> item.opt(key).toHourOfDay(date) }
        }
        else -> null
    }

    private fun Any?.toHourOfDay(date: LocalDate): Double? = when (this) {
        is Number -> {
            val value = toDouble()
            when {
                value > 1_000_000_000_000.0 -> Instant.ofEpochMilli(value.toLong()).toLocalHour()
                value > 1_000_000_000.0 -> Instant.ofEpochMilli((value * 1000.0).toLong()).toLocalHour()
                value in 60_000.0..86_400_000.0 -> value / 3_600_000.0
                else -> null
            }
        }
        is String -> parseHourOfDay(trim(), date)
        else -> null
    }?.coerceIn(0.0, 24.0)

    private fun parseHourOfDay(text: String, date: LocalDate): Double? {
        if (text.isBlank()) return null
        runCatching { Instant.parse(text).toLocalHour() }.getOrNull()?.let { return it }
        runCatching {
            LocalDateTime.parse(text.substringBefore(".").replace("Z", ""))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toLocalHour()
        }.getOrNull()?.let { return it }
        runCatching {
            LocalDateTime.parse(text.substringBefore("."), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toLocalHour()
        }.getOrNull()?.let { return it }
        runCatching { LocalTime.parse(text.substringBefore(".")) }.getOrNull()?.let {
            return it.hour + it.minute / 60.0 + it.second / 3600.0
        }
        return runCatching {
            LocalDateTime.of(date, LocalTime.parse(text.takeLast(8))).atZone(ZoneId.systemDefault()).toInstant().toLocalHour()
        }.getOrNull()
    }

    private fun Instant.toLocalHour(): Double {
        val time = atZone(ZoneId.systemDefault()).toLocalTime()
        return time.hour + time.minute / 60.0 + time.second / 3600.0
    }

    private fun firstNumber(json: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            val value = json.opt(key)
            if (value is Number) return value.toDouble()
        }
        return null
    }

    private fun shouldSkipScalar(field: ScalarField): Boolean {
        val value = field.value
        val key = field.key.lowercase()
        val joinedPath = field.path.joinToString(".").lowercase()
        if (value is String && value.isBlank()) return true
        if (key == "value") return true
        if (key == "privacyprotected") return true
        if (key == "userprofileid" || key == "userprofilename") return true
        if (
            key.contains("time") ||
            key.contains("date") ||
            key.contains("timestamp") ||
            key.contains("gmt") ||
            key.contains("local") ||
            joinedPath.contains("start") ||
            joinedPath.contains("end")
        ) return true
        return false
    }

    private fun labelFor(key: String, path: List<String>): String? {
        val exact = FieldLabels[key]
        if (exact != null) return exact
        val lower = key.lowercase()
        return when {
            lower.contains("average") && lower.contains("heartrate") -> "平均心率"
            lower.contains("max") && lower.contains("heartrate") -> "最大心率"
            lower.contains("resting") && lower.contains("heartrate") -> "静息心率"
            lower.contains("calorie") || lower.contains("kilocalorie") -> prettifyKey(key).replace("Kilocalories", "卡路里")
            lower.contains("distance") -> prettifyKey(key).replace("Meters", "距离")
            lower.contains("duration") -> prettifyKey(key).replace("Duration", "时长")
            else -> null
        }
    }

    private fun arrayLabel(key: String, path: List<String>): String? {
        ArrayLabels[key]?.let { return it }
        val lower = key.lowercase()
        return when {
            lower.contains("heartrate") || lower.contains("hr") -> "心率采样"
            lower.contains("stress") -> "压力采样"
            lower.contains("bodybattery") -> "身体电量采样"
            lower.contains("respiration") -> "呼吸采样"
            lower.contains("spo2") -> "血氧采样"
            lower.contains("sleep") -> "睡眠分期/事件"
            lower.contains("split") -> "运动分段"
            lower.contains("lap") -> "运动计圈"
            lower.contains("metric") -> "指标明细"
            lower.contains("polyline") || lower.contains("route") -> null
            else -> null
        }
    }

    private fun formatValue(key: String, value: Any): String {
        val lower = key.lowercase()
        return when (value) {
            is Boolean -> if (value) "是" else "否"
            is Number -> formatNumber(lower, value.toDouble())
            is String -> formatString(lower, value)
            else -> value.toString()
        }
    }

    private fun formatNumber(key: String, value: Double): String {
        if (!value.isFinite()) return ""
        return when {
            key.contains("epochms") || (key.contains("timestamp") && value > 1_000_000_000_000.0) ->
                formatEpoch(value.toLong())
            key.contains("timestamp") && value > 1_000_000_000.0 ->
                formatEpoch((value * 1000.0).toLong())
            key.contains("seconds") || key.endsWith("duration") || key.contains("durationinseconds") ->
                formatDuration(value.toLong())
            key.contains("minutes") -> "${formatCompact(value)} 分钟"
            key.contains("distance") -> if (value >= 1000.0) "%.2f km".format(value / 1000.0) else "${formatCompact(value)} m"
            key.contains("calorie") || key.contains("kilocalorie") || key.endsWith("kcal") -> "${formatCompact(value)} kcal"
            key.contains("heartrate") || key == "averagehr" || key == "maxhr" || key == "minhr" -> "${formatCompact(value)} bpm"
            key.contains("hrv") -> "${formatCompact(value)} ms"
            key.contains("spo2") || key.contains("percentage") || key.contains("percent") -> "${formatCompact(value)}%"
            key.contains("respiration") -> "${formatCompact(value)} 次/分"
            key.contains("speed") || key.contains("pace") -> "%.2f km/h".format(value * 3.6)
            key.contains("cadence") -> "${formatCompact(value)} 步/分"
            key.contains("power") || key.contains("watts") -> "${formatCompact(value)} W"
            key.contains("elevation") || key.contains("altitude") || key.contains("ascent") || key.contains("descent") -> "${formatCompact(value)} m"
            key.contains("weightingrams") -> "${formatCompact(value / 1000.0)} kg"
            key.contains("weight") -> "${formatCompact(value)} kg"
            key.contains("hydration") || key.contains("sweat") || key.contains("water") -> "${formatCompact(value)} ml"
            key.contains("steps") -> "${formatCompact(value)} 步"
            key.contains("floors") -> "${formatCompact(value)} 层"
            else -> formatCompact(value)
        }
    }

    private fun formatString(key: String, value: String): String = when {
        key.contains("time") || key.contains("date") -> value.replace("T", " ").removeSuffix("Z")
        else -> value
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0L) return "0 分钟"
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        return when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "$minutes 分钟"
            else -> "$seconds 秒"
        }
    }

    private fun formatEpoch(epochMs: Long): String = runCatching {
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrDefault(epochMs.toString())

    private fun formatCompact(value: Double): String =
        if (value % 1.0 == 0.0) "%,.0f".format(value) else "%,.1f".format(value)

    private fun prettifyPath(path: List<String>): String =
        path.takeLast(2).joinToString(" / ") { prettifyKey(it) }

    private fun prettifyKey(key: String): String =
        key.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace("_", " ")
            .replaceFirstChar { it.uppercase() }

    private fun nodeSize(node: Any?): Int = when (node) {
        is JSONObject -> node.keys().asSequence().sumOf { 1 + nodeSize(node.opt(it)) }
        is JSONArray -> node.length()
        else -> 1
    }

    private data class ScalarField(val path: List<String>, val key: String, val value: Any)
    private data class ArrayField(val path: List<String>, val key: String, val value: JSONArray)

    private fun meaningFor(categoryKey: String): String {
        val key = categoryKey.lowercase()
        return when {
            key.contains("heart") -> "这里整理当天心率和全天变化，可辅助回看运动强度和恢复情况。"
            key.contains("hrv") -> "心率变异性适合和自己的长期变化一起看，用来辅助观察恢复和压力。"
            key.contains("stress") -> "压力记录可帮助回看一天里紧张和放松的变化。"
            key.contains("sleep") -> "这里整理睡眠总时长、分期和评分等信息，方便回看睡眠质量。"
            key.contains("body_battery") -> "身体电量反映一天里的精力恢复和消耗，可作为日常参考。"
            key.contains("spo2") -> "血氧用于观察血氧饱和度水平；低值请先结合佩戴状态和身体感受复核。"
            key.contains("respiration") -> "呼吸率是每分钟呼吸次数，可辅助观察睡眠和恢复状态。"
            key.contains("intensity") -> "这里整理中高强度活动时间，方便回看当天活动量。"
            key.contains("activity") -> "运动记录包含时长、距离、消耗和心率等明细。"
            key.contains("training") -> "训练相关数据可辅助判断近期训练负荷和恢复节奏。"
            key.contains("body_composition") -> "身体成分包含体重、BMI 等体测数据。"
            key.contains("hydration") || key.contains("nutrition") -> "饮水和营养记录用于观察摄入目标和实际记录。"
            key.contains("menstrual") -> "经期详情用于展示周期相关记录。"
            else -> "这里只展示目前能整理清楚的内容；其他记录仍保存在设备上，后续可以继续适配。"
        }
    }

    private val FieldLabels = mapOf(
        "activityName" to "运动名称",
        "totalSteps" to "步数",
        "steps" to "步数",
        "totalDistanceMeters" to "距离",
        "distance" to "距离",
        "activeKilocalories" to "活跃卡路里",
        "totalKilocalories" to "总消耗",
        "bmrKilocalories" to "静息消耗",
        "calories" to "活动消耗",
        "restingHeartRate" to "静息心率",
        "averageHeartRate" to "平均心率",
        "averageHR" to "平均心率",
        "maxHeartRate" to "最大心率",
        "maxHR" to "最大心率",
        "minHeartRate" to "最低心率",
        "averageStressLevel" to "平均压力",
        "maxStressLevel" to "最高压力",
        "bodyBatteryHighestValue" to "身体电量最高",
        "bodyBatteryLowestValue" to "身体电量最低",
        "bodyBatteryMostRecentValue" to "身体电量当前",
        "averageSpo2" to "平均血氧",
        "averageSpo2Value" to "平均血氧",
        "avgWakingRespirationValue" to "平均呼吸率",
        "averageRespirationValue" to "平均呼吸率",
        "moderateIntensityMinutes" to "中强度活动",
        "vigorousIntensityMinutes" to "高强度活动",
        "floorsAscended" to "爬楼",
        "floorsAscendedInMeters" to "爬升高度",
        "sleepTimeSeconds" to "睡眠时长",
        "deepSleepSeconds" to "深睡",
        "lightSleepSeconds" to "浅睡",
        "remSleepSeconds" to "REM",
        "awakeSleepSeconds" to "清醒",
        "sleepScore" to "睡眠评分",
        "duration" to "时长",
        "elapsedDuration" to "总耗时",
        "movingDuration" to "移动时间",
        "sumDuration" to "总时长",
        "averageSpeed" to "平均速度",
        "maxSpeed" to "最高速度",
        "averageCadence" to "平均步频",
        "maxCadence" to "最高步频",
        "avgPower" to "平均功率",
        "maxPower" to "最大功率",
        "elevationGain" to "爬升",
        "elevationLoss" to "下降",
        "waterEstimated" to "估计汗液流失",
        "sweatLoss" to "估计汗液流失",
        "aerobicTrainingEffect" to "有氧训练效果",
        "anaerobicTrainingEffect" to "无氧训练效果",
        "trainingEffectLabel" to "训练效果",
        "trainingReadinessScore" to "训练准备度",
        "trainingStatus" to "训练状态",
        "hrvStatus" to "HRV 状态",
        "lastNightAvg" to "昨夜 HRV",
        "weeklyAvg" to "7日 HRV",
        "weight" to "体重",
        "weightInGrams" to "体重",
        "bmi" to "BMI",
        "bodyFat" to "体脂率",
        "muscleMass" to "肌肉量",
        "hydrationGoal" to "饮水目标",
    )

    private val ArrayLabels = mapOf(
        "heartRateValues" to "心率采样",
        "heartRateValueDescriptors" to "心率描述",
        "stressValuesArray" to "压力采样",
        "bodyBatteryValuesArray" to "身体电量采样",
        "bodyBatteryValues" to "身体电量采样",
        "respirationValuesArray" to "呼吸采样",
        "spo2Values" to "血氧采样",
        "dailyMovementDTOList" to "全天活动曲线",
        "sleepLevels" to "睡眠分期",
        "sleepScores" to "睡眠评分项",
        "splitDTOs" to "运动分段",
        "lapDTOs" to "运动计圈",
        "activityDetailMetrics" to "运动明细指标",
        "geoPolylineDTO" to "GPS 轨迹",
        "activityLaps" to "运动计圈",
        "exerciseSets" to "训练组",
    )
}

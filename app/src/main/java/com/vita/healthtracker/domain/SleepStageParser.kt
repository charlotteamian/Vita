package com.vita.healthtracker.domain

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/** 睡眠分期 (与成熟手表 app 的催眠图同一口径)。 */
enum class SleepStage {
    DEEP,
    LIGHT,
    REM,
    AWAKE,
}

/** 一段连续的同一分期区间。 */
data class SleepStageSegment(
    val startEpochMs: Long,
    val endEpochMs: Long,
    val stage: SleepStage,
)

/**
 * 从佳明 `dailySleepData` 原始 JSON (garmin_raw_record, categoryKey="sleep") 里解析
 * `sleepLevels` 分期区间, 供睡眠详情页画整晚深浅图。
 *
 * sleepLevels 每项形如 {"startGMT":"2026-06-07T14:23:00.0","endGMT":"...","activityLevel":1.0},
 * 时间是 GMT, activityLevel: 0=深睡 1=浅睡 2=REM 3=清醒。
 * 解析失败/字段缺失一律返回空列表, 由 UI 退化为只显示汇总。
 */
object SleepStageParser {

    private val GMT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.S")

    fun parse(payloadJson: String): List<SleepStageSegment> = runCatching {
        val root = JSONObject(payloadJson)
        val levels = root.optJSONArray("sleepLevels") ?: return emptyList()
        val segments = ArrayList<SleepStageSegment>(levels.length())
        for (i in 0 until levels.length()) {
            val item = levels.optJSONObject(i) ?: continue
            val start = parseGmt(item.optString("startGMT")) ?: continue
            val end = parseGmt(item.optString("endGMT")) ?: continue
            if (end <= start) continue
            val stage = when (item.optDouble("activityLevel", -1.0).toInt()) {
                0 -> SleepStage.DEEP
                1 -> SleepStage.LIGHT
                2 -> SleepStage.REM
                3 -> SleepStage.AWAKE
                else -> continue
            }
            segments += SleepStageSegment(start, end, stage)
        }
        // 排序 + 合并相邻同分期 (个别响应会把同一分期切成连续小段)
        segments.sortBy { it.startEpochMs }
        val merged = ArrayList<SleepStageSegment>(segments.size)
        for (seg in segments) {
            val last = merged.lastOrNull()
            if (last != null && last.stage == seg.stage && seg.startEpochMs <= last.endEpochMs) {
                merged[merged.size - 1] = last.copy(endEpochMs = maxOf(last.endEpochMs, seg.endEpochMs))
            } else {
                merged += seg
            }
        }
        merged
    }.getOrDefault(emptyList())

    private fun parseGmt(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val local = runCatching { LocalDateTime.parse(text, GMT_FMT) }
            .recoverCatching { LocalDateTime.parse(text) }
            .getOrNull() ?: return null
        return local.toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}

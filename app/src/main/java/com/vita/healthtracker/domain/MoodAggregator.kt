package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.MoodEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/** 一天里的一条情绪时刻 (展示与分析用的视图模型)。 */
data class MoodMoment(
    val id: String,
    val recordedAtEpochMs: Long,
    val time: LocalTime,
    val moodId: String,
    val note: String,
    /** 记这一刻时的天气 (WeatherCatalog id), 可空。 */
    val weatherId: String? = null,
)

/**
 * 一天的情绪聚合。把当天多条时刻收敛成既能展示又能进分析的代表值。
 *
 * - [avgValence]    当天各时刻「价」(1..5) 的平均; 判断这一天整体偏正还是偏负的主信号。
 * - [volatility]    当天最高价 − 最低价 (单条记录为 0); 反映一天内情绪起伏, 是时刻制带来的新信号。
 * - [dominantMoodId] 最能代表这一天的情绪: 出现次数最多; 并列取「价」最接近当天均值的那种,
 *                   仍并列再取当天最早出现的。刻意不用「最后一条」——当天多为正向、只有末尾一条负向时,
 *                   主导不应退化成最后记的情绪。
 * - [lastMoodId]    当天最后一条时刻的情绪 (仅备用, 展示/分析都用 [dominantMoodId])。
 */
data class MoodDaily(
    val date: LocalDate,
    val moments: List<MoodMoment>,
    val avgValence: Double,
    val volatility: Int,
    val dominantMoodId: String,
    val lastMoodId: String,
) {
    val count: Int get() = moments.size
}

/** 把扁平的情绪时刻列表按本地日期聚合。纯函数, 无状态。 */
object MoodAggregator {

    fun moment(entry: MoodEntry, zone: ZoneId): MoodMoment? {
        val time = runCatching {
            Instant.ofEpochMilli(entry.recordedAtEpochMs).atZone(zone).toLocalTime()
        }.getOrNull() ?: return null
        return MoodMoment(
            id = entry.id,
            recordedAtEpochMs = entry.recordedAtEpochMs,
            time = time,
            moodId = entry.moodId,
            note = entry.note,
            weatherId = entry.weatherId,
        )
    }

    /** 按 [MoodEntry.date] 分组聚合; 跳过无法解析日期或没有有效情绪的天。 */
    fun daily(entries: List<MoodEntry>, zone: ZoneId): Map<LocalDate, MoodDaily> =
        entries
            .groupBy { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .mapNotNull { (date, rows) ->
                if (date == null) return@mapNotNull null
                val moments = rows.mapNotNull { moment(it, zone) }
                    .sortedBy { it.recordedAtEpochMs }
                buildDaily(date, moments)?.let { date to it }
            }
            .toMap()

    private fun buildDaily(date: LocalDate, moments: List<MoodMoment>): MoodDaily? {
        val valenced = moments.mapNotNull { m -> MoodCatalog.valence(m.moodId)?.let { m to it } }
        if (valenced.isEmpty()) return null
        val values = valenced.map { it.second }
        val mean = values.average()
        val dominant = valenced
            .groupBy { it.first.moodId }
            .entries
            // 次数最多者胜; 并列时取「价」最接近当天均值的 (最能代表这一天),
            // 仍并列取当天最早出现的——都不用「最后一条」, 避免主导退化成末尾情绪。
            .maxWith(
                compareBy<Map.Entry<String, List<Pair<MoodMoment, Int>>>> { it.value.size }
                    .thenByDescending { entry -> abs(entry.value.first().second - mean) }
                    .thenByDescending { entry -> entry.value.minOf { it.first.recordedAtEpochMs } },
            )
            .key
        return MoodDaily(
            date = date,
            moments = moments,
            avgValence = mean,
            volatility = (values.max() - values.min()),
            dominantMoodId = dominant,
            lastMoodId = moments.last().moodId,
        )
    }
}

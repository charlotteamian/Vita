package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import com.vita.healthtracker.data.local.entity.MoodEntry
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.local.entity.WeatherEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 本地关联分析库。
 *
 * 只做可解释的分组对照，不上传数据，也不把相关性写成因果。样本不足或差异太小时，
 * 返回“继续积累”而不是勉强生成结论。
 */
object LocalAssociationAnalyzer {

    fun analyze(
        daily: List<DailyHealthSnapshot>,
        sleeps: List<SleepSession>,
        exercises: List<ExerciseSession>,
        habits: List<HabitDefinition>,
        habitCheckIns: List<HabitCheckIn>,
        moods: List<MoodEntry>,
        cycleEntries: List<CycleEntry>,
        weather: List<WeatherEntry>,
        zone: ZoneId,
    ): LocalAssociationReport {
        val dailyByDate = daily.mapNotNull { row ->
            runCatching { LocalDate.parse(row.date) }.getOrNull()?.let { it to row }
        }.toMap()
        val sleepByDate = sleeps
            .filter { it.totalMinutes in 1L..(16L * 60L) }
            .mapNotNull { sleep -> sleepDisplayDate(sleep, zone)?.let { it to sleep } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, rows) -> rows.maxByOrNull { it.totalMinutes }!! }

        return LocalAssociationReport(
            topics = listOf(
                exerciseAndSleep(exercises, sleepByDate, zone),
                cycleAndStress(dailyByDate, cycleEntries),
                moodAndRecovery(dailyByDate, moods),
                moodAndSleep(sleepByDate, moods),
                weatherAndMood(weather, moods),
                habitsAndRecovery(dailyByDate, habits, habitCheckIns),
            ),
        )
    }

    private fun moodAndSleep(
        sleeps: Map<LocalDate, SleepSession>,
        moods: List<MoodEntry>,
    ): LocalAssociationTopic {
        val groups = moods.mapNotNull { mood ->
            val date = parseDate(mood.date) ?: return@mapNotNull null
            val minutes = sleeps[date]?.totalMinutes?.toDouble() ?: return@mapNotNull null
            when (mood.moodId) {
                in POSITIVE_MOODS -> "正向情绪" to minutes
                in NEGATIVE_MOODS -> "负向情绪" to minutes
                else -> null
            }
        }.groupBy({ it.first }, { it.second })
        val positive = groups["正向情绪"].orEmpty()
        val negative = groups["负向情绪"].orEmpty()
        if (positive.size < 3 || negative.size < 3) return insufficient(
            "情绪与前一晚睡眠",
            "不同心情各记录几天，并且这些日期有睡眠数据后，才能尝试比较",
            positive.size + negative.size,
        )
        val difference = positive.average() - negative.average()
        if (abs(difference) < 30.0) return stable("情绪与前一晚睡眠", "已经看过 ${positive.size + negative.size} 天记录，暂时没发现心情和睡眠时长有明显联系。")
        return LocalAssociationTopic(
            title = "情绪与前一晚睡眠",
            finding = "你心情较好的日子，前一晚睡眠平均${if (difference > 0) "多" else "少"} ${minutesText(abs(difference))}。",
            evidence = "心情较好 ${positive.size} 天；心情较低 ${negative.size} 天",
            note = "这只是你记录里的一个现象，不代表睡眠一定导致了心情变化。",
        )
    }

    private fun weatherAndMood(
        weather: List<WeatherEntry>,
        moods: List<MoodEntry>,
    ): LocalAssociationTopic {
        val moodsByDate = moods.associateBy { it.date }
        val groups = weather.mapNotNull { entry ->
            val kind = WeatherCatalog.byId(entry.weatherId) ?: return@mapNotNull null
            val moodScore = moodScore(moodsByDate[entry.date]?.moodId) ?: return@mapNotNull null
            kind to moodScore
        }.groupBy({ it.first.label }, { it.second }).filterValues { it.size >= 3 }
        if (groups.size < 2) return insufficient("天气与情绪", "需要多记录几种天气下的心情，才好看出差别", groups.values.sumOf { it.size })
        val averages = groups.mapValues { (_, values) -> values.average() }
        val highest = averages.maxByOrNull { it.value } ?: return stable("天气与情绪", "天气记录还不够分组比较。")
        val lowest = averages.minByOrNull { it.value } ?: return stable("天气与情绪", "天气记录还不够分组比较。")
        val difference = highest.value - lowest.value
        if (difference < 0.8) return stable("天气与情绪", "已经看过 ${groups.values.sumOf { it.size }} 天记录，暂时没发现天气和心情有明显联系。")
        return LocalAssociationTopic(
            title = "天气与情绪",
            finding = "${highest.key}天气下，你记录的心情平均比${lowest.key}高 ${"%.1f".format(difference)} 档。",
            evidence = "${highest.key} ${groups.getValue(highest.key).size} 天；${lowest.key} ${groups.getValue(lowest.key).size} 天",
            note = "天气来自你的手动记录，只用来帮助回看生活环境。",
        )
    }

    private fun exerciseAndSleep(
        exercises: List<ExerciseSession>,
        sleeps: Map<LocalDate, SleepSession>,
        zone: ZoneId,
    ): LocalAssociationTopic {
        val samples = exercises
            .groupBy { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }
            .mapNotNull { (date, rows) ->
                val nextSleep = sleeps[date.plusDays(1)] ?: return@mapNotNull null
                ExerciseSleepSample(rows.sumOf(::exerciseLoad), nextSleep.totalMinutes.toDouble())
            }
            .sortedBy { it.load }
        if (samples.size < 6) return insufficient("运动与睡眠", "多记录几次运动和之后的睡眠，才好看出规律", samples.size)
        val midpoint = samples.size / 2
        val lighter = samples.take(midpoint)
        val higher = samples.takeLast(midpoint)
        val difference = higher.map { it.sleepMinutes }.average() - lighter.map { it.sleepMinutes }.average()
        if (abs(difference) < 30.0) return stable(
            "运动与睡眠",
            "已经看过 ${samples.size} 个运动日，暂时没发现运动强度和之后睡眠时长有明显联系。",
        )
        return LocalAssociationTopic(
            title = "运动与睡眠",
            finding = "运动量较高之后，你下一晚睡眠平均${if (difference > 0) "多" else "少"} ${minutesText(abs(difference))}。",
            evidence = "运动量较高 ${higher.size} 天；较轻松 ${lighter.size} 天",
            note = "这里只帮助你回看运动和睡眠是否常常一起变化。",
        )
    }

    private fun cycleAndStress(
        daily: Map<LocalDate, DailyHealthSnapshot>,
        entries: List<CycleEntry>,
    ): LocalAssociationTopic {
        val starts = entries.filter { it.isPeriodStart }.mapNotNull { parseDate(it.date) }.distinct().sorted()
        val gaps = starts.zipWithNext { first, second -> second.toEpochDay() - first.toEpochDay() }.filter { it in 21L..45L }
        val cycleLength = gaps.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            ?: return insufficient("周期与压力", "多记录几次经期开始日后，才能尝试比较周期不同阶段的压力", starts.size)
        val periodDates = entries.filter { it.flow > 0 }.mapNotNull { parseDate(it.date) }.toSet()
        val groups = daily.mapNotNull { (date, row) ->
            val stress = row.avgStress?.takeIf { it > 0 } ?: return@mapNotNull null
            cyclePhase(date, starts, periodDates, cycleLength)?.let { it to stress.toDouble() }
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.takeIf { it.size >= 3 } }
            .filterValues { it != null }
            .mapValues { (_, values) -> values!! }
        if (groups.size < 2) return insufficient("周期与压力", "不同周期阶段都多积累几天压力记录后，才好比较", groups.values.sumOf { it.size })
        val averages = groups.mapValues { (_, values) -> values.average() }
        val highest = averages.maxByOrNull { it.value } ?: return stable("周期与压力", "周期记录还不够分阶段比较。")
        val lowest = averages.minByOrNull { it.value } ?: return stable("周期与压力", "周期记录还不够分阶段比较。")
        val difference = highest.value - lowest.value
        if (difference < 5.0) return stable("周期与压力", "已经看过 ${groups.values.sumOf { it.size }} 天记录，暂时没发现不同周期阶段的压力差别。")
        return LocalAssociationTopic(
            title = "周期与压力",
            finding = "${highest.key}的平均压力比${lowest.key}高 ${difference.roundToInt()} 点。",
            evidence = "${highest.key} ${groups.getValue(highest.key).size} 天；${lowest.key} ${groups.getValue(lowest.key).size} 天",
            note = "周期阶段是根据你的经期记录大致推算，仅用于生活回看。",
        )
    }

    private fun moodAndRecovery(
        daily: Map<LocalDate, DailyHealthSnapshot>,
        moods: List<MoodEntry>,
    ): LocalAssociationTopic {
        val groups = moods.mapNotNull { mood ->
            val date = parseDate(mood.date) ?: return@mapNotNull null
            val score = daily[date]?.recoveryIndex() ?: return@mapNotNull null
            when (mood.moodId) {
                in POSITIVE_MOODS -> "正向情绪" to score
                in NEGATIVE_MOODS -> "负向情绪" to score
                else -> null
            }
        }.groupBy({ it.first }, { it.second })
        val positive = groups["正向情绪"].orEmpty()
        val negative = groups["负向情绪"].orEmpty()
        if (positive.size < 3 || negative.size < 3) return insufficient(
            "情绪和状态",
            "不同心情各记录几天，并且这些日期有身体数据后，才能尝试比较",
            positive.size + negative.size,
        )
        val difference = positive.average() - negative.average()
        if (abs(difference) < 8.0) return stable("情绪和状态", "已经看过 ${positive.size + negative.size} 天记录，暂时没发现不同心情对应的状态差别。")
        return LocalAssociationTopic(
            title = "情绪和状态",
            finding = "记录好心情的日子，状态平均${if (difference > 0) "高" else "低"} ${abs(difference).roundToInt()} 分。",
            evidence = "心情较好 ${positive.size} 天；心情较低 ${negative.size} 天",
            note = "状态由当天可用的身体电量和压力综合参考，只描述记录里常常一起出现的变化。",
        )
    }

    private fun habitsAndRecovery(
        daily: Map<LocalDate, DailyHealthSnapshot>,
        habits: List<HabitDefinition>,
        checkIns: List<HabitCheckIn>,
    ): LocalAssociationTopic {
        val candidates = habits.mapNotNull { habit ->
            val rows = checkIns.filter { it.habitId == habit.id }.mapNotNull { row ->
                val score = parseDate(row.date)?.let(daily::get)?.recoveryIndex() ?: return@mapNotNull null
                row.status to score
            }
            val done = rows.filter { it.first == HabitCheckIn.StatusDone }.map { it.second }
            val missed = rows.filter { it.first == HabitCheckIn.StatusMissed }.map { it.second }
            if (
                done.size < MIN_HABIT_GROUP_DAYS ||
                missed.size < MIN_HABIT_GROUP_DAYS ||
                done.size + missed.size < MIN_HABIT_TOTAL_DAYS
            ) return@mapNotNull null
            HabitAssociation(habit.name, done.size, missed.size, done.average() - missed.average())
        }
        val lead = candidates.maxByOrNull { abs(it.difference) }
            ?: return insufficient(
                "习惯和状态",
                "同一个习惯需要多积累一些完成和未完成记录，才好看出它是否和状态一起变化",
                checkIns.size,
            )
        if (abs(lead.difference) < 8.0) return stable("习惯和状态", "已经有一些打卡记录，但完成和未完成那几天的状态差别还不明显。")
        return LocalAssociationTopic(
            title = "习惯和状态",
            finding = "你完成「${lead.name}」的日子，状态平均${if (lead.difference > 0) "高" else "低"} ${abs(lead.difference).roundToInt()} 分。",
            evidence = "完成 ${lead.doneDays} 天；明确没完成 ${lead.missedDays} 天",
            note = "这只是记录里常常一起出现的变化，不说明这个习惯直接影响了状态。没打卡的日期不会拿来比较。",
        )
    }

    private fun exerciseLoad(exercise: ExerciseSession): Double {
        val heartRateFactor = when {
            (exercise.avgHeartRate ?: 0) >= 145 -> 1.5
            (exercise.avgHeartRate ?: 0) >= 120 -> 1.25
            else -> 1.0
        }
        val trainingFactor = exercise.trainingEffect?.coerceIn(0.0, 5.0)?.times(8.0) ?: 0.0
        return exercise.durationMinutes * heartRateFactor + (exercise.activeCalories ?: 0.0) / 10.0 + trainingFactor
    }

    private fun cyclePhase(date: LocalDate, starts: List<LocalDate>, periodDates: Set<LocalDate>, length: Int): String? {
        if (date in periodDates) return "经期"
        val previous = starts.lastOrNull { !it.isAfter(date) } ?: return null
        val day = (date.toEpochDay() - previous.toEpochDay()).toInt()
        if (day !in 0 until length) return null
        return when {
            day < (length - 15).coerceAtLeast(6) -> "卵泡期"
            day <= length - 12 -> "排卵期附近"
            else -> "黄体期"
        }
    }

    private fun DailyHealthSnapshot.recoveryIndex(): Double? {
        val values = listOfNotNull(
            bodyBatteryHigh?.takeIf { it in 1..100 }?.toDouble(),
            avgStress?.takeIf { it in 1..100 }?.let { 100.0 - it },
        )
        return values.takeIf { it.isNotEmpty() }?.average()
    }

    private fun insufficient(title: String, requirement: String, recorded: Int) = LocalAssociationTopic(
        title = title,
        finding = null,
        evidence = "$requirement。现在可用 $recorded 条记录。",
        note = "记录还少，先不急着下结论。",
    )

    private fun stable(title: String, text: String) = LocalAssociationTopic(
        title = title,
        finding = null,
        evidence = text,
        note = "暂时没看出稳定模式。",
    )

    private fun sleepDisplayDate(sleep: SleepSession, zone: ZoneId): LocalDate? = runCatching {
        Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate()
    }.getOrNull()

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun minutesText(value: Double): String {
        val minutes = value.roundToInt()
        return if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分钟" else "$minutes 分钟"
    }

    private fun moodScore(id: String?): Double? = when (id) {
        "joy" -> 5.0
        "content", "calm" -> 4.0
        "neutral" -> 3.0
        "tired", "stressed" -> 2.0
        "down", "irritated" -> 1.0
        else -> null
    }

    private val POSITIVE_MOODS = setOf("joy", "calm", "content")
    private val NEGATIVE_MOODS = setOf("tired", "stressed", "down", "irritated")

    private const val MIN_HABIT_GROUP_DAYS = 7
    private const val MIN_HABIT_TOTAL_DAYS = 21
}

data class LocalAssociationReport(
    val topics: List<LocalAssociationTopic>,
) {
    val insightCount: Int get() = topics.count { it.finding != null }
}

data class LocalAssociationTopic(
    val title: String,
    val finding: String?,
    val evidence: String,
    val note: String,
)

private data class ExerciseSleepSample(
    val load: Double,
    val sleepMinutes: Double,
)

private data class HabitAssociation(
    val name: String,
    val doneDays: Int,
    val missedDays: Int,
    val difference: Double,
)

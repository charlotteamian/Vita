package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/**
 * 单枚健身徽章的进度。current/target 都做了上限钳制，便于直接画进度条。
 */
data class FitnessProgress(val current: Int, val target: Int) {
    val earned: Boolean get() = current >= target
    val ratio: Float get() =
        if (target <= 0) 0f else (current.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

/**
 * 12 枚健身徽章触发逻辑。读 ExerciseSession + DailyHealthSnapshot，不依赖 longestStreak。
 *
 * 注意：分类（力量 / 爬坡 / 跑步 / 有氧）走的是字符串包含匹配，
 * 同时识别 `type` 和 `customCategory`，因为佳明 / HC 字段名差异较大。
 * 后续若与 ExerciseClassifier 对齐，把这里的关键字常量挪过去即可。
 */
object FitnessBadgeEvaluator {

    // 力量训练关键字（type / customCategory 任一命中即可）
    private val StrengthKeywords = setOf(
        "strength", "weight", "gym", "resistance", "力量", "举铁", "健身", "无器械",
    )

    // 爬坡 / 海拔类
    private val ClimbKeywords = setOf(
        "climb", "hike", "stair", "mountaineer", "trail", "爬坡", "登山", "徒步", "爬楼",
    )

    // 跑步类（含跑步机 / 户外 / 越野）
    private val RunKeywords = setOf(
        "run", "treadmill", "jog", "trail_run", "跑步", "越野", "跑步机",
    )

    // 一般有氧（耐力卡 fit_core_battery 接受的运动类型）
    private val AerobicKeywords = setOf(
        "run", "bike", "cycling", "ride", "row", "swim", "elliptical", "treadmill",
        "hike", "walk", "cardio", "跑", "骑", "划船", "游泳", "椭圆", "有氧", "步行",
    )

    /** 评估一枚徽章在当前数据下的进度。 */
    fun evaluate(
        badgeId: String,
        sessions: List<ExerciseSession>,
        snapshots: List<DailyHealthSnapshot>,
        weekStart: DayOfWeek,
        today: LocalDate = LocalDate.now(),
    ): FitnessProgress {
        val valid = sessions.filter { it.isValidForFitnessBadge() }

        return when (badgeId) {

            // ───── 01 启动 ─────
            "fit_first_sweat" -> {
                FitnessProgress(min(valid.size, 1), 1)
            }
            "fit_portal_entry" -> {
                val peak = valid.maxOfOrNull { it.maxHeartRate ?: 0 } ?: 0
                FitnessProgress(min(peak, 150), 150)
            }

            // ───── 02 节奏 ─────
            "fit_rhythm" -> {
                val daysWithSession = valid
                    .map { it.localDate() }
                    .distinct()
                    .sorted()
                val bestInWindow = daysWithSession.indices.maxOfOrNull { i ->
                    val windowEnd = daysWithSession[i].plusDays(7)
                    daysWithSession.count { it >= daysWithSession[i] && it < windowEnd }
                } ?: 0
                FitnessProgress(min(bestInWindow, 3), 3)
            }
            "fit_week_combo" -> {
                val bestWeek = valid
                    .groupBy { startOfWeek(it.localDate(), weekStart) }
                    .values
                    .maxOfOrNull { weekSessions -> weekSessions.map { it.localDate() }.distinct().size } ?: 0
                FitnessProgress(min(bestWeek, 5), 5)
            }

            // ───── 03 力量 ─────
            "fit_force_atom" -> {
                val n = valid.count { it.isStrength() }
                FitnessProgress(min(n, 10), 10)
            }
            "fit_beast_subject" -> {
                val strength = valid.filter { it.isStrength() }.sortedBy { it.startEpochMs }
                val pr = strength.withIndex().any { (i, s) ->
                    i > 0 && s.durationMinutes > strength.subList(0, i).maxOf { it.durationMinutes }
                }
                FitnessProgress(if (pr) 1 else 0, 1)
            }

            // ───── 04 耐力 ─────
            "fit_core_battery" -> {
                val hit = valid.any { it.isAerobic() && it.durationMinutes >= 60 }
                FitnessProgress(if (hit) 1 else 0, 1)
            }
            "fit_step_warp" -> {
                val peakDay = snapshots.maxOfOrNull { it.steps.toInt() } ?: 0
                FitnessProgress(min(peakDay, 20_000), 20_000)
            }

            // ───── 05 突破 ─────
            "fit_climb_rift" -> {
                val n = valid.count { s ->
                    s.isClimb() || (s.elevationGainMeters ?: 0.0) >= 100.0
                }
                FitnessProgress(min(n, 5), 5)
            }
            "fit_burn_lab" -> {
                val totalKcal = snapshots.sumOf { (it.activeCalories ?: 0.0) }.toInt()
                FitnessProgress(min(totalKcal, 10_000), 10_000)
            }
            "fit_blue_rift" -> {
                // 把跑步按 2 km 区间分桶，同桶内后跑赢前跑（avgSpeed 更大）即 PR
                val runs = valid.filter {
                    it.isRun() && (it.distanceMeters ?: 0.0) >= 1_000.0 && (it.avgSpeed ?: 0.0) > 0.0
                }
                val pr = runs.groupBy { ((it.distanceMeters!! / 2_000.0).toInt()) }
                    .values
                    .any { bucket ->
                        val sorted = bucket.sortedBy { it.startEpochMs }
                        sorted.withIndex().any { (i, s) ->
                            i > 0 && (s.avgSpeed ?: 0.0) > sorted.subList(0, i).maxOf { it.avgSpeed ?: 0.0 }
                        }
                    }
                FitnessProgress(if (pr) 1 else 0, 1)
            }

            // ───── 06 巅峰 ─────
            "fit_lab_king" -> {
                val streak = longestConsecutiveTrainingDays(valid)
                FitnessProgress(min(streak, 365), 365)
            }

            else -> FitnessProgress(0, 1)
        }
    }

    /** 一次性算出所有已点亮的健身徽章 id。 */
    fun earnedIds(
        sessions: List<ExerciseSession>,
        snapshots: List<DailyHealthSnapshot>,
        weekStart: DayOfWeek,
        today: LocalDate = LocalDate.now(),
    ): Set<String> = HabitBadgeCatalog.fitnessBadges
        .filter { evaluate(it.id, sessions, snapshots, weekStart, today).earned }
        .mapTo(mutableSetOf()) { it.id }

    /** 一次性算出所有徽章的进度，喂给详情页。 */
    fun allProgress(
        sessions: List<ExerciseSession>,
        snapshots: List<DailyHealthSnapshot>,
        weekStart: DayOfWeek,
        today: LocalDate = LocalDate.now(),
    ): Map<String, FitnessProgress> = HabitBadgeCatalog.fitnessBadges
        .associate { it.id to evaluate(it.id, sessions, snapshots, weekStart, today) }

    // ─────────────────────────────────────────────────────────

    private fun longestConsecutiveTrainingDays(valid: List<ExerciseSession>): Int {
        val days = valid.map { it.localDate() }.distinct().sorted()
        if (days.isEmpty()) return 0
        var longest = 1
        var cur = 1
        for (i in 1 until days.size) {
            cur = if (days[i - 1].plusDays(1) == days[i]) cur + 1 else 1
            if (cur > longest) longest = cur
        }
        return longest
    }

    private fun startOfWeek(date: LocalDate, weekStart: DayOfWeek): LocalDate {
        val diff = (date.dayOfWeek.value - weekStart.value + 7) % 7
        return date.minusDays(diff.toLong())
    }

    private fun ExerciseSession.localDate(): LocalDate =
        java.time.Instant.ofEpochMilli(startEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

    /** 至少 5 分钟、未删除、有心率/距离/卡路里任一信号，才认为是「有效训练」。 */
    private fun ExerciseSession.isValidForFitnessBadge(): Boolean {
        if (isDeleted) return false
        if (durationMinutes < 5) return false
        val hasSignal = (avgHeartRate ?: 0) > 0
            || (distanceMeters ?: 0.0) > 0
            || (activeCalories ?: 0.0) > 0
            || (steps ?: 0L) > 0
        return hasSignal
    }

    private fun ExerciseSession.matchesAny(keywords: Set<String>): Boolean {
        val t = type.lowercase()
        val c = customCategory?.lowercase().orEmpty()
        return keywords.any { kw -> t.contains(kw) || c.contains(kw) }
    }

    private fun ExerciseSession.isStrength(): Boolean = matchesAny(StrengthKeywords)
    private fun ExerciseSession.isClimb(): Boolean = matchesAny(ClimbKeywords)
    private fun ExerciseSession.isRun(): Boolean = matchesAny(RunKeywords)
    private fun ExerciseSession.isAerobic(): Boolean = matchesAny(AerobicKeywords)
}

package com.vita.healthtracker.data.ai

import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 压缩多年历史给 AI: 保留逐年概貌和最明显的相邻年份变化, 避免把十年逐日噪音全塞进模型。
 */
object AiLongTermSummaryBuilder {

    fun build(
        daily: List<DailyHealthSnapshot>,
        sleeps: List<SleepSession>,
        exercises: List<ExerciseSession>,
        cycles: List<CycleEntry>,
        zone: ZoneId,
    ): AiLongTermSummary {
        val dailyRows = daily.mapNotNull { snapshot ->
            parseDate(snapshot.date)?.let { it to snapshot }
        }.sortedBy { it.first }
        val primarySleeps = primarySleepsByWakeDate(sleeps, zone)
        val exerciseRows = exercises
            .filter { !it.isDeleted && it.durationMinutes > 0 }
            .mapNotNull { exercise ->
                runCatching {
                    Instant.ofEpochMilli(exercise.startEpochMs).atZone(zone).toLocalDate() to exercise
                }.getOrNull()
            }
            .sortedBy { it.first }
        val cycleRows = cycles.mapNotNull { entry ->
            parseDate(entry.date)?.let { it to entry }
        }

        val trackedDates = buildSet {
            dailyRows.forEach { (date, snapshot) -> if (snapshot.hasRealSignal()) add(date) }
            primarySleeps.forEach { (date, _) -> add(date) }
            exerciseRows.forEach { (date, _) -> add(date) }
            cycleRows.forEach { (date, entry) -> if (entry.flow > 0 || entry.isPeriodStart) add(date) }
        }
        val firstDate = trackedDates.minOrNull()
            ?: LocalDate.now(zone)
        val lastDate = trackedDates.maxOrNull() ?: firstDate

        val dailyByYear = dailyRows.groupBy { it.first.year }
        val sleepsByYear = primarySleeps.groupBy { it.first.year }
        val exercisesByYear = exerciseRows.groupBy { it.first.year }
        val cyclesByYear = cycleRows.groupBy { it.first.year }
        val years = (dailyByYear.keys + sleepsByYear.keys + exercisesByYear.keys + cyclesByYear.keys)
            .sorted()
            .map { year ->
                val yearDaily = dailyByYear[year].orEmpty().map { it.second }
                val yearSleeps = sleepsByYear[year].orEmpty().map { it.second }
                val yearExercises = exercisesByYear[year].orEmpty().map { it.second }
                val yearCycles = cyclesByYear[year].orEmpty().map { it.second }
                AiLongTermYear(
                    y = year,
                    days = dailyByYear[year].orEmpty().count { it.second.hasRealSignal() },
                    sleepNights = yearSleeps.size,
                    sleepMin = yearSleeps.map { it.totalMinutes.toDouble() }.roundedLongAverage(),
                    steps = yearDaily.mapNotNull { it.steps.takeIf { value -> value > 0 }?.toDouble() }.roundedLongAverage(),
                    activeMin = yearDaily.mapNotNull { it.activeMinutes?.takeIf { value -> value > 0 }?.toDouble() }.roundedLongAverage(),
                    rhr = yearDaily.mapNotNull { it.restingHeartRate?.takeIf { value -> value > 0 }?.toDouble() }.roundedIntAverage(),
                    hrv = yearDaily.mapNotNull { it.hrv?.takeIf { value -> value > 0 }?.toDouble() }.roundedIntAverage(),
                    stress = yearDaily.mapNotNull { it.avgStress?.takeIf { value -> value > 0 }?.toDouble() }.roundedIntAverage(),
                    bbHigh = yearDaily.mapNotNull { it.bodyBatteryHigh?.takeIf { value -> value > 0 }?.toDouble() }.roundedIntAverage(),
                    spo2 = yearDaily.mapNotNull { it.avgSpo2?.takeIf { value -> value > 0 }?.toDouble() }.roundedIntAverage(),
                    weight = yearDaily.mapNotNull { it.weightKg?.takeIf { value -> value > 0 } }.roundedDoubleAverage(),
                    exerciseCount = yearExercises.size,
                    exerciseMin = yearExercises.map { it.durationMinutes.toDouble() }.roundedLongAverage(),
                    exerciseKm = yearExercises.mapNotNull { it.distanceMeters?.takeIf { value -> value > 0 }?.div(1000.0) }.roundedDoubleAverage(),
                    cycleStarts = yearCycles.count { it.isPeriodStart },
                )
            }

        return AiLongTermSummary(
            from = firstDate.toString(),
            to = lastDate.toString(),
            trackedDays = trackedDates.size,
            years = years,
            shifts = buildShifts(years),
        )
    }

    private fun primarySleepsByWakeDate(
        sleeps: List<SleepSession>,
        zone: ZoneId,
    ): List<Pair<LocalDate, SleepSession>> =
        sleeps.asSequence()
            .filter { it.totalMinutes in 1L..(16L * 60L) && it.endEpochMs > 0 }
            .mapNotNull { sleep ->
                runCatching {
                    Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate() to sleep
                }.getOrNull()
            }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (date, sessions) ->
                sessions.maxByOrNull { it.totalMinutes }?.let { date to it }
            }
            .sortedBy { it.first }

    private fun buildShifts(years: List<AiLongTermYear>): List<AiLongTermShift> {
        fun metric(name: String, value: (AiLongTermYear) -> Number?): AiLongTermShift? =
            years.zipWithNext()
                .mapNotNull { (first, second) ->
                    val from = value(first)?.toDouble() ?: return@mapNotNull null
                    val to = value(second)?.toDouble() ?: return@mapNotNull null
                    AiLongTermShift(
                        metric = name,
                        fromY = first.y,
                        toY = second.y,
                        from = round1(from),
                        to = round1(to),
                        delta = round1(to - from),
                    )
                }
                .filter { abs(it.delta) > 0.0 }
                .maxByOrNull { abs(it.delta) }

        return listOfNotNull(
            metric("sleepMin") { it.sleepMin },
            metric("steps") { it.steps },
            metric("activeMin") { it.activeMin },
            metric("rhr") { it.rhr },
            metric("hrv") { it.hrv },
            metric("stress") { it.stress },
            metric("bbHigh") { it.bbHigh },
            metric("spo2") { it.spo2 },
            metric("weight") { it.weight },
            metric("exerciseCount") { it.exerciseCount.takeIf { count -> count > 0 } },
            metric("exerciseMin") { it.exerciseMin },
            metric("exerciseKm") { it.exerciseKm },
            metric("cycleStarts") { it.cycleStarts.takeIf { count -> count > 0 } },
        ).sortedByDescending { abs(it.delta) }.take(MAX_SHIFTS)
    }

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()

    private fun List<Double>.roundedLongAverage(): Long? =
        takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toLong()

    private fun List<Double>.roundedIntAverage(): Int? =
        takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    private fun List<Double>.roundedDoubleAverage(): Double? =
        takeIf { it.isNotEmpty() }?.average()?.let(::round1)

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

    private fun DailyHealthSnapshot.hasRealSignal(): Boolean =
        steps > 0 ||
            (distanceMeters ?: 0.0) > 0.0 ||
            (activeCalories ?: 0.0) > 0.0 ||
            (activeMinutes ?: 0L) > 0 ||
            (avgHeartRate ?: 0) > 0 ||
            (restingHeartRate ?: 0) > 0 ||
            (avgStress ?: 0) > 0 ||
            (avgSpo2 ?: 0) > 0 ||
            (avgRespiration ?: 0.0) > 0.0 ||
            (hrv ?: 0) > 0 ||
            (weightKg ?: 0.0) > 0.0

    private const val MAX_SHIFTS = 8
}

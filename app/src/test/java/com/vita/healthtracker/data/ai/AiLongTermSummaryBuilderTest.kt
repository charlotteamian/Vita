package com.vita.healthtracker.data.ai

import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.SleepSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLongTermSummaryBuilderTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `builds yearly averages and largest year shifts`() {
        val summary = AiLongTermSummaryBuilder.build(
            daily = listOf(
                daily("2020-01-01", steps = 5_000, rhr = 62),
                daily("2020-01-02", steps = 7_000, rhr = 60),
                daily("2021-01-01", steps = 9_000, rhr = 56),
                daily("2021-01-02", steps = 11_000, rhr = 54),
            ),
            sleeps = listOf(
                sleep("2020-short", "2020-01-01", minutes = 300),
                sleep("2020-main", "2020-01-01", minutes = 420),
                sleep("2021-main", "2021-01-01", minutes = 480),
            ),
            exercises = listOf(
                exercise("e2020", "2020-01-01", minutes = 30, meters = 3_000.0),
                exercise("e2021", "2021-01-01", minutes = 60, meters = 6_000.0),
            ),
            cycles = emptyList(),
            zone = zone,
        )

        assertEquals("2020-01-01", summary.from)
        assertEquals("2021-01-02", summary.to)
        assertEquals(4, summary.trackedDays)
        assertEquals(2, summary.years.size)

        val y2020 = summary.years.first { it.y == 2020 }
        assertEquals(2, y2020.days)
        assertEquals(6_000L, y2020.steps)
        assertEquals(61, y2020.rhr)
        assertEquals(420L, y2020.sleepMin)
        assertEquals(1, y2020.exerciseCount)
        assertEquals(30L, y2020.exerciseMin)
        assertEquals(3.0, y2020.exerciseKm ?: -1.0, 0.01)

        val sleepShift = summary.shifts.first { it.metric == "sleepMin" }
        assertEquals(2020, sleepShift.fromY)
        assertEquals(2021, sleepShift.toY)
        assertEquals(420.0, sleepShift.from, 0.01)
        assertEquals(480.0, sleepShift.to, 0.01)
        assertEquals(60.0, sleepShift.delta, 0.01)
        assertTrue(summary.shifts.any { it.metric == "steps" && it.delta == 4_000.0 })
    }

    private fun daily(date: String, steps: Long, rhr: Int): DailyHealthSnapshot =
        DailyHealthSnapshot(date = date, steps = steps, restingHeartRate = rhr)

    private fun sleep(id: String, wakeDate: String, minutes: Long): SleepSession {
        val end = LocalDate.parse(wakeDate).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        return SleepSession(
            id = id,
            startEpochMs = end - minutes * 60_000,
            endEpochMs = end,
            totalMinutes = minutes,
            deepMinutes = 0,
            lightMinutes = minutes,
            remMinutes = 0,
            awakeMinutes = 0,
            source = "test",
        )
    }

    private fun exercise(id: String, date: String, minutes: Long, meters: Double): ExerciseSession {
        val start = LocalDate.parse(date).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        return ExerciseSession(
            id = id,
            type = "步行",
            startEpochMs = start,
            endEpochMs = start + minutes * 60_000,
            durationMinutes = minutes,
            distanceMeters = meters,
            activeCalories = null,
            avgHeartRate = null,
            maxHeartRate = null,
            source = "test",
        )
    }
}

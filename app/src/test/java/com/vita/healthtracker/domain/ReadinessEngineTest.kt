package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.SleepSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessEngineTest {

    @Test
    fun `sleep below seven hours lowers otherwise similar day`() {
        val shortSleep = evaluate(sleepMinutes = 6 * 60L + 24L, spo2 = 98)
        val enoughSleep = evaluate(sleepMinutes = 8 * 60L, spo2 = 98)

        assertTrue(shortSleep.overall < enoughSleep.overall)
        assertTrue(shortSleep.limits.any { it.metric == ScoreMetric.SLEEP })
        assertFalse(shortSleep.insights.any { it.title == "恢复充分，可以加量" })
    }

    @Test
    fun `spo2 below 95 applies dynamic shortfall correction`() {
        val normalSpo2 = evaluate(sleepMinutes = 8 * 60L, spo2 = 98)
        val mildlyLowSpo2 = evaluate(sleepMinutes = 8 * 60L, spo2 = 94)

        assertTrue(mildlyLowSpo2.overall < normalSpo2.overall)
        assertTrue(mildlyLowSpo2.weightedOverall > mildlyLowSpo2.overall)
        assertTrue(mildlyLowSpo2.limits.any { it.metric == ScoreMetric.SPO2 })
        assertFalse(mildlyLowSpo2.insights.any { it.title == "恢复充分，可以加量" })
    }

    @Test
    fun `different recovery shortfalls do not collapse to same score`() {
        val shortButEfficientSleep = evaluate(sleepMinutes = 6 * 60L + 24L, spo2 = 98)
        val enoughSleepWithLowSpo2 = evaluate(sleepMinutes = 7 * 60L + 47L, spo2 = 94, awakeMinutes = 60L)

        assertNotEquals(shortButEfficientSleep.overall, enoughSleepWithLowSpo2.overall)
    }

    @Test
    fun `same low spo2 still keeps sleep difference visible`() {
        val shortSleepWithLowSpo2 = evaluate(sleepMinutes = 6 * 60L + 24L, spo2 = 94)
        val enoughSleepWithLowSpo2 = evaluate(sleepMinutes = 7 * 60L + 47L, spo2 = 94, awakeMinutes = 60L)

        assertTrue(shortSleepWithLowSpo2.overall < enoughSleepWithLowSpo2.overall)
        assertNotEquals(shortSleepWithLowSpo2.overall, enoughSleepWithLowSpo2.overall)
    }

    private fun evaluate(sleepMinutes: Long, spo2: Int, awakeMinutes: Long = 5L): StatusScore =
        requireNotNull(
            ReadinessEngine.evaluate(
                date = "2026-06-02",
                today = DailyHealthSnapshot(
                    date = "2026-06-02",
                    restingHeartRate = 54,
                    avgSpo2 = spo2,
                    avgStress = 20,
                    bodyBatteryHigh = 90,
                ),
                lastSleep = SleepSession(
                    id = "sleep",
                    startEpochMs = 0,
                    endEpochMs = 1,
                    totalMinutes = sleepMinutes,
                    deepMinutes = 60,
                    lightMinutes = 240,
                    remMinutes = 80,
                    awakeMinutes = awakeMinutes,
                    source = "test",
                ),
                baselineDaily = emptyList(),
                baselineSleep = emptyList(),
            )
        )
}

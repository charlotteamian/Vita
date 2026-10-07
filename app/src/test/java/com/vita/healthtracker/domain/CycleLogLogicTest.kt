package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.CycleEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleLogLogicTest {

    @Test
    fun `spotting entries are excluded from period groups`() {
        val entries = listOf(
            CycleEntry(
                date = "2026-06-06",
                flow = 1,
                isPeriodStart = false,
                notes = CycleLogLogic.NOTE_INTERMENSTRUAL_BLEEDING,
            ),
            CycleEntry(date = "2026-06-09", flow = 4, isPeriodStart = true),
        )

        val periods = CycleLogLogic.groupIntoPeriods(entries)
        val spotting = CycleLogLogic.spottingEntries(entries)

        assertEquals(1, periods.size)
        assertEquals(LocalDate.parse("2026-06-09"), periods.single().startDate)
        assertEquals(1, periods.single().days)
        assertEquals(listOf("2026-06-06"), spotting.map { it.date })
    }

    @Test
    fun `spotting entry ignores period start and keeps symptoms`() {
        val entry = CycleLogLogic.buildEntry(
            date = LocalDate.parse("2026-06-06"),
            recordType = CycleRecordType.SPOTTING,
            flow = 4,
            isStart = true,
            symptoms = setOf("腰酸", "偏头痛"),
        )

        assertEquals(1, entry.flow)
        assertFalse(entry.isPeriodStart)
        assertEquals(CycleLogLogic.NOTE_INTERMENSTRUAL_BLEEDING, entry.notes)
        assertEquals("偏头痛,腰酸", entry.symptomsCsv)
    }

    @Test
    fun `symptom options include custom history after first entry`() {
        val entries = listOf(
            CycleEntry(
                date = "2026-06-09",
                flow = 4,
                isPeriodStart = true,
                symptomsCsv = "腹痛,偏头痛",
            )
        )

        val options = CycleLogLogic.symptomOptions(entries)

        assertTrue("偏头痛" in options)
        assertTrue("腹痛" in options)
    }

    @Test
    fun `symptom only entries do not count as bleeding or period days`() {
        val entry = CycleLogLogic.buildEntry(
            date = LocalDate.parse("2026-06-08"),
            recordType = CycleRecordType.SYMPTOMS,
            flow = 4,
            isStart = true,
            symptoms = setOf("腹胀", "疲惫"),
        )

        assertEquals(0, entry.flow)
        assertFalse(entry.isPeriodStart)
        assertEquals("疲惫,腹胀", entry.symptomsCsv)
        assertTrue(CycleLogLogic.groupIntoPeriods(listOf(entry)).isEmpty())
        assertTrue(CycleLogLogic.spottingEntries(listOf(entry)).isEmpty())
        assertEquals(listOf("2026-06-08"), CycleLogLogic.symptomOnlyEntries(listOf(entry)).map { it.date })
    }

    @Test
    fun `symptom only entries with blank csv are excluded`() {
        // After clearing symptoms from a symptom-only entry, it should not appear
        // in any display list.
        val entry = CycleEntry(
            date = "2026-06-08",
            flow = 0,
            isPeriodStart = false,
            symptomsCsv = null,
            source = "manual",
        )

        assertTrue(CycleLogLogic.groupIntoPeriods(listOf(entry)).isEmpty())
        assertTrue(CycleLogLogic.spottingEntries(listOf(entry)).isEmpty())
        assertTrue(CycleLogLogic.symptomOnlyEntries(listOf(entry)).isEmpty())
    }

    @Test
    fun `menstrual day countdown does not show overdue text`() {
        val prediction = CyclePrediction(
            nextPeriodStart = LocalDate.parse("2026-06-02"),
            confidenceIntervalStart = LocalDate.parse("2026-05-28"),
            confidenceIntervalEnd = LocalDate.parse("2026-06-07"),
            confidenceIntervalDays = 5,
            posteriorMeanCycleLength = 36.1,
            posteriorStdCycleLength = 2.6,
            predictiveStd = 4.0,
            fertileWindowStart = LocalDate.parse("2026-05-14"),
            fertileWindowEnd = LocalDate.parse("2026-05-20"),
            ovulationEstimate = LocalDate.parse("2026-05-19"),
            daysUntilNextPeriod = -7,
            cycleDay = 44,
            averagePeriodDays = 5,
            totalCyclesRecorded = 1,
            confidence = PredictionConfidence.LOW,
            observedCycleLengths = emptyList(),
        )

        val text = CyclePredictionDisplay.countdown(
            prediction = prediction,
            todayStatus = DayStatus.MENSTRUAL,
            todayPeriodDay = 1,
        )

        assertEquals("1", text.value)
        assertEquals("天经期中", text.unit)
        assertFalse(text.unit.contains("推迟"))
    }
}

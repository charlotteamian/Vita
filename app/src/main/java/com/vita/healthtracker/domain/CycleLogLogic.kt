package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.CycleEntry
import java.time.LocalDate

data class CyclePeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: Int,
    val avgFlow: Int,
    val entries: List<CycleEntry>,
)

enum class CycleRecordType {
    PERIOD,
    SPOTTING,
}

data class CycleCountdownText(
    val value: String,
    val unit: String,
)

object CycleLogLogic {
    const val NOTE_INTERMENSTRUAL_BLEEDING = "经间出血"

    private val defaultSymptoms = listOf(
        "腹痛",
        "腰酸",
        "头痛",
        "乳房胀痛",
        "疲惫",
        "腹胀",
        "情绪波动",
        "痘痘",
    )

    fun buildEntry(
        date: LocalDate,
        recordType: CycleRecordType,
        flow: Int,
        isStart: Boolean,
        symptoms: Set<String>,
    ): CycleEntry {
        val symptomsCsv = encodeSymptoms(symptoms)
        return when (recordType) {
            CycleRecordType.PERIOD -> CycleEntry(
                date = date.toString(),
                flow = flow.coerceIn(1, 4),
                isPeriodStart = isStart,
                symptomsCsv = symptomsCsv,
            )

            CycleRecordType.SPOTTING -> CycleEntry(
                date = date.toString(),
                flow = 1,
                isPeriodStart = false,
                notes = NOTE_INTERMENSTRUAL_BLEEDING,
                symptomsCsv = symptomsCsv,
            )
        }
    }

    fun groupIntoPeriods(entries: List<CycleEntry>): List<CyclePeriod> {
        if (entries.isEmpty()) return emptyList()
        val sorted = entries
            .asSequence()
            .filter { e -> e.flow > 0 && !hasIntermenstrualBleedingNote(e) }
            .mapNotNull { e -> runCatching { LocalDate.parse(e.date) to e }.getOrNull() }
            .sortedBy { it.first }
            .toList()

        if (sorted.isEmpty()) return emptyList()

        val periods = mutableListOf<CyclePeriod>()
        var groupStart = sorted.first().first
        var groupEntries = mutableListOf(sorted.first().second)

        for (i in 1 until sorted.size) {
            val (date, entry) = sorted[i]
            val prevDate = sorted[i - 1].first
            if (date.toEpochDay() - prevDate.toEpochDay() <= 1) {
                groupEntries.add(entry)
            } else {
                periods.add(buildPeriod(groupStart, groupEntries))
                groupStart = date
                groupEntries = mutableListOf(entry)
            }
        }
        periods.add(buildPeriod(groupStart, groupEntries))
        return periods.sortedByDescending { it.startDate }
    }

    fun spottingEntries(entries: List<CycleEntry>): List<CycleEntry> =
        entries.filter { it.flow > 0 && hasIntermenstrualBleedingNote(it) }
            .sortedByDescending { it.date }

    fun symptomOptions(entries: List<CycleEntry>): List<String> {
        val fromHistory = entries.flatMap { decodeSymptoms(it.symptomsCsv) }
        return (defaultSymptoms + fromHistory)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun decodeSymptoms(symptomsCsv: String?): List<String> =
        symptomsCsv
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    fun encodeSymptoms(symptoms: Set<String>): String? =
        symptoms
            .map { it.trim().replace(",", " ") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .joinToString(",")
            .ifBlank { null }

    fun isIntermenstrualBleeding(entry: CycleEntry): Boolean = hasIntermenstrualBleedingNote(entry)

    private fun hasIntermenstrualBleedingNote(entry: CycleEntry): Boolean =
        entry.notes == NOTE_INTERMENSTRUAL_BLEEDING

    private fun buildPeriod(startDate: LocalDate, entries: List<CycleEntry>): CyclePeriod {
        val endDate = runCatching { LocalDate.parse(entries.last().date) }.getOrDefault(startDate)
        return CyclePeriod(
            startDate = startDate,
            endDate = endDate,
            days = entries.size,
            avgFlow = if (entries.isNotEmpty()) entries.map { it.flow }.average().toInt() else 0,
            entries = entries,
        )
    }
}

object CyclePredictionDisplay {
    fun countdown(
        prediction: CyclePrediction,
        todayStatus: DayStatus,
        todayPeriodDay: Int?,
    ): CycleCountdownText {
        if (todayStatus == DayStatus.MENSTRUAL && todayPeriodDay != null) {
            return CycleCountdownText(
                value = todayPeriodDay.toString(),
                unit = "天经期中",
            )
        }
        return when {
            prediction.daysUntilNextPeriod < 0 -> CycleCountdownText(
                value = (-prediction.daysUntilNextPeriod).toString(),
                unit = "天已推迟",
            )

            prediction.daysUntilNextPeriod == 0L -> CycleCountdownText(
                value = "今",
                unit = "天",
            )

            else -> CycleCountdownText(
                value = prediction.daysUntilNextPeriod.toString(),
                unit = "天后",
            )
        }
    }
}

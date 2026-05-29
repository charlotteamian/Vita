package com.vita.healthtracker.data.repository

import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.vita.healthtracker.data.healthconnect.HealthConnectManager
import com.vita.healthtracker.data.local.dao.CycleDao
import com.vita.healthtracker.data.local.entity.CycleEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow

class CycleRepository(
    private val dao: CycleDao,
    private val healthConnect: HealthConnectManager,
) {
    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun observeAll(): Flow<List<CycleEntry>> = dao.allDescFlow()

    suspend fun getAll(): List<CycleEntry> = dao.all()

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<CycleEntry>> =
        dao.rangeFlow(from.format(fmt), to.format(fmt))

    suspend fun upsert(entry: CycleEntry) {
        dao.upsert(entry)
        // 同步写回 Health Connect (除了已经从 HC 拉来的)
        if (entry.source == "manual" && entry.flow > 0) {
            val day = LocalDate.parse(entry.date)
            val zone = ZoneId.systemDefault()
            val start = day.atStartOfDay(zone).toInstant()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
            val flow = when (entry.flow) {
                1, 2 -> MenstruationFlowRecord.FLOW_LIGHT
                3 -> MenstruationFlowRecord.FLOW_MEDIUM
                4 -> MenstruationFlowRecord.FLOW_HEAVY
                else -> MenstruationFlowRecord.FLOW_LIGHT
            }
            healthConnect.writeMenstruationFlow(
                listOf(
                    MenstruationFlowRecord(
                        time = start,
                        zoneOffset = ZoneOffset.from(start.atZone(zone)),
                        flow = flow,
                        metadata = Metadata(),
                    )
                )
            )
        }
    }

    suspend fun delete(date: String) = dao.deleteForDate(date)

    /**
     * 外部导入 (Apple / Garmin / HC) 只补充周期数据, 不覆盖用户在 Vita 里手动改过的日期。
     * 这和运动明细里的自定义分类/备注一样, 用户整理过的数据应当稳定保留。
     */
    suspend fun upsertExternalPreservingManual(entries: List<CycleEntry>) {
        if (entries.isEmpty()) return
        val existing = dao.all().associateBy { it.date }
        val merged = entries.mapNotNull { incoming ->
            val old = existing[incoming.date]
            when {
                old?.source == "manual" -> null
                old == null -> incoming
                else -> incoming.copy(
                    notes = old.notes ?: incoming.notes,
                    symptomsCsv = old.symptomsCsv ?: incoming.symptomsCsv,
                )
            }
        }
        if (merged.isNotEmpty()) dao.upsertAll(merged)
    }

    /** 把 Health Connect 月经数据拉进本地. */
    suspend fun syncFromHealthConnect(zone: ZoneId = ZoneId.systemDefault()) {
        if (healthConnect.availability != HealthConnectManager.Availability.Installed) return
        val now = Instant.now()
        val from = now.minusSeconds(365L * 24 * 3600)

        val flowRecords = healthConnect.readMenstruationFlow(from, now)
        val periodRecords = healthConnect.readMenstruationPeriods(from, now)

        val byDate = mutableMapOf<String, CycleEntry>()
        flowRecords.forEach { r ->
            val d = r.time.atZone(zone).toLocalDate().format(fmt)
            val severity = when (r.flow) {
                MenstruationFlowRecord.FLOW_HEAVY -> 4
                MenstruationFlowRecord.FLOW_MEDIUM -> 3
                MenstruationFlowRecord.FLOW_LIGHT -> 2
                else -> 1
            }
            byDate[d] = CycleEntry(
                date = d,
                flow = severity,
                isPeriodStart = false,
                source = "health_connect",
            )
        }
        periodRecords.forEach { p ->
            val start = p.startTime.atZone(zone).toLocalDate().format(fmt)
            byDate[start] = (byDate[start] ?: CycleEntry(start, 2, false)).copy(isPeriodStart = true)
        }
        if (byDate.isNotEmpty()) dao.upsertAll(byDate.values.toList())
    }

    /** 基于 [isPeriodStart=true] 的记录估算平均周期长度. */
    suspend fun averageCycleLength(): Int? {
        val starts = dao.periodStarts().mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .sorted()
        if (starts.size < 2) return null
        val gaps = starts.zipWithNext { a, b -> b.toEpochDay() - a.toEpochDay() }
        return gaps.average().toInt()
    }

    /** 推测下一次月经开始日. */
    suspend fun predictNextPeriodStart(): LocalDate? {
        val starts = dao.periodStarts().mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .sorted()
        val last = starts.lastOrNull() ?: return null
        val avg = averageCycleLength() ?: 28
        return last.plusDays(avg.toLong())
    }
}

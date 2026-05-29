package com.vita.healthtracker.ui.screens.garmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.local.entity.BodyBatterySample
import com.vita.healthtracker.data.local.entity.CycleEntry
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.GarminRawRecord
import com.vita.healthtracker.data.local.entity.HeartRateSample
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.repository.CycleRepository
import com.vita.healthtracker.data.repository.HealthRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class GarminDataDetailState(
    val date: LocalDate = LocalDate.now(),
    val records: List<GarminRawRecord> = emptyList(),
    val daily: DailyHealthSnapshot? = null,
    val sleeps: List<SleepSession> = emptyList(),
    val exercises: List<ExerciseSession> = emptyList(),
    val heartRates: List<HeartRateSample> = emptyList(),
    val bodyBattery: List<BodyBatterySample> = emptyList(),
    val cycles: List<CycleEntry> = emptyList(),
)

class GarminDataDetailViewModel(
    private val healthRepository: HealthRepository,
    private val cycleRepository: CycleRepository,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = selectedDate
        .flatMapLatest { date ->
            val zone = ZoneId.systemDefault()
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            val sleepStart = date.minusDays(1).atStartOfDay(zone).toInstant()
            val structured = combine(
                healthRepository.dailyRange(date, date),
                healthRepository.sleepRange(sleepStart, dayEnd),
                healthRepository.exerciseRange(dayStart, dayEnd),
                healthRepository.heartRateRange(dayStart, dayEnd),
                healthRepository.bodyBatteryRange(dayStart, dayEnd),
            ) { daily, sleeps, exercises, heartRates, bodyBattery ->
                StructuredDayData(
                    daily = daily.firstOrNull(),
                    sleeps = sleeps.filter { sleepDisplayDate(it, zone) == date },
                    exercises = exercises,
                    heartRates = heartRates,
                    bodyBattery = bodyBattery,
                )
            }
            combine(
                structured,
                healthRepository.garminRawForDate(date),
                cycleRepository.observeRange(date, date),
            ) { dayData, records, cycles ->
                GarminDataDetailState(
                    date = date,
                    records = records,
                    daily = dayData.daily,
                    sleeps = dayData.sleeps,
                    exercises = dayData.exercises,
                    heartRates = dayData.heartRates,
                    bodyBattery = dayData.bodyBattery,
                    cycles = cycles,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GarminDataDetailState(),
        )

    fun load(date: LocalDate) {
        selectedDate.value = date
    }

    private data class StructuredDayData(
        val daily: DailyHealthSnapshot?,
        val sleeps: List<SleepSession>,
        val exercises: List<ExerciseSession>,
        val heartRates: List<HeartRateSample>,
        val bodyBattery: List<BodyBatterySample>,
    )
}

private fun sleepDisplayDate(sleep: SleepSession, zone: ZoneId): LocalDate? =
    runCatching {
        val end = Instant.ofEpochMilli(sleep.endEpochMs).atZone(zone).toLocalDate()
        val start = Instant.ofEpochMilli(sleep.startEpochMs).atZone(zone).toLocalDate()
        if (sleep.endEpochMs > sleep.startEpochMs) end else start
    }.getOrNull()

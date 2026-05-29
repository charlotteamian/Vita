package com.vita.healthtracker.ui.screens.today

import androidx.lifecycle.ViewModel
import com.vita.healthtracker.data.local.entity.BodyBatterySample
import com.vita.healthtracker.data.repository.HealthRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

data class BodyBatteryDetailUiState(
    val date: LocalDate = LocalDate.now(),
    val samples: List<BodyBatterySample> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class BodyBatteryDetailViewModel(
    private val repo: HealthRepository,
) : ViewModel() {
    private val _date = MutableStateFlow(LocalDate.now())

    val state: StateFlow<BodyBatteryDetailUiState> = _date.flatMapLatest { date ->
        val zone = ZoneId.systemDefault()
        repo.bodyBatteryRange(
            from = date.atStartOfDay(zone).toInstant(),
            to = date.plusDays(1).atStartOfDay(zone).toInstant(),
        ).map { BodyBatteryDetailUiState(date, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyBatteryDetailUiState())

    fun setDate(date: LocalDate) {
        _date.value = date
    }

    fun previousDay() {
        _date.value = _date.value.minusDays(1)
    }

    fun nextDay() {
        val next = _date.value.plusDays(1)
        if (!next.isAfter(LocalDate.now())) _date.value = next
    }

    fun jumpToToday() {
        _date.value = LocalDate.now()
    }
}

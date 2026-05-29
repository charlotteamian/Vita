package com.vita.healthtracker.ui.screens.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.local.entity.SleepSession
import com.vita.healthtracker.data.repository.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SleepDetailUiState(
    val sleeps: List<SleepSession> = emptyList(),
)

class SleepDetailViewModel(
    private val healthRepo: HealthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SleepDetailUiState())
    val state: StateFlow<SleepDetailUiState> = _state

    init {
        viewModelScope.launch {
            val from = LocalDate.of(2010, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val to = Instant.now()
            healthRepo.sleepRange(from, to).collect { sleeps ->
                _state.value = SleepDetailUiState(sleeps.sortedByDescending { it.startEpochMs })
            }
        }
    }
}

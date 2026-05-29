package com.vita.healthtracker.ui.screens.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vita.healthtracker.data.local.dao.ExerciseDao
import com.vita.healthtracker.data.local.dao.HeartRateDao
import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.HeartRateSample
import com.vita.healthtracker.data.repository.HealthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class ExerciseDetailUiState(
    val exercise: ExerciseSession? = null,
    val heartRates: List<HeartRateSample> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModel(
    private val exerciseDao: ExerciseDao,
    private val heartRateDao: HeartRateDao,
    private val healthRepository: HealthRepository,
) : ViewModel() {

    private val _exerciseId = MutableStateFlow<String?>(null)

    val state: StateFlow<ExerciseDetailUiState> = _exerciseId
        .filterNotNull()
        .flatMapLatest { id ->
            val exerciseFlow = exerciseDao.getByIdFlow(id)
            exerciseFlow.flatMapLatest { exercise ->
                if (exercise == null) {
                    MutableStateFlow(ExerciseDetailUiState())
                } else {
                    val hrFlow = heartRateDao.rangeFlow(exercise.startEpochMs, exercise.endEpochMs)
                    combine(MutableStateFlow(exercise), hrFlow) { ex, hr ->
                        ExerciseDetailUiState(exercise = ex, heartRates = hr)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun load(id: String) {
        _exerciseId.value = id
    }

    fun updateUserFields(title: String?, category: String?, note: String?) {
        val id = _exerciseId.value ?: return
        viewModelScope.launch {
            healthRepository.updateExerciseUserFields(id, title, category, note)
        }
    }

    fun deleteExercise() {
        val id = _exerciseId.value ?: return
        viewModelScope.launch {
            healthRepository.deleteExercise(id)
        }
    }
}

package com.vita.healthtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vita.healthtracker.VitaApplication
import com.vita.healthtracker.data.AppContainer
import com.vita.healthtracker.ui.screens.account.AccountAuthViewModel
import com.vita.healthtracker.ui.screens.garmin.GarminDataDetailViewModel
import com.vita.healthtracker.ui.screens.life.LifeViewModel
import com.vita.healthtracker.ui.screens.settings.SettingsViewModel
import com.vita.healthtracker.ui.screens.today.BodyBatteryDetailViewModel
import com.vita.healthtracker.ui.screens.stats.StatsViewModel
import com.vita.healthtracker.ui.screens.today.TodayViewModel

/** 极简手摇 ViewModel 工厂,避免引入 Hilt. */
class VitaViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when {
        modelClass.isAssignableFrom(TodayViewModel::class.java) ->
            TodayViewModel(
                container.healthRepository,
                container.settingsPreferences,
                container.syncCoordinator,
                container.habitRepository,
                container.moodRepository,
                container.cycleRepository,
            ) as T
        modelClass.isAssignableFrom(BodyBatteryDetailViewModel::class.java) ->
            BodyBatteryDetailViewModel(container.healthRepository) as T
        modelClass.isAssignableFrom(GarminDataDetailViewModel::class.java) ->
            GarminDataDetailViewModel(container.healthRepository, container.cycleRepository) as T
        modelClass.isAssignableFrom(StatsViewModel::class.java) ->
            StatsViewModel(
                container.healthRepository,
                container.settingsPreferences,
                container.habitRepository,
                container.moodRepository,
                container.cycleRepository,
                container.weatherRepository,
            ) as T
        modelClass.isAssignableFrom(LifeViewModel::class.java) ->
            LifeViewModel(
                container.cycleRepository,
                container.healthRepository,
                container.habitRepository,
                container.moodRepository,
                container.weatherRepository,
                container.settingsPreferences,
            ) as T
        modelClass.isAssignableFrom(com.vita.healthtracker.ui.screens.life.SleepDetailViewModel::class.java) ->
            com.vita.healthtracker.ui.screens.life.SleepDetailViewModel(container.healthRepository) as T
        modelClass.isAssignableFrom(com.vita.healthtracker.ui.screens.exercise.ExerciseDetailViewModel::class.java) ->
            com.vita.healthtracker.ui.screens.exercise.ExerciseDetailViewModel(
                container.database.exerciseDao(),
                container.database.heartRateDao(),
                container.healthRepository,
            ) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(
                container.healthConnect,
                container.healthRepository,
                container.cycleRepository,
                container.backupManager,
                container.settingsPreferences,
                container.stepSensorManager,
            ) as T
        modelClass.isAssignableFrom(AccountAuthViewModel::class.java) ->
            AccountAuthViewModel(
                container.garminAuthClient,
                container.syncCoordinator,
                container.appleHealthImporter,
            ) as T
        else -> error("Unknown ViewModel ${modelClass.name}")
    }
}

@Composable
inline fun <reified VM : ViewModel> vitaViewModel(): VM {
    val context = LocalContext.current
    val app = context.applicationContext as VitaApplication
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }
    return viewModel(viewModelStoreOwner = owner, factory = VitaViewModelFactory(app.container))
}

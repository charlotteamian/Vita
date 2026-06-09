package com.vita.healthtracker.data

import android.content.Context
import androidx.room.Room
import com.vita.healthtracker.data.backup.BackupManager
import com.vita.healthtracker.data.healthconnect.HealthConnectManager
import com.vita.healthtracker.data.local.MIGRATION_1_3
import com.vita.healthtracker.data.local.MIGRATION_2_3
import com.vita.healthtracker.data.local.MIGRATION_3_5
import com.vita.healthtracker.data.local.MIGRATION_4_5
import com.vita.healthtracker.data.local.MIGRATION_5_6
import com.vita.healthtracker.data.local.MIGRATION_6_7
import com.vita.healthtracker.data.local.MIGRATION_7_8
import com.vita.healthtracker.data.local.MIGRATION_8_9
import com.vita.healthtracker.data.local.MIGRATION_9_10
import com.vita.healthtracker.data.local.MIGRATION_10_11
import com.vita.healthtracker.data.local.VitaDatabase
import com.vita.healthtracker.data.prefs.SettingsPreferences
import com.vita.healthtracker.data.repository.CycleRepository
import com.vita.healthtracker.data.repository.HabitRepository
import com.vita.healthtracker.data.repository.HealthRepository
import com.vita.healthtracker.data.repository.MoodRepository
import com.vita.healthtracker.data.repository.WeatherRepository
import com.vita.healthtracker.data.sensor.StepSensorManager
import com.vita.healthtracker.data.apple.AppleHealthImporter
import com.vita.healthtracker.data.garmin.GarminAuthClient
import com.vita.healthtracker.data.garmin.GarminDataFetcher
import com.vita.healthtracker.data.garmin.GarminSyncManager
import com.vita.healthtracker.data.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val database: VitaDatabase
    val healthConnect: HealthConnectManager
    val healthRepository: HealthRepository
    val cycleRepository: CycleRepository
    val habitRepository: HabitRepository
    val moodRepository: MoodRepository
    val weatherRepository: WeatherRepository
    val backupManager: BackupManager
    val stepSensorManager: StepSensorManager
    val appleHealthImporter: AppleHealthImporter
    val settingsPreferences: SettingsPreferences
    val garminAuthClient: GarminAuthClient
    val garminDataFetcher: GarminDataFetcher
    val garminSyncManager: GarminSyncManager
    val syncCoordinator: SyncCoordinator
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    /** 应用作用域: 同步协调器跑在这上面, 不随任何页面/ViewModel 销毁而取消 (修 #7)。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val database: VitaDatabase by lazy {
        Room.databaseBuilder(appContext, VitaDatabase::class.java, "vita.db")
            .addMigrations(
                MIGRATION_1_3,
                MIGRATION_2_3,
                MIGRATION_3_5,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
            )
            .build()
    }

    override val healthConnect: HealthConnectManager by lazy {
        HealthConnectManager(appContext)
    }

    override val healthRepository: HealthRepository by lazy {
        HealthRepository(
            healthConnect = healthConnect,
            dailyDao = database.dailyHealthDao(),
            sleepDao = database.sleepDao(),
            heartRateDao = database.heartRateDao(),
            exerciseDao = database.exerciseDao(),
            bodyBatteryDao = database.bodyBatteryDao(),
            garminRawDao = database.garminRawDao(),
            syncDao = database.syncMarkerDao(),
        )
    }

    override val cycleRepository: CycleRepository by lazy {
        CycleRepository(database.cycleDao(), healthConnect)
    }

    override val habitRepository: HabitRepository by lazy {
        HabitRepository(database.habitDao())
    }

    override val moodRepository: MoodRepository by lazy {
        MoodRepository(database.moodDao())
    }

    override val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(database.weatherDao())
    }

    override val backupManager: BackupManager by lazy {
        BackupManager(appContext, database)
    }

    override val stepSensorManager: StepSensorManager by lazy {
        StepSensorManager(appContext, database.dailyHealthDao())
    }

    override val appleHealthImporter: AppleHealthImporter by lazy {
        AppleHealthImporter(appContext, healthRepository, cycleRepository, appScope)
    }

    override val settingsPreferences: SettingsPreferences by lazy {
        SettingsPreferences(appContext)
    }

    override val garminAuthClient: GarminAuthClient by lazy {
        GarminAuthClient(appContext)
    }

    override val garminDataFetcher: GarminDataFetcher by lazy {
        GarminDataFetcher(garminAuthClient)
    }

    override val garminSyncManager: GarminSyncManager by lazy {
        GarminSyncManager(garminAuthClient, garminDataFetcher, healthRepository, cycleRepository)
    }

    override val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(
            context = appContext,
            scope = appScope,
            healthRepo = healthRepository,
            cycleRepo = cycleRepository,
            garminAuthClient = garminAuthClient,
            garminSyncManager = garminSyncManager,
        )
    }
}

package com.vita.healthtracker.data.healthconnect

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId

/** 包一层 Health Connect SDK,方便在 ViewModel/Repository 里调用. */
class HealthConnectManager(private val context: Context) {

    sealed interface Availability {
        data object Installed : Availability
        data object NotInstalled : Availability       // 设备没装 Health Connect (Android 13-)
        data object ProviderUpdateRequired : Availability
        data object NotSupported : Availability        // API < 28 或硬件不支持
    }

    val availability: Availability
        get() = when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> Availability.Installed
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.ProviderUpdateRequired
            HealthConnectClient.SDK_UNAVAILABLE -> Availability.NotInstalled
            else -> Availability.NotSupported
        }

    /** 拿 client 之前调用方需先确认 [availability] == Installed,否则会抛异常. */
    val client: HealthConnectClient
        get() = HealthConnectClient.getOrCreate(context, PROVIDER_PACKAGE)

    /** 跳转去 Play 商店安装 Health Connect (Android 13-) */
    fun installHealthConnectIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun hasAllReadPermissions(): Boolean {
        if (availability != Availability.Installed) return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(READ_PERMISSIONS)
    }

    /**
     * 是否至少授权了一项读权限。
     * 同步时用它做闸门: 只要授权了任意一类(比如只给了睡眠), 就放行同步,
     * 每个数据类型各自读取(无权限的会被 runCatching 吞掉返回空), 不再因为漏授一项就整体不同步。
     */
    suspend fun hasAnyReadPermission(): Boolean {
        if (availability != Availability.Installed) return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.any { it in READ_PERMISSIONS }
    }

    suspend fun grantedPermissions(): Set<String> =
        if (availability == Availability.Installed) client.permissionController.getGrantedPermissions() else emptySet()

    // ---------- 聚合读取 ----------

    /** 拉取某一天的聚合 (steps/distance/calories/floors). 返回 null 表示无权限或无数据. */
    suspend fun aggregateForDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): AggregationResult? {
        if (availability != Availability.Installed) return null
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                        FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
                    ),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            )
        }.getOrNull()
    }

    /** 按天聚合一段范围,返回 (date -> aggregation). */
    suspend fun aggregateByDay(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DailyAggregate> {
        if (availability != Availability.Installed) return emptyList()
        val start = from.atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant()
        return runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                        FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
                    ),
                    timeRangeFilter = TimeRangeFilter.between(
                        LocalDateTime.ofInstant(start, zone),
                        LocalDateTime.ofInstant(end, zone),
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                )
            ).map { bucket ->
                DailyAggregate(
                    date = bucket.startTime.toLocalDate(),
                    steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L,
                    distanceMeters = bucket.result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
                    activeCalories = bucket.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0,
                    totalCalories = bucket.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0,
                    floorsClimbed = bucket.result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0.0,
                )
            }
        }.getOrDefault(emptyList())
    }

    // ---------- 原始记录读取 ----------

    suspend fun readSleepSessions(from: Instant, to: Instant): List<SleepSessionRecord> {
        if (availability != Availability.Installed) return emptyList()
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            ).records
        }.getOrDefault(emptyList())
    }

    suspend fun readHeartRate(from: Instant, to: Instant): List<HeartRateRecord> {
        if (availability != Availability.Installed) return emptyList()
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            ).records
        }.getOrDefault(emptyList())
    }

    suspend fun readRestingHeartRate(from: Instant, to: Instant): List<RestingHeartRateRecord> {
        if (availability != Availability.Installed) return emptyList()
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            ).records
        }.getOrDefault(emptyList())
    }

    suspend fun readExerciseSessions(from: Instant, to: Instant): List<ExerciseSessionRecord> {
        if (availability != Availability.Installed) return emptyList()
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            ).records
        }.getOrDefault(emptyList())
    }

    suspend fun readWeight(from: Instant, to: Instant): List<WeightRecord> {
        if (availability != Availability.Installed) return emptyList()
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            ).records
        }.getOrDefault(emptyList())
    }

    suspend fun readMenstruationPeriods(from: Instant, to: Instant): List<MenstruationPeriodRecord> {
        if (availability != Availability.Installed) return emptyList()
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    MenstruationPeriodRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            ).records
        }.getOrDefault(emptyList())
    }

    suspend fun readMenstruationFlow(from: Instant, to: Instant): List<MenstruationFlowRecord> {
        if (availability != Availability.Installed) return emptyList()
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    MenstruationFlowRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            ).records
        }.getOrDefault(emptyList())
    }

    suspend fun writeMenstruationFlow(records: List<MenstruationFlowRecord>) {
        if (availability != Availability.Installed) return
        runCatching { client.insertRecords(records) }
    }

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val READ_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(FloorsClimbedRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(MenstruationFlowRecord::class),
            HealthPermission.getReadPermission(MenstruationPeriodRecord::class),
        )

        val WRITE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(MenstruationFlowRecord::class),
        )

        val ALL_PERMISSIONS: Set<String> = READ_PERMISSIONS + WRITE_PERMISSIONS
    }
}

data class DailyAggregate(
    val date: LocalDate,
    val steps: Long,
    val distanceMeters: Double,
    val activeCalories: Double,
    val totalCalories: Double,
    val floorsClimbed: Double,
)

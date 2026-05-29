package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyHealthDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: DailyHealthSnapshot)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(snapshots: List<DailyHealthSnapshot>)

    @Query("SELECT * FROM daily_health WHERE date = :date")
    suspend fun forDate(date: String): DailyHealthSnapshot?

    @Query(
        """
        DELETE FROM daily_health
        WHERE source = 'garmin_api'
          AND date BETWEEN :from AND :to
          AND steps = 0
          AND (distanceMeters IS NULL OR distanceMeters = 0)
          AND (activeCalories IS NULL OR activeCalories = 0)
          AND (totalCalories IS NULL OR totalCalories = 0)
          AND (activeMinutes IS NULL OR activeMinutes = 0)
          AND (floorsClimbed IS NULL OR floorsClimbed = 0)
          AND (avgHeartRate IS NULL OR avgHeartRate = 0)
          AND (restingHeartRate IS NULL OR restingHeartRate = 0)
          AND (avgStress IS NULL OR avgStress = 0)
          AND (avgSpo2 IS NULL OR avgSpo2 = 0)
          AND (avgRespiration IS NULL OR avgRespiration = 0)
          AND (hrv IS NULL OR hrv = 0)
          AND (weightKg IS NULL OR weightKg = 0)
        """
    )
    suspend fun deleteEmptyGarminRows(from: String, to: String): Int

    @Query(
        """
        DELETE FROM daily_health
        WHERE date BETWEEN :from AND :to
          AND steps = 0
          AND (distanceMeters IS NULL OR distanceMeters = 0)
          AND (activeCalories IS NULL OR activeCalories = 0)
          AND (totalCalories IS NULL OR totalCalories = 0)
          AND (activeMinutes IS NULL OR activeMinutes = 0)
          AND (floorsClimbed IS NULL OR floorsClimbed = 0)
          AND (avgHeartRate IS NULL OR avgHeartRate = 0)
          AND (restingHeartRate IS NULL OR restingHeartRate = 0)
          AND (avgStress IS NULL OR avgStress = 0)
          AND (avgSpo2 IS NULL OR avgSpo2 = 0)
          AND (avgRespiration IS NULL OR avgRespiration = 0)
          AND (hrv IS NULL OR hrv = 0)
          AND (weightKg IS NULL OR weightKg = 0)
        """
    )
    suspend fun deleteEmptyRows(from: String, to: String): Int

    /**
     * 清理「BMR 幽灵数据」: 没戴表的日子佳明仍返回一个近乎恒定的 totalKilocalories(基础代谢),
     * 之前会被存成只有「总消耗」的一天, 导致整列卡路里都一样, 还把月/年图表拉回到很多年前。
     * 这里删掉「除 totalCalories 外没有任何真实活动/生理信号」的佳明行。
     */
    @Query(
        """
        DELETE FROM daily_health
        WHERE source LIKE '%garmin_api%'
          AND date BETWEEN :from AND :to
          AND steps = 0
          AND (distanceMeters IS NULL OR distanceMeters = 0)
          AND (activeCalories IS NULL OR activeCalories = 0)
          AND (activeMinutes IS NULL OR activeMinutes = 0)
          AND (floorsClimbed IS NULL OR floorsClimbed = 0)
          AND (avgHeartRate IS NULL OR avgHeartRate = 0)
          AND (restingHeartRate IS NULL OR restingHeartRate = 0)
          AND (avgStress IS NULL OR avgStress = 0)
          AND (avgSpo2 IS NULL OR avgSpo2 = 0)
          AND (avgRespiration IS NULL OR avgRespiration = 0)
          AND (hrv IS NULL OR hrv = 0)
          AND (weightKg IS NULL OR weightKg = 0)
        """
    )
    suspend fun deleteBmrOnlyGarminRows(from: String, to: String): Int

    /**
     * 同样清理非 Garmin 来源的 totalCalories-only 行。Health Connect 有时也会只给出基础代谢式的
     * 总消耗, 没有步数/活动/心率等信号时不应被当成真实健康数据。
     */
    @Query(
        """
        DELETE FROM daily_health
        WHERE date BETWEEN :from AND :to
          AND steps = 0
          AND (distanceMeters IS NULL OR distanceMeters = 0)
          AND (activeCalories IS NULL OR activeCalories = 0)
          AND (activeMinutes IS NULL OR activeMinutes = 0)
          AND (floorsClimbed IS NULL OR floorsClimbed = 0)
          AND (avgHeartRate IS NULL OR avgHeartRate = 0)
          AND (restingHeartRate IS NULL OR restingHeartRate = 0)
          AND (avgStress IS NULL OR avgStress = 0)
          AND (avgSpo2 IS NULL OR avgSpo2 = 0)
          AND (avgRespiration IS NULL OR avgRespiration = 0)
          AND (hrv IS NULL OR hrv = 0)
          AND (weightKg IS NULL OR weightKg = 0)
        """
    )
    suspend fun deleteBmrOnlyRows(from: String, to: String): Int

    @Query("SELECT * FROM daily_health WHERE date BETWEEN :from AND :to ORDER BY date")
    fun rangeFlow(from: String, to: String): Flow<List<DailyHealthSnapshot>>

    @Query("SELECT * FROM daily_health ORDER BY date DESC LIMIT 1")
    fun latestFlow(): Flow<DailyHealthSnapshot?>

    @Query("SELECT * FROM daily_health ORDER BY date")
    suspend fun all(): List<DailyHealthSnapshot>
}

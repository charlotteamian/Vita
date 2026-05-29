package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vita.healthtracker.data.local.entity.GarminRawRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface GarminRawDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<GarminRawRecord>)

    @Query(
        """
        SELECT * FROM garmin_raw_record
        WHERE date = :date
        ORDER BY categoryLabel, categoryKey
        """
    )
    fun forDateFlow(date: String): Flow<List<GarminRawRecord>>

    @Query("SELECT * FROM garmin_raw_record ORDER BY date, categoryLabel")
    suspend fun all(): List<GarminRawRecord>

    @Query(
        """
        SELECT COUNT(*) FROM garmin_raw_record
        WHERE date = :date AND domain = :domain
        """
    )
    suspend fun countForDateDomain(date: String, domain: String): Int
}

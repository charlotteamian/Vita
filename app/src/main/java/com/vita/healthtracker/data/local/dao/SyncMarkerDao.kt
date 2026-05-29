package com.vita.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vita.healthtracker.data.local.entity.SyncMarker

@Dao
interface SyncMarkerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(marker: SyncMarker)

    @Query("SELECT * FROM sync_marker WHERE dataType = :type")
    suspend fun get(type: String): SyncMarker?

    @Query("SELECT * FROM sync_marker WHERE dataType = :type")
    fun getFlow(type: String): kotlinx.coroutines.flow.Flow<SyncMarker?>

    @Query("SELECT * FROM sync_marker")
    suspend fun all(): List<SyncMarker>
}

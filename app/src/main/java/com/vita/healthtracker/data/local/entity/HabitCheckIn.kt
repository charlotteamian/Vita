package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "habit_check_in",
    primaryKeys = ["habitId", "date"],
)
data class HabitCheckIn(
    val habitId: String,
    val date: String,
    val status: Int,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    companion object {
        const val StatusMissed = -1
        const val StatusDone = 1
    }
}

package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "habit_definition")
data class HabitDefinition(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
)

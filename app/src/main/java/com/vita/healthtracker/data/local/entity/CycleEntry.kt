package com.vita.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 月经记录,主键即日期. flow: 0=无, 1=点滴, 2=轻, 3=中, 4=重 */
@Serializable
@Entity(tableName = "cycle_entry")
data class CycleEntry(
    @PrimaryKey val date: String,           // yyyy-MM-dd
    val flow: Int,
    val isPeriodStart: Boolean,             // 周期起始日,用于推算下一次
    val notes: String? = null,
    val symptomsCsv: String? = null,        // "cramp,headache,..."
    val source: String = "manual",          // manual / health_connect
)

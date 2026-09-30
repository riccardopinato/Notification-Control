package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "critical_patterns",
    indices = [Index("type"), Index(value = ["type", "value"], unique = true)]
)
data class CriticalPatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val value: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

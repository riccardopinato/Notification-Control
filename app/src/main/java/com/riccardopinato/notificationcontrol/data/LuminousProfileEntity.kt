package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "luminous_profiles",
    indices = [
        Index("enabled"),
        Index("packageName"),
        Index("priority")
    ]
)
data class LuminousProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val packageName: String? = null,
    val senderQuery: String? = null,
    val colorHex: String = "#6750A4",
    val flashEnabled: Boolean = true,
    val overlayEnabled: Boolean = true,
    val strobeCycles: Int = 5,
    val strobeSpeedMs: Long = 150L,
    val circleThickness: Float = 24f,
    val circleGlow: Float = 30f,
    val pulseSpeedMs: Long = 1_000L,
    val displayDurationMs: Long = 15_000L,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

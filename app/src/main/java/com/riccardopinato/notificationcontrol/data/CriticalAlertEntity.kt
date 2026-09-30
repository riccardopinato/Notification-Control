package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "critical_alerts",
    indices = [
        Index(value = ["eventKey"], unique = true),
        Index("status"),
        Index("nextAt")
    ]
)
data class CriticalAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventKey: String,
    val sourcePackage: String,
    val sourceLabel: String,
    val title: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String = "ACTIVE",
    val escalationStep: Int = 0,
    val nextAt: Long
)

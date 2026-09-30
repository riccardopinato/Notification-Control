package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pickup_codes",
    indices = [
        Index("expiresAt"),
        Index("dismissed"),
        Index(value = ["code", "sourcePackage", "notificationKey"], unique = true)
    ]
)
data class PickupCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val sourcePackage: String,
    val sourceLabel: String,
    val notificationKey: String,
    val contextText: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val dismissed: Boolean = false
)

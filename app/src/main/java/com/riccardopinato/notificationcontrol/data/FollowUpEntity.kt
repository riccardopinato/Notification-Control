package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "follow_ups",
    indices = [Index("status"), Index("dueAt"), Index("notificationKey")]
)
data class FollowUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String?,
    val sourcePackage: String?,
    val sourceLabel: String?,
    val title: String,
    val body: String?,
    val dueAt: Long,
    val status: String = "ACTIVE",
    val repeatMinutes: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

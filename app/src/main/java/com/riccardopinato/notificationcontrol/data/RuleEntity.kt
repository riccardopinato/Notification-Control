package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rules",
    indices = [Index("enabled"), Index("priority"), Index("packageName")]
)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val packageName: String? = null,
    val senderQuery: String? = null,
    val textQuery: String? = null,
    val matchMode: String = "ALL",
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

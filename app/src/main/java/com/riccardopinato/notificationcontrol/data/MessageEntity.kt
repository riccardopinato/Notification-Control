package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(entity = NotificationEntity::class, parentColumns = ["sbnKey"], childColumns = ["notificationKey"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("notificationKey"), Index("timestamp")]
)
data class MessageEntity(
    @PrimaryKey val messageKey: String,
    val notificationKey: String,
    val sender: String?,
    val text: String,
    val timestamp: Long,
    val mimeType: String? = null,
    val dataUri: String? = null
)

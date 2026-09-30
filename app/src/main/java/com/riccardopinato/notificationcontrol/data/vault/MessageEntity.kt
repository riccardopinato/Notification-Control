package com.riccardopinato.notificationcontrol.data.vault

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_messages",
    foreignKeys = [
        ForeignKey(
            entity = NotificationEntity::class,
            parentColumns = ["notificationKey"],
            childColumns = ["notificationKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notificationKey")]
)
data class MessageEntity(
    @PrimaryKey val messageKey: String,
    val notificationKey: String,
    val position: Int,
    val sender: String?,
    val text: String?,
    val timestamp: Long,
    val mimeType: String?,
    val dataUri: String?
)

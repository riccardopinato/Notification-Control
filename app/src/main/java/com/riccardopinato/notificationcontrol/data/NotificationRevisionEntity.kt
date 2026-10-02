package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_revisions",
    foreignKeys = [
        ForeignKey(
            entity = NotificationEntity::class,
            parentColumns = ["sbnKey"],
            childColumns = ["notificationKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notificationKey"), Index("capturedAt"), Index("contentHash")]
)
data class NotificationRevisionEntity(
    @PrimaryKey val revisionKey: String,
    val notificationKey: String,
    val capturedAt: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val conversationTitle: String?,
    val thumbnailPath: String? = null,
    val contentHash: String
)

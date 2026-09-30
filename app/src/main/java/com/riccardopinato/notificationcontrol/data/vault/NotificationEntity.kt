package com.riccardopinato.notificationcontrol.data.vault

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_events")
data class NotificationEntity(
    @PrimaryKey val notificationKey: String,
    val packageName: String,
    val notificationId: Int,
    val tag: String?,
    val groupKey: String?,
    val postTime: Long,
    val firstSeenAt: Long,
    val lastUpdatedAt: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val conversationTitle: String?,
    val category: String?,
    val channelId: String?,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val contentHash: String,
    val removedAt: Long? = null,
    val removalReason: Int? = null,
    val protectedFromCleanup: Boolean = false
)

package com.riccardopinato.notificationcontrol.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notifications", indices = [Index("platformKey"), Index("packageName"), Index("postedAt"), Index("removedAt"), Index("protected")])
data class NotificationEntity(
    @PrimaryKey val sbnKey: String,
    @ColumnInfo(defaultValue = "''") val platformKey: String = sbnKey,
    val packageName: String,
    val appLabel: String,
    val notificationId: Int,
    val tag: String?,
    val groupKey: String?,
    val category: String?,
    val channelId: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val conversationTitle: String?,
    val thumbnailPath: String? = null,
    val postedAt: Long,
    val updatedAt: Long,
    val removedAt: Long? = null,
    val removalReason: Int? = null,
    val isOngoing: Boolean = false,
    val isClearable: Boolean = true,
    val protected: Boolean = false
)

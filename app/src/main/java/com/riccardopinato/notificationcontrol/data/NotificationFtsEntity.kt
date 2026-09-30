package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "notification_fts")
data class NotificationFtsEntity(
    val sbnKey: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val conversationTitle: String?,
    val messagesText: String
)

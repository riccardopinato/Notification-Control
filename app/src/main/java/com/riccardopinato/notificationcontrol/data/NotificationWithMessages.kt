package com.riccardopinato.notificationcontrol.data

import androidx.room.Embedded
import androidx.room.Relation

data class NotificationWithMessages(
    @Embedded val notification: NotificationEntity,
    @Relation(parentColumn = "sbnKey", entityColumn = "notificationKey") val messages: List<MessageEntity>
)

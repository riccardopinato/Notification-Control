package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "message_fts")
data class MessageFtsEntity(
    val messageKey: String,
    val notificationKey: String,
    val sender: String?,
    val text: String
)

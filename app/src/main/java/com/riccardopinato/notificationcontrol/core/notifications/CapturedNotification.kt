package com.riccardopinato.notificationcontrol.core.notifications

data class CapturedNotification(
    val notificationKey: String,
    val packageName: String,
    val notificationId: Int,
    val tag: String?,
    val groupKey: String?,
    val postTime: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val conversationTitle: String?,
    val category: String?,
    val channelId: String?,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val messages: List<CapturedMessage>,
    val contentHash: String
)

data class CapturedMessage(
    val sender: String?,
    val text: String?,
    val timestamp: Long,
    val mimeType: String?,
    val dataUri: String?
)

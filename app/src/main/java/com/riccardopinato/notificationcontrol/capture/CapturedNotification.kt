package com.riccardopinato.notificationcontrol.capture

data class CapturedMessage(
    val sender: String?,
    val text: String,
    val timestamp: Long,
    val mimeType: String? = null,
    val dataUri: String? = null,
    val timestampReliable: Boolean = true
)

data class CapturedNotification(
    val sbnKey: String,
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
    val thumbnailPath: String?,
    val postedAt: Long,
    val capturedAt: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val messages: List<CapturedMessage>
)

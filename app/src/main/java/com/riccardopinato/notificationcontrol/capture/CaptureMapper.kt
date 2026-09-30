package com.riccardopinato.notificationcontrol.capture

import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import java.security.MessageDigest

fun CapturedNotification.toNotificationEntity(existingProtected: Boolean = false, existingThumbnailPath: String? = null): NotificationEntity =
    NotificationEntity(
        sbnKey = sbnKey,
        packageName = packageName,
        appLabel = appLabel,
        notificationId = notificationId,
        tag = tag,
        groupKey = groupKey,
        category = category,
        channelId = channelId,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        conversationTitle = conversationTitle,
        thumbnailPath = thumbnailPath ?: existingThumbnailPath,
        postedAt = postedAt,
        updatedAt = capturedAt,
        removedAt = null,
        removalReason = null,
        isOngoing = isOngoing,
        isClearable = isClearable,
        protected = existingProtected
    )

fun CapturedNotification.toMessageEntities(): List<MessageEntity> = messages.map { message ->
    val fingerprint = listOf(sbnKey, message.timestamp.toString(), message.sender.orEmpty(), message.text)
        .joinToString("\u0000")
    MessageEntity(
        messageKey = sha256(fingerprint),
        notificationKey = sbnKey,
        sender = message.sender,
        text = message.text,
        timestamp = message.timestamp,
        mimeType = message.mimeType,
        dataUri = message.dataUri
    )
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }

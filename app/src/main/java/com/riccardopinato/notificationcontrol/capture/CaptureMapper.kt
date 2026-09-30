package com.riccardopinato.notificationcontrol.capture

import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationFtsEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity
import java.security.MessageDigest

fun CapturedNotification.toNotificationEntity(
    existingProtected: Boolean = false,
    existingThumbnailPath: String? = null
): NotificationEntity =
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

fun CapturedNotification.toRevisionEntity(): NotificationRevisionEntity {
    val contentFingerprint = listOf(
        title.orEmpty(),
        text.orEmpty(),
        bigText.orEmpty(),
        subText.orEmpty(),
        conversationTitle.orEmpty(),
        messages.joinToString("\u0001") {
            listOf(it.timestamp.toString(), it.sender.orEmpty(), it.text, it.mimeType.orEmpty(), it.dataUri.orEmpty())
                .joinToString("\u0000")
        }
    ).joinToString("\u0002")
    val contentHash = sha256(contentFingerprint)
    return NotificationRevisionEntity(
        revisionKey = sha256("$sbnKey\u0000$contentHash"),
        notificationKey = sbnKey,
        capturedAt = capturedAt,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        conversationTitle = conversationTitle,
        contentHash = contentHash
    )
}

fun CapturedNotification.toFtsEntity(): NotificationFtsEntity =
    NotificationFtsEntity(
        sbnKey = sbnKey,
        appLabel = appLabel,
        title = title,
        text = text,
        bigText = bigText,
        conversationTitle = conversationTitle,
        messagesText = messages.joinToString(" ") { it.sender.orEmpty() + " " + it.text }
    )

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }

package com.riccardopinato.notificationcontrol.capture

import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationFtsEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity
import java.security.MessageDigest

fun CapturedNotification.toNotificationEntity(
    eventKey: String = sbnKey,
    existingProtected: Boolean = false,
    existingThumbnailPath: String? = null
): NotificationEntity =
    NotificationEntity(
        sbnKey = eventKey,
        platformKey = sbnKey,
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

fun CapturedNotification.toMessageEntities(
    eventKey: String = sbnKey
): List<MessageEntity> = messages.map { message ->
    val fingerprint = listOf(eventKey, message.timestamp.toString(), message.sender.orEmpty(), message.text)
        .joinToString("\u0000")
    MessageEntity(
        messageKey = sha256(fingerprint),
        notificationKey = eventKey,
        sender = message.sender,
        text = message.text,
        timestamp = message.timestamp,
        mimeType = message.mimeType,
        dataUri = message.dataUri
    )
}

fun CapturedNotification.toRevisionEntity(
    eventKey: String = sbnKey
): NotificationRevisionEntity {
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
        revisionKey = sha256("$eventKey\u0000$contentHash"),
        notificationKey = eventKey,
        capturedAt = capturedAt,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        conversationTitle = conversationTitle,
        contentHash = contentHash
    )
}

fun CapturedNotification.toFtsEntity(
    eventKey: String = sbnKey
): NotificationFtsEntity =
    NotificationFtsEntity(
        sbnKey = eventKey,
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

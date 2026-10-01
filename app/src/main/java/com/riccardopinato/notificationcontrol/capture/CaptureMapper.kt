package com.riccardopinato.notificationcontrol.capture

import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationFtsEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity

fun CapturedNotification.toNotificationEntity(
    vaultKey: String = sbnKey,
    existingProtected: Boolean = false,
    existingThumbnailPath: String? = null
): NotificationEntity =
    NotificationEntity(
        sbnKey = vaultKey,
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
    vaultKey: String = sbnKey
): List<MessageEntity> = messages.map { message ->
    val fingerprint = listOf(
        vaultKey,
        message.timestamp.toString(),
        message.sender.orEmpty(),
        message.text
    ).joinToString("\u0000")
    MessageEntity(
        messageKey = NotificationFingerprint.sha256(fingerprint),
        notificationKey = vaultKey,
        sender = message.sender,
        text = message.text,
        timestamp = message.timestamp,
        mimeType = message.mimeType,
        dataUri = message.dataUri
    )
}

fun CapturedNotification.toRevisionEntity(
    vaultKey: String = sbnKey
): NotificationRevisionEntity {
    val contentHash = NotificationFingerprint.contentHash(this)
    return NotificationRevisionEntity(
        revisionKey = NotificationFingerprint.sha256(
            vaultKey + "\u0000" + contentHash
        ),
        notificationKey = vaultKey,
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
    vaultKey: String = sbnKey,
    messagesText: String = messages.joinToString(" ") {
        it.sender.orEmpty() + " " + it.text
    }
): NotificationFtsEntity =
    NotificationFtsEntity(
        sbnKey = vaultKey,
        appLabel = appLabel,
        title = title,
        text = text,
        bigText = bigText,
        conversationTitle = conversationTitle,
        messagesText = messagesText
    )

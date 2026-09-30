package com.riccardopinato.notificationcontrol.core.notifications

import com.riccardopinato.notificationcontrol.data.vault.MessageEntity
import com.riccardopinato.notificationcontrol.data.vault.NotificationDao
import com.riccardopinato.notificationcontrol.data.vault.NotificationEntity

class NotificationVaultRepository(private val dao: NotificationDao) {
    suspend fun capture(notification: CapturedNotification, observedAt: Long = System.currentTimeMillis()) {
        val previous = dao.findByKey(notification.notificationKey)
        if (previous?.contentHash == notification.contentHash && previous.removedAt == null) return

        val entity = NotificationEntity(
            notificationKey = notification.notificationKey,
            packageName = notification.packageName,
            notificationId = notification.notificationId,
            tag = notification.tag,
            groupKey = notification.groupKey,
            postTime = notification.postTime,
            firstSeenAt = previous?.firstSeenAt ?: observedAt,
            lastUpdatedAt = observedAt,
            title = notification.title,
            text = notification.text,
            bigText = notification.bigText,
            subText = notification.subText,
            conversationTitle = notification.conversationTitle,
            category = notification.category,
            channelId = notification.channelId,
            isOngoing = notification.isOngoing,
            isClearable = notification.isClearable,
            contentHash = notification.contentHash,
            removedAt = null,
            removalReason = null,
            protectedFromCleanup = previous?.protectedFromCleanup ?: false
        )

        val messages = notification.messages.mapIndexed { index, message ->
            MessageEntity(
                messageKey = NotificationHasher.hash(
                    notification.notificationKey,
                    index.toString(),
                    message.timestamp.toString(),
                    message.sender,
                    message.text,
                    message.mimeType,
                    message.dataUri
                ),
                notificationKey = notification.notificationKey,
                position = index,
                sender = message.sender,
                text = message.text,
                timestamp = message.timestamp,
                mimeType = message.mimeType,
                dataUri = message.dataUri
            )
        }
        dao.replaceNotificationAndMessages(entity, messages)
    }

    suspend fun markRemoved(notificationKey: String, reason: Int?) {
        dao.markRemoved(notificationKey, System.currentTimeMillis(), reason)
    }
}

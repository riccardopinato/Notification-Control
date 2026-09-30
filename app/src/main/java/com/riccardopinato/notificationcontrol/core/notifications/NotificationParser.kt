package com.riccardopinato.notificationcontrol.core.notifications

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

class NotificationParser {
    fun parse(sbn: StatusBarNotification): CapturedNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val messages = messagingStyle?.messages.orEmpty().map { message ->
            CapturedMessage(
                sender = message.person?.name?.toString(),
                text = message.text?.toString(),
                timestamp = message.timestamp,
                mimeType = message.dataMimeType,
                dataUri = message.dataUri?.toString()
            )
        }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val conversationTitle = messagingStyle?.conversationTitle?.toString()
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            } else null
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.channelId else null

        val hash = NotificationHasher.hash(
            sbn.packageName,
            title,
            text,
            bigText,
            subText,
            conversationTitle,
            messages.joinToString("|") { "${it.timestamp}:${it.sender}:${it.text}:${it.mimeType}:${it.dataUri}" }
        )

        return CapturedNotification(
            notificationKey = sbn.key,
            packageName = sbn.packageName,
            notificationId = sbn.id,
            tag = sbn.tag,
            groupKey = sbn.groupKey,
            postTime = sbn.postTime,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            conversationTitle = conversationTitle,
            category = notification.category,
            channelId = channelId,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            messages = messages,
            contentHash = hash
        )
    }
}

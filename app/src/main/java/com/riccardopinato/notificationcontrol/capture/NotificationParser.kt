package com.riccardopinato.notificationcontrol.capture

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

class NotificationParser(
    private val context: Context,
    private val mediaStore: NotificationMediaStore = NotificationMediaStore(context)
) {
    fun parse(
        sbn: StatusBarNotification,
        now: Long = System.currentTimeMillis(),
        captureThumbnail: Boolean = false
    ): CapturedNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val appLabel = runCatching {
            val info = context.packageManager.getApplicationInfo(sbn.packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        val messagingStyle = runCatching {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
        }.getOrNull()

        val structuredMessages = buildList {
            messagingStyle?.historicMessages.orEmpty().forEach { message ->
                message.toCapturedMessage()?.let(::add)
            }
            messagingStyle?.messages.orEmpty().forEach { message ->
                message.toCapturedMessage()?.let(::add)
            }
        }.distinctBy { message ->
            listOf(
                message.timestamp.toString(),
                message.sender.orEmpty(),
                message.text,
                message.mimeType.orEmpty(),
                message.dataUri.orEmpty()
            ).joinToString("¦")
        }

        val textLineMessages = extras
            ?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            .orEmpty()
            .mapIndexedNotNull { index, line ->
                val value = line?.toString()?.trim().orEmpty()
                if (value.isBlank()) {
                    null
                } else {
                    CapturedMessage(
                        sender = null,
                        text = value,
                        timestamp = sbn.postTime + index
                    )
                }
            }

        val capturedMessages =
            if (structuredMessages.isNotEmpty()) structuredMessages else textLineMessages

        val mediaStableKey = sbn.key + "\u0000" + sbn.postTime

        return CapturedNotification(
            sbnKey = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel,
            notificationId = sbn.id,
            tag = sbn.tag,
            groupKey = sbn.groupKey,
            category = notification.category,
            channelId = if (android.os.Build.VERSION.SDK_INT >= 26) {
                notification.channelId
            } else {
                null
            },
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            conversationTitle =
                extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            thumbnailPath = if (captureThumbnail) {
                mediaStore.savePicture(notification, mediaStableKey)
            } else {
                null
            },
            postedAt = sbn.postTime,
            capturedAt = now,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            messages = capturedMessages
        )
    }
    private fun NotificationCompat.MessagingStyle.Message.toCapturedMessage():
        CapturedMessage? {
        val value = text?.toString()?.takeIf { it.isNotBlank() } ?: return null
        @Suppress("DEPRECATION")
        val legacySender = sender?.toString()
        return CapturedMessage(
            sender = person?.name?.toString() ?: legacySender,
            text = value,
            timestamp = timestamp,
            mimeType = dataMimeType,
            dataUri = dataUri?.toString()
        )
    }

}

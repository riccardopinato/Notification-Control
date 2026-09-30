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

        val structuredMessages = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                ?.messages
                .orEmpty()
                .mapNotNull { message ->
                    val text = message.text?.toString()?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    @Suppress("DEPRECATION")
                    val legacySender = message.sender?.toString()
                    CapturedMessage(
                        sender = message.person?.name?.toString() ?: legacySender,
                        text = text,
                        timestamp = message.timestamp,
                        mimeType = message.dataMimeType,
                        dataUri = message.dataUri?.toString()
                    )
                }
        }.getOrDefault(emptyList())

        val textLines = extras
            ?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val fallbackBigText = textLines
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n")

        return CapturedNotification(
            sbnKey = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel,
            notificationId = sbn.id,
            tag = sbn.tag,
            groupKey = sbn.groupKey,
            category = notification.category,
            channelId = if (android.os.Build.VERSION.SDK_INT >= 26) notification.channelId else null,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: structuredMessages.lastOrNull()?.text,
            bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: fallbackBigText,
            subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            conversationTitle = extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            thumbnailPath = if (captureThumbnail) {
                mediaStore.savePicture(
                    notification,
                    sbn.key + ":" + sbn.postTime + ":" + now
                )
            } else {
                null
            },
            postedAt = sbn.postTime,
            capturedAt = now,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            messages = structuredMessages
        )
    }
}

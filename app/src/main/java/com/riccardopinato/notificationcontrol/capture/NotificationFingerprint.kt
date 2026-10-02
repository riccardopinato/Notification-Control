package com.riccardopinato.notificationcontrol.capture

import java.security.MessageDigest

object NotificationFingerprint {
    fun contentHash(event: CapturedNotification): String {
        val content = listOf(
            event.title.orEmpty(),
            event.text.orEmpty(),
            event.bigText.orEmpty(),
            event.subText.orEmpty(),
            event.conversationTitle.orEmpty(),
            event.messages.joinToString("\u0001") {
                listOf(
                    if (it.timestampReliable) it.timestamp.toString() else "",
                    it.sender.orEmpty(),
                    it.text,
                    it.mimeType.orEmpty(),
                    it.dataUri.orEmpty()
                ).joinToString("\u0000")
            }
        ).joinToString("\u0002")
        return sha256(content)
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

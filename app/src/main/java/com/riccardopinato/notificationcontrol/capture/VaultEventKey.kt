package com.riccardopinato.notificationcontrol.capture

object VaultEventKey {
    fun create(event: CapturedNotification): String =
        NotificationFingerprint.sha256(
            listOf(
                event.sbnKey,
                event.packageName,
                event.notificationId.toString(),
                event.tag.orEmpty(),
                event.postedAt.toString(),
                event.capturedAt.toString()
            ).joinToString("\u0000")
        )
}

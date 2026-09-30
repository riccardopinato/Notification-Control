package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.capture.toMessageEntities
import com.riccardopinato.notificationcontrol.capture.toNotificationEntity
import com.riccardopinato.notificationcontrol.capture.toRevisionEntity
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.NotificationDatabase

class NotificationVaultRepository(
    private val settings: AppSettings,
    private val database: NotificationDatabase,
    private val mediaStore: NotificationMediaStore
) {
    fun shouldPersist(packageName: String): Boolean =
        packageName in settings.monitoredPackages

    fun shouldCaptureThumbnail(packageName: String): Boolean =
        shouldPersist(packageName) && settings.isPremium

    suspend fun persist(captured: CapturedNotification) {
        if (!shouldPersist(captured.packageName)) return

        val dao = database.notificationDao()
        val active = dao.findActiveByPlatformKey(captured.sbnKey)
        val eventKey = active?.sbnKey ?: newEventKey(captured)
        val previous = dao.findByKey(eventKey)

        dao.upsert(
            captured.toNotificationEntity(
                eventKey = eventKey,
                existingProtected = previous?.protected == true,
                existingThumbnailPath = previous?.thumbnailPath
            ),
            captured.toMessageEntities(eventKey),
            captured.toRevisionEntity(eventKey)
        )

        val replacedMedia = previous?.thumbnailPath
        if (
            replacedMedia != null &&
            captured.thumbnailPath != null &&
            replacedMedia != captured.thumbnailPath
        ) {
            mediaStore.delete(replacedMedia)
        }
    }

    suspend fun markRemoved(platformKey: String, reason: Int?) {
        database.notificationDao().markLatestRemovedByPlatformKey(
            platformKey = platformKey,
            removedAt = System.currentTimeMillis(),
            reason = reason
        )
    }

    private suspend fun newEventKey(captured: CapturedNotification): String {
        val dao = database.notificationDao()
        val initial = VaultEventIdentity.initialKey(
            platformKey = captured.sbnKey,
            postedAt = captured.postedAt
        )
        if (dao.findByKey(initial) == null) return initial

        var attempt = captured.capturedAt
        while (true) {
            val candidate = VaultEventIdentity.collisionKey(
                platformKey = captured.sbnKey,
                postedAt = captured.postedAt,
                capturedAt = attempt
            )
            if (dao.findByKey(candidate) == null) return candidate
            attempt++
        }
    }
}

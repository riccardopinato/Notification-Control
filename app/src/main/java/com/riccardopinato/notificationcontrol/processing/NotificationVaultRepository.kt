package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.capture.toFtsEntity
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
        val previous = dao.findByKey(captured.sbnKey)
        dao.upsert(
            captured.toNotificationEntity(
                existingProtected = previous?.protected == true,
                existingThumbnailPath = previous?.thumbnailPath
            ),
            captured.toMessageEntities(),
            captured.toRevisionEntity(),
            captured.toFtsEntity()
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

    suspend fun markRemoved(key: String, reason: Int?) {
        database.notificationDao().markRemoved(key, System.currentTimeMillis(), reason)
    }
}

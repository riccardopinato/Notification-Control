package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.NotificationFingerprint
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.capture.VaultEventKey
import com.riccardopinato.notificationcontrol.capture.toMessageEntities
import com.riccardopinato.notificationcontrol.capture.toNotificationEntity
import com.riccardopinato.notificationcontrol.capture.toRevisionEntity
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity

enum class VaultPersistKind {
    NOT_MONITORED,
    NEW_EVENT,
    CONTENT_CHANGED,
    DUPLICATE
}

data class VaultPersistResult(
    val kind: VaultPersistKind,
    val vaultKey: String? = null
)

class NotificationVaultRepository(
    private val settings: AppSettings,
    private val database: NotificationDatabase,
    private val mediaStore: NotificationMediaStore
) {
    fun shouldPersist(packageName: String): Boolean =
        packageName in settings.monitoredPackages

    fun shouldCaptureThumbnail(packageName: String): Boolean =
        shouldPersist(packageName) && settings.isPremium

    suspend fun persist(captured: CapturedNotification): VaultPersistResult {
        if (!shouldPersist(captured.packageName)) {
            captured.thumbnailPath?.let(mediaStore::delete)
            return VaultPersistResult(VaultPersistKind.NOT_MONITORED)
        }

        val dao = database.notificationDao()
        val active = dao.findActiveByPlatformKey(captured.sbnKey)
        val contentHash = NotificationFingerprint.contentHash(captured)
        val replay = if (active == null) {
            dao.findRecentRemovedReplay(
                packageName = captured.packageName,
                contentHash = contentHash,
                sinceMillis = captured.capturedAt - REPLAY_WINDOW_MS
            )
        } else {
            null
        }
        val existing = active ?: replay
        val vaultKey = existing?.sbnKey ?: VaultEventKey.create(captured)
        val revision = captured.toRevisionEntity(vaultKey)
        val previousRevision = existing?.let { dao.latestRevisionFor(vaultKey) }

        if (previousRevision?.contentHash == revision.contentHash) {
            if (replay != null) {
                persistReplay(
                    captured = captured,
                    replay = replay,
                    vaultKey = vaultKey,
                    revision = revision
                )
            } else if (
                captured.thumbnailPath != null &&
                captured.thumbnailPath != active?.thumbnailPath
            ) {
                mediaStore.delete(captured.thumbnailPath)
            }

            return VaultPersistResult(
                kind = VaultPersistKind.DUPLICATE,
                vaultKey = vaultKey
            )
        }

        dao.upsert(
            captured.toNotificationEntity(
                vaultKey = vaultKey,
                existingProtected = existing?.protected == true,
                existingThumbnailPath = existing?.thumbnailPath
            ),
            captured.toMessageEntities(vaultKey),
            revision
        )

        cleanupReplacedMedia(
            oldPath = existing?.thumbnailPath,
            newPath = captured.thumbnailPath
        )

        return VaultPersistResult(
            kind = if (existing == null) {
                VaultPersistKind.NEW_EVENT
            } else {
                VaultPersistKind.CONTENT_CHANGED
            },
            vaultKey = vaultKey
        )
    }

    suspend fun markRemoved(platformKey: String, reason: Int?) {
        database.notificationDao().markRemovedByPlatformKey(
            platformKey = platformKey,
            removedAt = System.currentTimeMillis(),
            reason = reason
        )
    }

    private suspend fun persistReplay(
        captured: CapturedNotification,
        replay: NotificationEntity,
        vaultKey: String,
        revision: NotificationRevisionEntity
    ) {
        database.notificationDao().upsert(
            captured.toNotificationEntity(
                vaultKey = vaultKey,
                existingProtected = replay.protected,
                existingThumbnailPath = replay.thumbnailPath
            ).copy(postedAt = replay.postedAt),
            emptyList(),
            revision
        )

        cleanupReplacedMedia(
            oldPath = replay.thumbnailPath,
            newPath = captured.thumbnailPath
        )
    }

    private fun cleanupReplacedMedia(oldPath: String?, newPath: String?) {
        if (
            oldPath != null &&
            newPath != null &&
            oldPath != newPath
        ) {
            mediaStore.delete(oldPath)
        }
    }

    private companion object {
        const val REPLAY_WINDOW_MS = 10_000L
    }
}

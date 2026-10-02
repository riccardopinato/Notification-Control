package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.capture.VaultEventKey
import com.riccardopinato.notificationcontrol.capture.toMessageEntities
import com.riccardopinato.notificationcontrol.capture.toNotificationEntity
import com.riccardopinato.notificationcontrol.capture.toRevisionEntity
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.NotificationDatabase

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

        val provisionalKey = active?.sbnKey ?: VaultEventKey.create(captured)
        val provisionalRevision = captured.toRevisionEntity(provisionalKey)

        val replay = if (active == null) {
            dao.findRecentEquivalentEvent(
                packageName = captured.packageName,
                notificationId = captured.notificationId,
                tag = captured.tag,
                postedAt = captured.postedAt,
                contentHash = provisionalRevision.contentHash,
                cutoffMillis = captured.capturedAt - REPLAY_WINDOW_MS
            )
        } else {
            null
        }

        if (replay != null) {
            dao.rebindPlatformKey(
                eventKey = replay.sbnKey,
                platformKey = captured.sbnKey,
                updatedAt = captured.capturedAt
            )
            if (
                captured.thumbnailPath != null &&
                captured.thumbnailPath != replay.thumbnailPath
            ) {
                mediaStore.delete(captured.thumbnailPath)
            }
            return VaultPersistResult(
                kind = VaultPersistKind.DUPLICATE,
                vaultKey = replay.sbnKey
            )
        }

        val vaultKey = provisionalKey
        val revision = if (vaultKey == provisionalKey) {
            provisionalRevision
        } else {
            captured.toRevisionEntity(vaultKey)
        }
        val previousRevision = active?.let { dao.latestRevisionFor(vaultKey) }

        if (previousRevision?.contentHash == revision.contentHash) {
            if (
                captured.thumbnailPath != null &&
                captured.thumbnailPath != active.thumbnailPath
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
                existingProtected = active?.protected == true,
                existingThumbnailPath = active?.thumbnailPath
            ),
            captured.toMessageEntities(vaultKey),
            revision
        )

        val replacedMedia = active?.thumbnailPath
        if (
            replacedMedia != null &&
            captured.thumbnailPath != null &&
            replacedMedia != captured.thumbnailPath
        ) {
            mediaStore.delete(replacedMedia)
        }

        return VaultPersistResult(
            kind = if (active == null) {
                VaultPersistKind.NEW_EVENT
            } else {
                VaultPersistKind.CONTENT_CHANGED
            },
            vaultKey = vaultKey
        )
    }

    suspend fun attachRecoveredThumbnail(
        eventKey: String,
        thumbnailPath: String
    ): Boolean {
        val dao = database.notificationDao()
        val current = dao.findByKey(eventKey)
        if (current == null) {
            mediaStore.delete(thumbnailPath)
            return false
        }
        if (current.thumbnailPath != null) {
            if (current.thumbnailPath != thumbnailPath) {
                mediaStore.delete(thumbnailPath)
            }
            return current.thumbnailPath == thumbnailPath
        }

        val updated = dao.attachThumbnailIfMissing(eventKey, thumbnailPath)
        if (updated == 0) {
            val latest = dao.findByKey(eventKey)
            if (latest?.thumbnailPath != thumbnailPath) {
                mediaStore.delete(thumbnailPath)
            }
            return latest?.thumbnailPath == thumbnailPath
        }
        return true
    }

    suspend fun markRemoved(platformKey: String, reason: Int?) {
        database.notificationDao().markRemovedByPlatformKey(
            platformKey = platformKey,
            removedAt = System.currentTimeMillis(),
            reason = reason
        )
    }

    companion object {
        internal const val REPLAY_WINDOW_MS = 30_000L
    }
}

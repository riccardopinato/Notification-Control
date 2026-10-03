package com.riccardopinato.notificationcontrol.capture

import android.content.Context
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.MediaRecoveryPendingEntity
import com.riccardopinato.notificationcontrol.data.MediaRescueEntity
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.processing.NotificationVaultRepository
import com.riccardopinato.notificationcontrol.workers.MediaRecoveryScheduler
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class MediaRecoveryCoordinator(
    context: Context,
    private val settings: AppSettings = AppSettings(context),
    private val database: NotificationDatabase = NotificationDatabase.get(context),
    private val mediaStore: NotificationMediaStore = NotificationMediaStore(context)
) {
    private val appContext = context.applicationContext
    private val recoveryDao = database.mediaRecoveryDao()
    private val notificationDao = database.notificationDao()
    private val safSource = WhatsAppSafMediaSource(appContext)
    private val vaultRepository = NotificationVaultRepository(
        settings = settings,
        database = database,
        mediaStore = mediaStore
    )

    suspend fun register(
        captured: CapturedNotification,
        notificationKey: String,
        revisionKey: String
    ) {
        if (!settings.isPremium) return
        if (!WhatsAppMediaRecoveryPolicy.supportsPackage(captured.packageName)) return

        val hasMediaStore = mediaStore.canRecoverWhatsAppImages()
        val hasSaf = captured.packageName == WHATSAPP_PACKAGE && hasSafAccess()
        if (!hasMediaStore && !hasSaf) return

        val now = System.currentTimeMillis()
        val snapshot = mediaStore.snapshot()
        val referenceHash = mediaStore.perceptualHashForPath(captured.thumbnailPath)
        recoveryDao.upsertPending(
            MediaRecoveryPendingEntity(
                revisionKey = revisionKey,
                notificationKey = notificationKey,
                packageName = captured.packageName,
                postedAt = captured.postedAt,
                capturedAt = captured.capturedAt,
                createdAt = now,
                expiresAt = now + PENDING_TTL_MS,
                baselineGeneration = snapshot.generation,
                mediaStoreVersion = snapshot.version,
                referencePerceptualHash = referenceHash
            )
        )
        MediaRecoveryScheduler.enqueue(appContext)
    }

    suspend fun resolvePending(now: Long = System.currentTimeMillis()): Int {
        recoveryDao.deleteExpiredPending(now)
        val targets = recoveryDao.activePending(now)
        if (targets.isEmpty()) return 0

        val rawCandidates = buildMap<String, MediaRecoveryCandidate> {
            targets.forEach { target ->
                mediaStore.queryWhatsAppCandidates(
                    packageName = target.packageName,
                    postedAt = target.postedAt,
                    baselineGeneration = target.baselineGeneration,
                    expectedVersion = target.mediaStoreVersion,
                    now = now
                ).forEach { put(it.sourceKey, it) }

                if (target.packageName == WHATSAPP_PACKAGE) {
                    safSource.queryCandidates(
                        treeUriString = settings.whatsAppMediaTreeUri,
                        postedAt = target.postedAt,
                        now = now
                    ).forEach { put(it.sourceKey, it) }
                }
            }
        }.values.toList()

        val needsVisualProof = targets.any { it.referencePerceptualHash != null }
        val candidates = if (needsVisualProof) {
            rawCandidates.map(mediaStore::withPerceptualHash)
        } else {
            rawCandidates
        }

        val assignments = MediaCorrelationEngine.assign(targets, candidates)
        val assignedSources = assignments.mapTo(hashSetOf()) { it.candidate.sourceKey }
        val assignedTargets = assignments.mapTo(hashSetOf()) { it.target.revisionKey }

        assignments.forEach { assignment ->
            val revision = notificationDao.findRevisionByKey(assignment.target.revisionKey)
            if (revision == null) {
                recoveryDao.deletePending(assignment.target.revisionKey)
                return@forEach
            }

            if (revision.thumbnailPath != null) {
                // Direct notification media already gives us a local copy. A strong independent
                // gallery/SAF match is used as proof-of-nine, not as a destructive replacement.
                recoveryDao.deletePending(assignment.target.revisionKey)
                recoveryDao.rescueBySourceKey(assignment.candidate.sourceKey)?.let { rescue ->
                    recoveryDao.deleteRescueBySourceKey(assignment.candidate.sourceKey)
                    if (rescue.localPath != revision.thumbnailPath) {
                        mediaStore.delete(rescue.localPath)
                    }
                }
                return@forEach
            }

            val rescue = recoveryDao.rescueBySourceKey(assignment.candidate.sourceKey)
            val localPath = rescue?.localPath ?: mediaStore.copyCandidate(
                candidate = assignment.candidate,
                stableKey = "recovery:" + assignment.target.revisionKey
            )
            if (localPath == null) return@forEach

            val attached = vaultRepository.attachRecoveredThumbnail(
                eventKey = assignment.target.notificationKey,
                revisionKey = assignment.target.revisionKey,
                thumbnailPath = localPath
            )
            if (attached) {
                recoveryDao.deletePending(assignment.target.revisionKey)
                recoveryDao.deleteRescueBySourceKey(assignment.candidate.sourceKey)
            }
        }

        candidates
            .asSequence()
            .filterNot { it.sourceKey in assignedSources }
            .forEach { candidate ->
                val confidence =
                    MediaCorrelationEngine.rescueConfidence(targets, candidate) ?: return@forEach
                if (recoveryDao.rescueBySourceKey(candidate.sourceKey) != null) return@forEach

                val enriched = if (candidate.perceptualHash == null) {
                    mediaStore.withPerceptualHash(candidate)
                } else {
                    candidate
                }
                val localPath = mediaStore.copyCandidate(
                    candidate = enriched,
                    stableKey = "rescue:" + enriched.sourceKey
                ) ?: return@forEach

                val inserted = recoveryDao.insertRescue(
                    MediaRescueEntity(
                        rescueKey = sha256(enriched.sourceKey),
                        sourceKey = enriched.sourceKey,
                        packageName = bestPackageFor(enriched, targets),
                        sourceKind = enriched.sourceKind,
                        localPath = localPath,
                        sourceUri = enriched.sourceUri,
                        mediaTimestamp = enriched.timestampMillis,
                        observedAt = now,
                        mimeType = enriched.mimeType,
                        width = enriched.width,
                        height = enriched.height,
                        sizeBytes = enriched.sizeBytes,
                        perceptualHash = enriched.perceptualHash,
                        confidence = confidence
                    )
                )
                if (inserted == -1L) {
                    val existing = recoveryDao.rescueBySourceKey(enriched.sourceKey)
                    if (existing?.localPath != localPath) {
                        mediaStore.delete(localPath)
                    }
                }
            }

        val remaining = targets
            .map(MediaRecoveryPendingEntity::revisionKey)
            .filterNot { it in assignedTargets }
        if (remaining.isNotEmpty()) {
            recoveryDao.markAttempted(remaining, now)
        }
        return recoveryDao.countActivePending(now)
    }

    suspend fun cleanupRescue(now: Long = System.currentTimeMillis()) {
        recoveryDao.rescuePackages().forEach { packageName ->
            val retentionDays = MediaRescueRetentionPolicy.daysForPackage(
                packageName = packageName,
                globalDays = settings.retentionDays,
                perAppDays = settings.retentionDaysPerApp
            )
            if (retentionDays == Int.MAX_VALUE) return@forEach
            val cutoff = now - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            val expiredPaths = recoveryDao.rescuePathsOlderThanForPackage(
                packageName = packageName,
                cutoffMillis = cutoff
            )
            recoveryDao.deleteRescueOlderThanForPackage(
                packageName = packageName,
                cutoffMillis = cutoff
            )
            expiredPaths.forEach(mediaStore::delete)
        }
    }

    fun hasSafAccess(): Boolean =
        safSource.hasPersistedAccess(settings.whatsAppMediaTreeUri)

    private fun bestPackageFor(
        candidate: MediaRecoveryCandidate,
        targets: List<MediaRecoveryPendingEntity>
    ): String {
        candidate.ownerPackageName?.let { owner ->
            if (WhatsAppMediaRecoveryPolicy.supportsPackage(owner)) return owner
        }
        return targets
            .mapNotNull { target ->
                MediaCorrelationEngine.score(target, candidate)?.let { target.packageName to it }
            }
            .maxByOrNull { it.second }
            ?.first
            ?: WHATSAPP_PACKAGE
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private val PENDING_TTL_MS = TimeUnit.MINUTES.toMillis(10)
    }
}

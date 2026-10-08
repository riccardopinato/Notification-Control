package com.riccardopinato.notificationcontrol.backup

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Base64
import androidx.room.withTransaction
import com.riccardopinato.notificationcontrol.automation.CriticalAlertScheduler
import com.riccardopinato.notificationcontrol.automation.FollowUpScheduler
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.CriticalAlertEntity
import com.riccardopinato.notificationcontrol.data.CriticalPatternEntity
import com.riccardopinato.notificationcontrol.data.FollowUpEntity
import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.MessageFtsEntity
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity
import com.riccardopinato.notificationcontrol.data.MediaRecoveryPendingEntity
import com.riccardopinato.notificationcontrol.data.MediaRescueEntity
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationFtsEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity
import com.riccardopinato.notificationcontrol.data.PickupCodeEntity
import com.riccardopinato.notificationcontrol.data.RuleActionEntity
import com.riccardopinato.notificationcontrol.data.RuleEntity
import com.riccardopinato.notificationcontrol.data.RestoreJournalEntity
import com.riccardopinato.notificationcontrol.domain.ProductLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class BackupSummary(
    val notifications: Int,
    val rules: Int,
    val followUps: Int,
    val warningCategories: List<String> = emptyList(),
    val recoveryPointCreated: Boolean = false
) {
    val warnings: Int
        get() = warningCategories.size
}

data class RecoveryPointInfo(
    val available: Boolean,
    val createdAt: Long? = null
)

private data class BackupRoot(
    val root: JSONObject,
    val summary: BackupSummary,
    val dataset: BackupDataset
)

private data class StagedRecoveryPoint(
    val operationId: String,
    val metadataFile: File,
    val pendingMediaDirectory: File,
    val finalMediaDirectory: File
)

private data class RecoveryMediaSources(
    val notifications: Map<String, File>,
    val revisions: Map<String, File>,
    val rescue: Map<String, File>
)

private data class BackupDataset(
    val notifications: List<NotificationEntity>,
    val messages: List<MessageEntity>,
    val revisions: List<NotificationRevisionEntity>,
    val rules: List<RuleEntity>,
    val actions: List<RuleActionEntity>,
    val critical: List<CriticalPatternEntity>,
    val criticalAlerts: List<CriticalAlertEntity>,
    val followUps: List<FollowUpEntity>,
    val pickupCodes: List<PickupCodeEntity>,
    val luminousProfiles: List<LuminousProfileEntity>,
    val pendingRecovery: List<MediaRecoveryPendingEntity>,
    val rescueRecovery: List<MediaRescueEntity>
)

class BackupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = NotificationDatabase.get(appContext)
    private val backupDao = database.backupDao()
    private val notificationDao = database.notificationDao()
    private val mediaRecoveryDao = database.mediaRecoveryDao()
    private val settings = AppSettings(appContext)
    private val mediaStore = NotificationMediaStore(appContext)

    suspend fun exportTo(uri: Uri, passphrase: CharArray): Result<BackupSummary> = runCatching {
        val backup = buildBackupRoot(includeTransientRecovery = false)
        val encrypted = encryptRoot(backup.root, passphrase)

        appContext.contentResolver.openOutputStream(uri, "w").use { output ->
            checkNotNull(output) { "Unable to open destination" }
            output.write(encrypted)
            output.flush()
        }

        backup.summary
    }

    fun recoveryPointInfo(): RecoveryPointInfo {
        val atomicFile = recoveryPointAtomicFile()
        val available = runCatching {
            atomicFile.openRead().use { input ->
                require(input.read() >= 0) { "Recovery Point is empty" }
            }
            true
        }.getOrDefault(false)
        val file = recoveryPointFile()
        return RecoveryPointInfo(
            available = available,
            createdAt = file.takeIf { available }?.lastModified()?.takeIf { it > 0L }
        )
    }

    fun discardRecoveryPoint() {
        runCatching { recoveryPointAtomicFile().delete() }
        runCatching { recoveryPointRootDirectory().deleteRecursively() }
    }

    suspend fun reconcileInterruptedRecoveryPoint() {
        recoveryMutex.withLock {
            reconcileInterruptedRecoveryPointLocked()
        }
    }

    private suspend fun reconcileInterruptedRecoveryPointLocked() {
        val journal = backupDao.restoreJournal()
        if (journal == null) {
            cleanupAbandonedRecoveryStaging()
            return
        }
        finalizeCommittedRecoveryPoint(journal.operationId)
    }

    suspend fun rollbackLastRestore(
        passphrase: CharArray
    ): Result<BackupSummary> = runCatching {
        recoveryMutex.withLock {
            require(settings.isPremium) { "Premium required for Recovery Point rollback" }
            reconcileInterruptedRecoveryPointLocked()
            val encrypted = readRecoveryPointLimited()
            val summary = restoreEncrypted(
                encrypted = encrypted,
                passphrase = passphrase,
                createRecoveryPoint = false
            )
            if (summary.warningCategories.isEmpty()) {
                discardRecoveryPoint()
            }
            summary
        }
    }

    private suspend fun stagePremiumRecoveryPoint(
        passphrase: CharArray
    ): StagedRecoveryPoint? {
        if (!settings.isPremium) return null

        val operationId = UUID.randomUUID().toString()
        val rootDirectory = recoveryPointRootDirectory()
        val pendingMetadata = recoveryPointPendingFile(operationId)
        val pendingMedia = recoveryPointPendingMediaDirectory(operationId)
        val finalMedia = recoveryPointMediaDirectory(operationId)

        runCatching { pendingMetadata.delete() }
        runCatching { pendingMedia.deleteRecursively() }
        runCatching { finalMedia.deleteRecursively() }

        try {
            val backup = buildBackupRoot(
                includeTransientRecovery = true,
                includeMediaPayloads = false
            )
            val mediaEntries = stageRecoveryPointMedia(
                dataset = backup.dataset,
                destination = pendingMedia
            )
            val root = backup.root
                .put("recoveryPoint", true)
                .put("recoveryPointOperationId", operationId)
                .put("recoveryMediaDir", finalMedia.name)
                .put("recoveryMedia", mediaEntries)
            val encrypted = encryptRoot(root, passphrase)

            rootDirectory.mkdirs()
            FileOutputStream(pendingMetadata).use { output ->
                output.write(encrypted)
                output.fd.sync()
            }
            return StagedRecoveryPoint(
                operationId = operationId,
                metadataFile = pendingMetadata,
                pendingMediaDirectory = pendingMedia,
                finalMediaDirectory = finalMedia
            )
        } catch (error: Throwable) {
            runCatching { pendingMetadata.delete() }
            runCatching { pendingMedia.deleteRecursively() }
            runCatching { finalMedia.deleteRecursively() }
            throw error
        }
    }

    private fun stageRecoveryPointMedia(
        dataset: BackupDataset,
        destination: File
    ): JSONArray {
        require(destination.mkdirs() || destination.isDirectory) {
            "Unable to create Recovery Point media staging directory"
        }

        val entries = JSONArray()
        val stagedByPath = linkedMapOf<String, String>()
        var nextFileIndex = 0

        fun stage(kind: String, key: String, path: String?) {
            if (path.isNullOrBlank()) return
            val canonicalPath = File(path).canonicalPath
            val fileName = stagedByPath[canonicalPath] ?: run {
                val bytes = requireNotNull(mediaStore.read(path)) {
                    "Unable to snapshot Recovery Point media: " + key
                }
                val generated = "%05d.bin".format(nextFileIndex++)
                val target = File(destination, generated)
                FileOutputStream(target).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                stagedByPath[canonicalPath] = generated
                generated
            }
            entries.put(
                JSONObject()
                    .put("kind", kind)
                    .put("key", key)
                    .put("file", fileName)
            )
        }

        dataset.notifications.forEach {
            stage(RECOVERY_MEDIA_NOTIFICATION, it.sbnKey, it.thumbnailPath)
        }
        dataset.revisions.forEach {
            stage(RECOVERY_MEDIA_REVISION, it.revisionKey, it.thumbnailPath)
        }
        dataset.rescueRecovery.forEach {
            require(it.localPath.isNotBlank()) {
                "Recovery Point Rescue media path is missing: " + it.rescueKey
            }
            stage(RECOVERY_MEDIA_RESCUE, it.rescueKey, it.localPath)
        }
        return entries
    }

    private suspend fun finalizeCommittedRecoveryPoint(operationId: String): Boolean {
        require(RECOVERY_OPERATION_ID.matches(operationId)) {
            "Invalid Recovery Point operation"
        }
        val journal = backupDao.restoreJournal()
            ?: error("Restore journal is missing")
        require(journal.operationId == operationId) {
            "Restore journal operation mismatch"
        }

        val pendingMetadata = recoveryPointPendingFile(operationId)
        val pendingMedia = recoveryPointPendingMediaDirectory(operationId)
        val finalMedia = recoveryPointMediaDirectory(operationId)

        if (pendingMedia.exists()) {
            if (finalMedia.exists()) {
                require(finalMedia.deleteRecursively()) {
                    "Unable to replace partial Recovery Point media"
                }
            }
            require(pendingMedia.renameTo(finalMedia)) {
                "Unable to promote Recovery Point media"
            }
        }
        require(finalMedia.isDirectory) {
            "Recovery Point media directory is missing"
        }

        if (pendingMetadata.isFile) {
            promoteStagedRecoveryPointMetadata(pendingMetadata)
        } else {
            // The metadata staging file is removed only after AtomicFile.finishWrite().
            // Therefore an absent staging file with a durable journal means promotion
            // already completed and only journal cleanup was interrupted.
            require(recoveryPointInfo().available) {
                "Recovery Point metadata is missing"
            }
        }

        cleanupRecoveryPointSidecars(keep = finalMedia)
        cleanupAbandonedRecoveryStaging()

        val referencedMedia =
            notificationDao.allThumbnailPaths().toSet() +
                mediaRecoveryDao.allRescuePaths().toSet()
        mediaStore.cleanupOrphans(referencedMedia)
        // Clear last: until this point startup reconciliation remains retryable.
        backupDao.clearRestoreJournal()
        return true
    }

    private fun promoteStagedRecoveryPointMetadata(staged: File): Boolean {
        require(staged.isFile) { "Staged Recovery Point metadata not found" }
        val atomicFile = recoveryPointAtomicFile()
        val output = atomicFile.startWrite()
        try {
            staged.inputStream().use { input ->
                input.copyTo(output)
            }
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
        // Deletion happens after the AtomicFile commit. If process death occurs
        // here, startup reconciliation safely replays the same metadata.
        runCatching { staged.delete() }
        return true
    }

    private fun cleanupAbandonedRecoveryStaging() {
        val root = recoveryPointRootDirectory()
        root.listFiles()?.forEach { file ->
            if (file.name.endsWith(".pending")) {
                runCatching {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            }
        }
    }

    private fun cleanupRecoveryPointSidecars(keep: File?) {
        val root = recoveryPointRootDirectory()
        val keepCanonical = keep?.let { runCatching { it.canonicalPath }.getOrNull() }
        root.listFiles()?.forEach { file ->
            if (!file.isDirectory || !file.name.startsWith("media-")) return@forEach
            val canonical = runCatching { file.canonicalPath }.getOrNull()
            if (canonical != keepCanonical) {
                runCatching { file.deleteRecursively() }
            }
        }
    }

    private fun cleanupStagedRecoveryPoint(staged: StagedRecoveryPoint) {
        runCatching { staged.metadataFile.delete() }
        runCatching { staged.pendingMediaDirectory.deleteRecursively() }
        runCatching { staged.finalMediaDirectory.deleteRecursively() }
    }

    private suspend fun buildBackupRoot(
        includeTransientRecovery: Boolean,
        includeMediaPayloads: Boolean = true
    ): BackupRoot {
        val dataset = database.withTransaction {
            BackupDataset(
                notifications = backupDao.allNotifications(),
                messages = backupDao.allMessages(),
                revisions = backupDao.allRevisions(),
                rules = backupDao.allRules(),
                actions = backupDao.allRuleActions(),
                critical = backupDao.allCriticalPatterns(),
                criticalAlerts = backupDao.allCriticalAlerts(),
                followUps = backupDao.allFollowUps(),
                pickupCodes = backupDao.allPickupCodes(),
                luminousProfiles = database.luminousProfileDao().allProfiles(),
                pendingRecovery = if (includeTransientRecovery) {
                    mediaRecoveryDao.allPendingForRecoveryPoint()
                } else {
                    emptyList()
                },
                rescueRecovery = if (includeTransientRecovery) {
                    mediaRecoveryDao.allRescueForRecoveryPoint()
                } else {
                    emptyList()
                }
            )
        }
        val notifications = dataset.notifications
        val messages = dataset.messages
        val revisions = dataset.revisions
        val rules = dataset.rules
        val actions = dataset.actions
        val critical = dataset.critical
        val criticalAlerts = dataset.criticalAlerts
        val followUps = dataset.followUps
        val pickupCodes = dataset.pickupCodes
        val luminousProfiles = dataset.luminousProfiles
        val pendingRecovery = dataset.pendingRecovery
        val rescueRecovery = dataset.rescueRecovery
        val revisionMediaPaths = revisions.mapNotNull { it.thumbnailPath }.toSet()

        val root = JSONObject()
            .put("format", FORMAT_VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("notifications", JSONArray().apply {
                notifications.forEach { put(notificationJson(it)) }
            })
            .put("messages", JSONArray().apply {
                messages.forEach { put(messageJson(it)) }
            })
            .put("revisions", JSONArray().apply {
                revisions.forEach { put(revisionJson(it)) }
            })
            .put("rules", JSONArray().apply {
                rules.forEach { put(ruleJson(it)) }
            })
            .put("ruleActions", JSONArray().apply {
                actions.forEach { put(ruleActionJson(it)) }
            })
            .put("criticalPatterns", JSONArray().apply {
                critical.forEach { put(criticalJson(it)) }
            })
            .put("criticalAlerts", JSONArray().apply {
                criticalAlerts.forEach { put(criticalAlertJson(it)) }
            })
            .put("followUps", JSONArray().apply {
                followUps.forEach { put(followUpJson(it)) }
            })
            .put("pickupCodes", JSONArray().apply {
                pickupCodes.forEach { put(pickupJson(it)) }
            })
            .put("luminousProfiles", JSONArray().apply {
                luminousProfiles.forEach { put(luminousProfileJson(it)) }
            })
            .put("media", JSONArray().apply {
                if (!includeMediaPayloads) return@apply
                notifications.forEach { notification ->
                    val path = notification.thumbnailPath ?: return@forEach
                    if (path in revisionMediaPaths) return@forEach
                    val bytes = mediaStore.read(path)
                    if (bytes == null) {
                        require(!includeTransientRecovery) {
                            "Unable to snapshot notification media: " +
                                notification.sbnKey
                        }
                        return@forEach
                    }
                    put(
                        JSONObject()
                            .put("notificationKey", notification.sbnKey)
                            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    )
                }
            })
            .put("revisionMedia", JSONArray().apply {
                if (!includeMediaPayloads) return@apply
                revisions.forEach { revision ->
                    val path = revision.thumbnailPath ?: return@forEach
                    val bytes = mediaStore.read(path)
                    if (bytes == null) {
                        require(!includeTransientRecovery) {
                            "Unable to snapshot revision media: " +
                                revision.revisionKey
                        }
                        return@forEach
                    }
                    put(
                        JSONObject()
                            .put("revisionKey", revision.revisionKey)
                            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    )
                }
            })
            .put("settings", settingsJson())

        if (includeTransientRecovery) {
            root
                .put("recoveryPending", JSONArray().apply {
                    pendingRecovery.forEach { put(recoveryPendingJson(it)) }
                })
                .put("recoveryRescue", JSONArray().apply {
                    rescueRecovery.forEach { put(recoveryRescueJson(it)) }
                })
                .put("recoveryRescueMedia", JSONArray().apply {
                    if (!includeMediaPayloads) return@apply
                    rescueRecovery.forEach { rescue ->
                        val bytes = requireNotNull(mediaStore.read(rescue.localPath)) {
                            "Unable to snapshot Rescue media: " + rescue.rescueKey
                        }
                        put(
                            JSONObject()
                                .put("rescueKey", rescue.rescueKey)
                                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        )
                    }
                })
        }

        return BackupRoot(
            root = root,
            summary = BackupSummary(
                notifications = notifications.size,
                rules = rules.size,
                followUps = followUps.size
            ),
            dataset = dataset
        )
    }

    private fun encryptRoot(root: JSONObject, passphrase: CharArray): ByteArray {
        val plain = root.toString().toByteArray(Charsets.UTF_8)
        val encrypted = EncryptedBackupCodec.encrypt(plain, passphrase)
        check(encrypted.size <= EncryptedBackupCodec.maxEncryptedBytes) {
            "Backup exceeds supported size"
        }
        return encrypted
    }

    private fun recoveryPointRootDirectory(): File =
        File(appContext.filesDir, "restore_recovery").apply { mkdirs() }

    private fun recoveryPointFile(): File =
        File(recoveryPointRootDirectory(), "last_restore.ncb")

    private fun recoveryPointPendingFile(operationId: String): File =
        File(recoveryPointRootDirectory(), "last_restore.$operationId.pending")

    private fun recoveryPointPendingMediaDirectory(operationId: String): File =
        File(recoveryPointRootDirectory(), "media-$operationId.pending")

    private fun recoveryPointMediaDirectory(operationId: String): File =
        File(recoveryPointRootDirectory(), "media-$operationId")

    private fun recoveryPointAtomicFile(): AtomicFile =
        AtomicFile(recoveryPointFile())

    private fun readRecoveryPointLimited(): ByteArray =
        recoveryPointAtomicFile().openRead().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= EncryptedBackupCodec.maxEncryptedBytes) {
                    "Recovery Point metadata too large"
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }

    private fun recoveryMediaSources(root: JSONObject): RecoveryMediaSources? {
        if (!root.optBoolean("recoveryPoint", false)) return null
        val operationId = root.optString("recoveryPointOperationId")
        require(RECOVERY_OPERATION_ID.matches(operationId)) {
            "Invalid Recovery Point operation"
        }
        val directoryName = root.optString("recoveryMediaDir").takeIf { it.isNotBlank() }
            ?: return null
        require(
            RECOVERY_MEDIA_DIRECTORY.matches(directoryName) &&
                directoryName == "media-$operationId"
        ) {
            "Invalid Recovery Point media directory"
        }
        val rootDirectory = recoveryPointRootDirectory().canonicalFile
        val directory = File(rootDirectory, directoryName).canonicalFile
        require(directory.parentFile == rootDirectory && directory.isDirectory) {
            "Recovery Point media directory is unavailable"
        }

        val notifications = linkedMapOf<String, File>()
        val revisions = linkedMapOf<String, File>()
        val rescue = linkedMapOf<String, File>()
        val entries = root.optJSONArray("recoveryMedia")
            ?: error("Recovery Point media manifest is missing")

        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val kind = entry.getString("kind")
            val key = entry.getString("key")
            val fileName = entry.getString("file")
            require(key.isNotBlank() && RECOVERY_MEDIA_FILE.matches(fileName)) {
                "Invalid Recovery Point media entry"
            }
            val file = File(directory, fileName).canonicalFile
            require(
                file.parentFile == directory &&
                    file.isFile &&
                    file.length() in 1..MAX_MEDIA_BYTES.toLong()
            ) {
                "Recovery Point media file is missing or invalid"
            }
            val target = when (kind) {
                RECOVERY_MEDIA_NOTIFICATION -> notifications
                RECOVERY_MEDIA_REVISION -> revisions
                RECOVERY_MEDIA_RESCUE -> rescue
                else -> error("Unsupported Recovery Point media kind")
            }
            require(target.put(key, file) == null) {
                "Duplicate Recovery Point media entry"
            }
        }
        return RecoveryMediaSources(notifications, revisions, rescue)
    }

    private fun readRecoveryMediaFile(file: File): ByteArray {
        require(file.isFile && file.length() in 1..MAX_MEDIA_BYTES.toLong()) {
            "Recovery Point media file is invalid"
        }
        return file.readBytes()
    }

    suspend fun restoreFrom(
        uri: Uri,
        passphrase: CharArray
    ): Result<BackupSummary> = runCatching {
        recoveryMutex.withLock {
            reconcileInterruptedRecoveryPointLocked()
            restoreEncrypted(
                encrypted = readLimited(uri),
                passphrase = passphrase,
                createRecoveryPoint = settings.isPremium
            )
        }
    }

    private suspend fun restoreEncrypted(
        encrypted: ByteArray,
        passphrase: CharArray,
        createRecoveryPoint: Boolean
    ): BackupSummary {
        val plain = EncryptedBackupCodec.decrypt(encrypted, passphrase)
        val root = JSONObject(plain.toString(Charsets.UTF_8))
        require(root.getInt("format") == FORMAT_VERSION) { "Unsupported backup version" }

        val recoveryMediaSources = recoveryMediaSources(root)
        val mediaPayloads = if (recoveryMediaSources == null) {
            parseMedia(root.getJSONArray("media"))
        } else {
            emptyMap()
        }
        val revisionMediaPayloads = if (recoveryMediaSources == null) {
            parseRevisionMedia(root.optJSONArray("revisionMedia") ?: JSONArray())
        } else {
            emptyMap()
        }
        val rescueMediaPayloads = if (recoveryMediaSources == null) {
            parseRescueMedia(root.optJSONArray("recoveryRescueMedia") ?: JSONArray())
        } else {
            emptyMap()
        }
        val notifications = parseNotifications(root.getJSONArray("notifications"))
        val messages = parseMessages(root.getJSONArray("messages"))
        val revisions = parseRevisions(root.getJSONArray("revisions"))
        val rules = parseRules(root.getJSONArray("rules"))
        val actions = parseRuleActions(root.getJSONArray("ruleActions"))
        val critical = parseCritical(root.getJSONArray("criticalPatterns"))
        val criticalAlerts = parseCriticalAlerts(
            root.optJSONArray("criticalAlerts") ?: JSONArray()
        )
        val followUps = parseFollowUps(root.getJSONArray("followUps"))
        val pickupCodes = parsePickupCodes(root.getJSONArray("pickupCodes"))
        val luminousProfiles = parseLuminousProfiles(
            root.optJSONArray("luminousProfiles") ?: JSONArray()
        )
        val recoveryPending = parseRecoveryPending(
            root.optJSONArray("recoveryPending") ?: JSONArray()
        )
        val recoveryRescue = parseRecoveryRescue(
            root.optJSONArray("recoveryRescue") ?: JSONArray()
        )
        val settingsObject = root.optJSONObject("settings")

        validateReferences(
            notifications = notifications,
            messages = messages,
            revisions = revisions,
            rules = rules,
            actions = actions,
            recoveryPending = recoveryPending
        )

        restoreTestHook?.invoke("BEFORE_RECOVERY_POINT")
        val stagedRecoveryPoint =
            if (createRecoveryPoint && settings.isPremium) {
                stagePremiumRecoveryPoint(passphrase)
            } else {
                null
            }
        try {
            restoreTestHook?.invoke("AFTER_RECOVERY_POINT")
        } catch (error: Throwable) {
            stagedRecoveryPoint?.let(::cleanupStagedRecoveryPoint)
            throw error
        }

        val previousVaultMedia = notificationDao.allThumbnailPaths().toSet()
        val previousRescueMedia = mediaRecoveryDao.allRescuePaths().toSet()
        val previousMedia = previousVaultMedia + previousRescueMedia
        val restorePrefix = "restore:" + System.currentTimeMillis() + ":"
        val stagedRestoredMedia = mutableSetOf<String>()

        fun restoreRequiredPicture(key: String, bytes: ByteArray): String {
            val restored = requireNotNull(mediaStore.restorePicture(key, bytes)) {
                "Unable to stage backup media: " + key
            }
            stagedRestoredMedia += restored
            return restored
        }

        val revisionsWithMedia: List<NotificationRevisionEntity>
        val notificationsWithMedia: List<NotificationEntity>
        val rescueWithMedia: List<MediaRescueEntity>
        try {
            revisionsWithMedia = revisions.map { revision ->
            revision.copy(
                thumbnailPath = (
                    recoveryMediaSources?.revisions?.get(revision.revisionKey)
                        ?.let(::readRecoveryMediaFile)
                        ?: revisionMediaPayloads[revision.revisionKey]
                    )?.let {
                    restoreRequiredPicture(
                        restorePrefix + "revision:" + revision.revisionKey,
                        it
                    )
                }
            )
        }
            val latestRevisionMediaByNotification = revisionsWithMedia
            .asSequence()
            .filter { it.thumbnailPath != null }
            .groupBy { it.notificationKey }
            .mapValues { (_, values) -> values.maxByOrNull { it.capturedAt } }

            notificationsWithMedia = notifications.map { notification ->
            val legacyPath = (
                recoveryMediaSources?.notifications?.get(notification.sbnKey)
                    ?.let(::readRecoveryMediaFile)
                    ?: mediaPayloads[notification.sbnKey]
                )?.let {
                restoreRequiredPicture(
                    restorePrefix + "notification:" + notification.sbnKey,
                    it
                )
            }
            notification.copy(
                thumbnailPath = legacyPath
                    ?: latestRevisionMediaByNotification[notification.sbnKey]?.thumbnailPath
            )
        }

            rescueWithMedia = recoveryRescue.map { rescue ->
            val restoredPath = (
                recoveryMediaSources?.rescue?.get(rescue.rescueKey)
                    ?.let(::readRecoveryMediaFile)
                    ?: rescueMediaPayloads[rescue.rescueKey]
                )?.let {
                restoreRequiredPicture(
                    restorePrefix + "rescue:" + rescue.rescueKey,
                    it
                )
            }
            if (root.optBoolean("recoveryPoint", false) && rescue.localPath.isNotBlank()) {
                requireNotNull(restoredPath) {
                    "Recovery Point Rescue media is incomplete: " + rescue.rescueKey
                }
            }
            rescue.copy(localPath = restoredPath ?: rescue.localPath)
            }
        } catch (error: Throwable) {
            stagedRecoveryPoint?.let(::cleanupStagedRecoveryPoint)
            stagedRestoredMedia.forEach(mediaStore::delete)
            throw error
        }

        val restoredMedia = buildSet {
            notificationsWithMedia.mapNotNullTo(this) { it.thumbnailPath }
            revisionsWithMedia.mapNotNullTo(this) { it.thumbnailPath }
            rescueWithMedia.mapNotNullTo(this) { it.localPath }
        }

        val warningCategories = mutableListOf<String>()
        var recoveryPointCreated = false

        try {
            restoreTestHook?.invoke("BEFORE_TRANSACTION")
            database.withTransaction {
                restoreTestHook?.invoke("TRANSACTION_BEFORE_DELETE")
                mediaRecoveryDao.deleteAllPending()
                mediaRecoveryDao.deleteAllRescue()
                database.luminousProfileDao().deleteAll()
                backupDao.deletePickupCodes()
                backupDao.deleteFollowUps()
                backupDao.deleteCriticalAlerts()
                backupDao.deleteCriticalPatterns()
                backupDao.deleteRuleActions()
                backupDao.deleteRules()
                backupDao.deleteFts()
                backupDao.deleteMessageFts()
                backupDao.deleteRevisions()
                backupDao.deleteMessages()
                backupDao.deleteNotifications()

                if (notificationsWithMedia.isNotEmpty()) {
                    backupDao.insertNotifications(notificationsWithMedia)
                }
                if (messages.isNotEmpty()) {
                    backupDao.insertMessages(messages)
                    backupDao.insertMessageFts(
                        messages.map {
                            MessageFtsEntity(
                                messageKey = it.messageKey,
                                notificationKey = it.notificationKey,
                                sender = it.sender,
                                text = it.text
                            )
                        }
                    )
                }
                if (revisionsWithMedia.isNotEmpty()) {
                    backupDao.insertRevisions(revisionsWithMedia)
                }
                if (recoveryPending.isNotEmpty()) {
                    mediaRecoveryDao.restorePending(recoveryPending)
                }
                if (rescueWithMedia.isNotEmpty()) {
                    mediaRecoveryDao.restoreRescue(rescueWithMedia)
                }
                if (rules.isNotEmpty()) backupDao.insertRules(rules)
                if (actions.isNotEmpty()) backupDao.insertRuleActions(actions)
                if (critical.isNotEmpty()) backupDao.insertCriticalPatterns(critical)
                if (criticalAlerts.isNotEmpty()) {
                    backupDao.insertCriticalAlerts(criticalAlerts)
                }
                if (followUps.isNotEmpty()) backupDao.insertFollowUps(followUps)
                if (pickupCodes.isNotEmpty()) backupDao.insertPickupCodes(pickupCodes)
                if (luminousProfiles.isNotEmpty()) {
                    database.luminousProfileDao().insertAll(luminousProfiles)
                }

                notificationsWithMedia.forEach { notification ->
                    notificationDao.insertFts(
                        NotificationFtsEntity(
                            sbnKey = notification.sbnKey,
                            appLabel = notification.appLabel,
                            title = notification.title,
                            text = notification.text,
                            bigText = notification.bigText,
                            conversationTitle = notification.conversationTitle,
                            messagesText = ""
                        )
                    )
                }
                if (stagedRecoveryPoint != null) {
                    backupDao.upsertRestoreJournal(
                        RestoreJournalEntity(
                            operationId = stagedRecoveryPoint.operationId,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                restoreTestHook?.invoke("TRANSACTION_AFTER_INSERT")
            }
        } catch (error: Throwable) {
            stagedRecoveryPoint?.let(::cleanupStagedRecoveryPoint)
            restoredMedia.filterNot { it in previousMedia }.forEach(mediaStore::delete)
            throw error
        }

        recoveryPointCreated = stagedRecoveryPoint?.let { staged ->
            runCatching { finalizeCommittedRecoveryPoint(staged.operationId) }
                .onFailure { warningCategories += "RECOVERY_POINT_COMMIT" }
                .getOrDefault(false)
        } ?: false

        if (stagedRecoveryPoint == null || recoveryPointCreated) {
            previousMedia.filterNot { it in restoredMedia }.forEach(mediaStore::delete)
            mediaStore.cleanupOrphans(restoredMedia)
        }

        runCatching { restoreTestHook?.invoke("AFTER_TRANSACTION") }
            .onFailure { warningCategories += "TEST_AFTER_TRANSACTION" }

        if (settingsObject != null) {
            runCatching { restoreSettings(settingsObject) }
                .onFailure { warningCategories += "SETTINGS_RESTORE" }
        }

        runCatching {
            FollowUpScheduler.cancelAll(appContext)
            followUps.filter { it.status == "ACTIVE" }.forEach {
                FollowUpScheduler.schedule(appContext, it.id, it.dueAt)
            }
        }.onFailure {
            warningCategories += "FOLLOW_UP_SCHEDULER"
        }

        runCatching {
            CriticalAlertScheduler.cancelAll(appContext)
            criticalAlerts.filter { it.status == "ACTIVE" }.forEach {
                CriticalAlertScheduler.schedule(appContext, it.id, it.nextAt)
            }
        }.onFailure {
            warningCategories += "CRITICAL_ALERT_SCHEDULER"
        }

        return BackupSummary(
            notifications = notificationsWithMedia.size,
            rules = rules.size,
            followUps = followUps.size,
            warningCategories = warningCategories.distinct(),
            recoveryPointCreated = recoveryPointCreated
        )
    }

    private fun parseNotifications(array: JSONArray): List<NotificationEntity> = buildList {
        for (index in 0 until array.length()) {
            val o = array.getJSONObject(index)
            add(
                NotificationEntity(
                    sbnKey = o.getString("sbnKey"),
                    platformKey = o.optString("platformKey", o.getString("sbnKey")),
                    packageName = o.getString("packageName"),
                    appLabel = o.getString("appLabel"),
                    notificationId = o.getInt("notificationId"),
                    tag = o.stringOrNull("tag"),
                    groupKey = o.stringOrNull("groupKey"),
                    category = o.stringOrNull("category"),
                    channelId = o.stringOrNull("channelId"),
                    title = o.stringOrNull("title"),
                    text = o.stringOrNull("text"),
                    bigText = o.stringOrNull("bigText"),
                    subText = o.stringOrNull("subText"),
                    conversationTitle = o.stringOrNull("conversationTitle"),
                    thumbnailPath = null,
                    postedAt = o.getLong("postedAt"),
                    updatedAt = o.getLong("updatedAt"),
                    removedAt = o.longOrNull("removedAt"),
                    removalReason = o.intOrNull("removalReason"),
                    isOngoing = o.getBoolean("isOngoing"),
                    isClearable = o.getBoolean("isClearable"),
                    protected = o.getBoolean("protected")
                )
            )
        }
    }

    private fun parseMessages(array: JSONArray): List<MessageEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                MessageEntity(
                    messageKey = o.getString("messageKey"),
                    notificationKey = o.getString("notificationKey"),
                    sender = o.stringOrNull("sender"),
                    text = o.getString("text"),
                    timestamp = o.getLong("timestamp"),
                    mimeType = o.stringOrNull("mimeType"),
                    dataUri = o.stringOrNull("dataUri")
                )
            )
        }
    }

    private fun parseRevisions(array: JSONArray): List<NotificationRevisionEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                NotificationRevisionEntity(
                    revisionKey = o.getString("revisionKey"),
                    notificationKey = o.getString("notificationKey"),
                    capturedAt = o.getLong("capturedAt"),
                    title = o.stringOrNull("title"),
                    text = o.stringOrNull("text"),
                    bigText = o.stringOrNull("bigText"),
                    subText = o.stringOrNull("subText"),
                    conversationTitle = o.stringOrNull("conversationTitle"),
                    thumbnailPath = null,
                    contentHash = o.getString("contentHash")
                )
            )
        }
    }

    private fun parseRules(array: JSONArray): List<RuleEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                RuleEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    enabled = o.getBoolean("enabled"),
                    packageName = o.stringOrNull("packageName"),
                    senderQuery = o.stringOrNull("senderQuery"),
                    textQuery = o.stringOrNull("textQuery"),
                    matchMode = o.getString("matchMode"),
                    timeStartMinutes = o.intOrNull("timeStartMinutes"),
                    timeEndMinutes = o.intOrNull("timeEndMinutes"),
                    screenState = o.optString("screenState", "ANY"),
                    priority = o.getInt("priority"),
                    createdAt = o.getLong("createdAt")
                )
            )
        }
    }

    private fun parseRuleActions(array: JSONArray): List<RuleActionEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                RuleActionEntity(
                    id = o.getLong("id"),
                    ruleId = o.getLong("ruleId"),
                    actionType = o.getString("actionType"),
                    actionValue = o.stringOrNull("actionValue")
                )
            )
        }
    }

    private fun parseCritical(array: JSONArray): List<CriticalPatternEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                CriticalPatternEntity(
                    id = o.getLong("id"),
                    type = o.getString("type"),
                    value = o.getString("value"),
                    enabled = o.getBoolean("enabled"),
                    createdAt = o.getLong("createdAt")
                )
            )
        }
    }

    private fun parseCriticalAlerts(
        array: JSONArray
    ): List<CriticalAlertEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                CriticalAlertEntity(
                    id = o.getLong("id"),
                    eventKey = o.getString("eventKey"),
                    sourcePackage = o.getString("sourcePackage"),
                    sourceLabel = o.getString("sourceLabel"),
                    title = o.stringOrNull("title"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                    status = o.getString("status"),
                    escalationStep = o.getInt("escalationStep"),
                    nextAt = o.getLong("nextAt")
                )
            )
        }
    }

    private fun parseFollowUps(array: JSONArray): List<FollowUpEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                FollowUpEntity(
                    id = o.getLong("id"),
                    notificationKey = o.stringOrNull("notificationKey"),
                    sourcePackage = o.stringOrNull("sourcePackage"),
                    sourceLabel = o.stringOrNull("sourceLabel"),
                    title = o.getString("title"),
                    body = o.stringOrNull("body"),
                    dueAt = o.getLong("dueAt"),
                    status = o.getString("status"),
                    repeatMinutes = o.intOrNull("repeatMinutes"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            )
        }
    }

    private fun parsePickupCodes(array: JSONArray): List<PickupCodeEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                PickupCodeEntity(
                    id = o.getLong("id"),
                    code = o.getString("code"),
                    sourcePackage = o.getString("sourcePackage"),
                    sourceLabel = o.getString("sourceLabel"),
                    notificationKey = o.getString("notificationKey"),
                    contextText = o.stringOrNull("contextText"),
                    createdAt = o.getLong("createdAt"),
                    expiresAt = o.getLong("expiresAt"),
                    dismissed = o.getBoolean("dismissed")
                )
            )
        }
    }

    private fun parseLuminousProfiles(array: JSONArray): List<LuminousProfileEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                LuminousProfileEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    enabled = o.getBoolean("enabled"),
                    packageName = o.stringOrNull("packageName"),
                    senderQuery = o.stringOrNull("senderQuery"),
                    colorHex = o.getString("colorHex"),
                    flashEnabled = o.getBoolean("flashEnabled"),
                    overlayEnabled = o.getBoolean("overlayEnabled"),
                    strobeCycles = o.getInt("strobeCycles"),
                    strobeSpeedMs = o.getLong("strobeSpeedMs"),
                    circleThickness = o.getDouble("circleThickness").toFloat(),
                    circleGlow = o.getDouble("circleGlow").toFloat(),
                    pulseSpeedMs = o.getLong("pulseSpeedMs"),
                    displayDurationMs = o.getLong("displayDurationMs"),
                    priority = o.getInt("priority"),
                    createdAt = o.getLong("createdAt")
                )
            )
        }
    }

    private fun parseMedia(array: JSONArray): Map<String, ByteArray> = buildMap {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val bytes = Base64.decode(o.getString("data"), Base64.DEFAULT)
            require(bytes.size <= MAX_MEDIA_BYTES) { "Media preview too large" }
            put(o.getString("notificationKey"), bytes)
        }
    }

    private fun parseRevisionMedia(array: JSONArray): Map<String, ByteArray> = buildMap {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val bytes = Base64.decode(o.getString("data"), Base64.DEFAULT)
            require(bytes.size <= MAX_MEDIA_BYTES) { "Revision media preview too large" }
            put(o.getString("revisionKey"), bytes)
        }
    }

    private fun parseRescueMedia(array: JSONArray): Map<String, ByteArray> = buildMap {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val bytes = Base64.decode(o.getString("data"), Base64.DEFAULT)
            require(bytes.size <= MAX_MEDIA_BYTES) { "Recovery rescue media too large" }
            put(o.getString("rescueKey"), bytes)
        }
    }

    private fun parseRecoveryPending(
        array: JSONArray
    ): List<MediaRecoveryPendingEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                MediaRecoveryPendingEntity(
                    revisionKey = o.getString("revisionKey"),
                    notificationKey = o.getString("notificationKey"),
                    packageName = o.getString("packageName"),
                    postedAt = o.getLong("postedAt"),
                    capturedAt = o.getLong("capturedAt"),
                    createdAt = o.getLong("createdAt"),
                    expiresAt = o.getLong("expiresAt"),
                    baselineGeneration = o.longOrNull("baselineGeneration"),
                    mediaStoreVersion = o.stringOrNull("mediaStoreVersion"),
                    referencePerceptualHash = o.stringOrNull("referencePerceptualHash"),
                    attempts = o.optInt("attempts", 0),
                    lastAttemptAt = o.longOrNull("lastAttemptAt")
                )
            )
        }
    }

    private fun parseRecoveryRescue(
        array: JSONArray
    ): List<MediaRescueEntity> = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                MediaRescueEntity(
                    rescueKey = o.getString("rescueKey"),
                    sourceKey = o.getString("sourceKey"),
                    packageName = o.getString("packageName"),
                    sourceKind = o.getString("sourceKind"),
                    localPath = o.optString("localPath"),
                    sourceUri = o.stringOrNull("sourceUri"),
                    mediaTimestamp = o.getLong("mediaTimestamp"),
                    observedAt = o.getLong("observedAt"),
                    mimeType = o.stringOrNull("mimeType"),
                    width = o.getInt("width"),
                    height = o.getInt("height"),
                    sizeBytes = o.getLong("sizeBytes"),
                    perceptualHash = o.stringOrNull("perceptualHash"),
                    confidence = o.getInt("confidence")
                )
            )
        }
    }

    private fun validateReferences(
        notifications: List<NotificationEntity>,
        messages: List<MessageEntity>,
        revisions: List<NotificationRevisionEntity>,
        rules: List<RuleEntity>,
        actions: List<RuleActionEntity>,
        recoveryPending: List<MediaRecoveryPendingEntity>
    ) {
        val notificationKeys = notifications.mapTo(hashSetOf()) { it.sbnKey }
        require(messages.all { it.notificationKey in notificationKeys }) {
            "Backup contains orphan messages"
        }
        require(revisions.all { it.notificationKey in notificationKeys }) {
            "Backup contains orphan revisions"
        }
        val revisionKeys = revisions.mapTo(hashSetOf()) { it.revisionKey }
        require(
            recoveryPending.all {
                it.notificationKey in notificationKeys && it.revisionKey in revisionKeys
            }
        ) {
            "Recovery Point contains orphan pending media"
        }
        val ruleIds = rules.mapTo(hashSetOf()) { it.id }
        require(actions.all { it.ruleId in ruleIds }) {
            "Backup contains orphan rule actions"
        }
    }

    private fun readLimited(uri: Uri): ByteArray {
        appContext.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "Unable to open backup" }
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= EncryptedBackupCodec.maxEncryptedBytes) { "Backup too large" }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun notificationJson(n: NotificationEntity) = JSONObject(mapOf(
        "sbnKey" to n.sbnKey,
        "platformKey" to n.platformKey,
        "packageName" to n.packageName,
        "appLabel" to n.appLabel,
        "notificationId" to n.notificationId,
        "tag" to n.tag,
        "groupKey" to n.groupKey,
        "category" to n.category,
        "channelId" to n.channelId,
        "title" to n.title,
        "text" to n.text,
        "bigText" to n.bigText,
        "subText" to n.subText,
        "conversationTitle" to n.conversationTitle,
        "postedAt" to n.postedAt,
        "updatedAt" to n.updatedAt,
        "removedAt" to n.removedAt,
        "removalReason" to n.removalReason,
        "isOngoing" to n.isOngoing,
        "isClearable" to n.isClearable,
        "protected" to n.protected
    ))

    private fun messageJson(m: MessageEntity) = JSONObject(mapOf(
        "messageKey" to m.messageKey,
        "notificationKey" to m.notificationKey,
        "sender" to m.sender,
        "text" to m.text,
        "timestamp" to m.timestamp,
        "mimeType" to m.mimeType,
        "dataUri" to m.dataUri
    ))

    private fun revisionJson(r: NotificationRevisionEntity) = JSONObject(mapOf(
        "revisionKey" to r.revisionKey,
        "notificationKey" to r.notificationKey,
        "capturedAt" to r.capturedAt,
        "title" to r.title,
        "text" to r.text,
        "bigText" to r.bigText,
        "subText" to r.subText,
        "conversationTitle" to r.conversationTitle,
        "contentHash" to r.contentHash
    ))

    private fun recoveryPendingJson(p: MediaRecoveryPendingEntity) = JSONObject(mapOf(
        "revisionKey" to p.revisionKey,
        "notificationKey" to p.notificationKey,
        "packageName" to p.packageName,
        "postedAt" to p.postedAt,
        "capturedAt" to p.capturedAt,
        "createdAt" to p.createdAt,
        "expiresAt" to p.expiresAt,
        "baselineGeneration" to p.baselineGeneration,
        "mediaStoreVersion" to p.mediaStoreVersion,
        "referencePerceptualHash" to p.referencePerceptualHash,
        "attempts" to p.attempts,
        "lastAttemptAt" to p.lastAttemptAt
    ))

    private fun recoveryRescueJson(r: MediaRescueEntity) = JSONObject(mapOf(
        "rescueKey" to r.rescueKey,
        "sourceKey" to r.sourceKey,
        "packageName" to r.packageName,
        "sourceKind" to r.sourceKind,
        "localPath" to r.localPath,
        "sourceUri" to r.sourceUri,
        "mediaTimestamp" to r.mediaTimestamp,
        "observedAt" to r.observedAt,
        "mimeType" to r.mimeType,
        "width" to r.width,
        "height" to r.height,
        "sizeBytes" to r.sizeBytes,
        "perceptualHash" to r.perceptualHash,
        "confidence" to r.confidence
    ))

    private fun ruleJson(r: RuleEntity) = JSONObject(mapOf(
        "id" to r.id,
        "name" to r.name,
        "enabled" to r.enabled,
        "packageName" to r.packageName,
        "senderQuery" to r.senderQuery,
        "textQuery" to r.textQuery,
        "matchMode" to r.matchMode,
        "timeStartMinutes" to r.timeStartMinutes,
        "timeEndMinutes" to r.timeEndMinutes,
        "screenState" to r.screenState,
        "priority" to r.priority,
        "createdAt" to r.createdAt
    ))

    private fun ruleActionJson(a: RuleActionEntity) = JSONObject(mapOf(
        "id" to a.id,
        "ruleId" to a.ruleId,
        "actionType" to a.actionType,
        "actionValue" to a.actionValue
    ))

    private fun criticalJson(c: CriticalPatternEntity) = JSONObject(mapOf(
        "id" to c.id,
        "type" to c.type,
        "value" to c.value,
        "enabled" to c.enabled,
        "createdAt" to c.createdAt
    ))

    private fun criticalAlertJson(c: CriticalAlertEntity) = JSONObject(mapOf(
        "id" to c.id,
        "eventKey" to c.eventKey,
        "sourcePackage" to c.sourcePackage,
        "sourceLabel" to c.sourceLabel,
        "title" to c.title,
        "createdAt" to c.createdAt,
        "updatedAt" to c.updatedAt,
        "status" to c.status,
        "escalationStep" to c.escalationStep,
        "nextAt" to c.nextAt
    ))

    private fun followUpJson(f: FollowUpEntity) = JSONObject(mapOf(
        "id" to f.id,
        "notificationKey" to f.notificationKey,
        "sourcePackage" to f.sourcePackage,
        "sourceLabel" to f.sourceLabel,
        "title" to f.title,
        "body" to f.body,
        "dueAt" to f.dueAt,
        "status" to f.status,
        "repeatMinutes" to f.repeatMinutes,
        "createdAt" to f.createdAt,
        "updatedAt" to f.updatedAt
    ))

    private fun pickupJson(p: PickupCodeEntity) = JSONObject(mapOf(
        "id" to p.id,
        "code" to p.code,
        "sourcePackage" to p.sourcePackage,
        "sourceLabel" to p.sourceLabel,
        "notificationKey" to p.notificationKey,
        "contextText" to p.contextText,
        "createdAt" to p.createdAt,
        "expiresAt" to p.expiresAt,
        "dismissed" to p.dismissed
    ))

    private fun luminousProfileJson(p: LuminousProfileEntity) = JSONObject(mapOf(
        "id" to p.id,
        "name" to p.name,
        "enabled" to p.enabled,
        "packageName" to p.packageName,
        "senderQuery" to p.senderQuery,
        "colorHex" to p.colorHex,
        "flashEnabled" to p.flashEnabled,
        "overlayEnabled" to p.overlayEnabled,
        "strobeCycles" to p.strobeCycles,
        "strobeSpeedMs" to p.strobeSpeedMs,
        "circleThickness" to p.circleThickness.toDouble(),
        "circleGlow" to p.circleGlow.toDouble(),
        "pulseSpeedMs" to p.pulseSpeedMs,
        "displayDurationMs" to p.displayDurationMs,
        "priority" to p.priority,
        "createdAt" to p.createdAt
    ))

    private fun settingsJson() = JSONObject()
        .put("monitoredPackages", JSONArray(settings.monitoredPackages.toList()))
        .put(
            "savedVaultFilters",
            JSONArray(
                settings.savedVaultFilters.map {
                    com.riccardopinato.notificationcontrol.data.SavedVaultFilterCodec.encode(it)
                }
            )
        )
        .put("retentionDays", settings.retentionDays)
        .put(
            "retentionDaysPerApp",
            JSONObject().apply {
                settings.retentionDaysPerApp.forEach { (packageName, days) ->
                    put(packageName, days)
                }
            }
        )
        .put("vaultMaxBytes", settings.vaultMaxBytes)
        .put("flashEnabled", settings.flashEnabled)
        .put("overlayEnabled", settings.overlayEnabled)
        .put("batteryGuardEnabled", settings.batteryGuardEnabled)
        .put("batteryGuardThreshold", settings.batteryGuardThreshold)
        .put("quietHoursEnabled", settings.quietHoursEnabled)
        .put("quietStartMinutes", settings.quietStartMinutes)
        .put("quietEndMinutes", settings.quietEndMinutes)
        .put(
            "additionalQuietHours",
            JSONArray().apply {
                settings.additionalQuietHours.forEach { band ->
                    put(
                        JSONObject()
                            .put("startMinutes", band.startMinutes)
                            .put("endMinutes", band.endMinutes)
                    )
                }
            }
        )
        .put(
            "quietHoursExceptionPackages",
            JSONArray(settings.quietHoursExceptionPackages.toList())
        )
        .put("screenOffOnly", settings.screenOffOnly)
        .put("strobeSpeedMs", settings.strobeSpeedMs)
        .put("strobeCycles", settings.strobeCycles)
        .put("circleColorHex", settings.circleColorHex)
        .put("circleThickness", settings.circleThickness.toDouble())
        .put("circleGlow", settings.circleGlow.toDouble())
        .put("pulseSpeedMs", settings.pulseSpeedMs)
        .put("sensitiveProtectionEnabled", settings.sensitiveProtectionEnabled)
        .put("pausePingEnabled", settings.pausePingEnabled)
        .put("pausePingCooldownSeconds", settings.pausePingCooldownSeconds)
        .put("pausePingBudgetEnabled", settings.pausePingBudgetEnabled)
        .put("pausePingBudgetMaxAlerts", settings.pausePingBudgetMaxAlerts)
        .put("pausePingBudgetWindowMinutes", settings.pausePingBudgetWindowMinutes)
        .put(
            "pausePingPerAppCooldowns",
            JSONObject().apply {
                settings.pausePingPerAppCooldowns.forEach { (packageName, seconds) ->
                    put(packageName, seconds)
                }
            }
        )
        .put("criticalBypassQuietHours", settings.criticalBypassQuietHours)

    private fun restoreSettings(o: JSONObject) {
        val packages = o.optJSONArray("monitoredPackages")
        if (packages != null) {
            val restored = buildList {
                for (i in 0 until packages.length()) add(packages.getString(i))
            }
            settings.monitoredPackages = if (settings.isPremium) {
                restored.toSet()
            } else {
                restored.take(ProductLimits.FREE_MONITORED_APPS).toSet()
            }
        }

        o.optJSONArray("savedVaultFilters")?.let { savedFilters ->
            settings.savedVaultFilters = buildList {
                for (i in 0 until savedFilters.length()) {
                    com.riccardopinato.notificationcontrol.data.SavedVaultFilterCodec
                        .decode(savedFilters.optString(i))
                        ?.let(::add)
                }
            }
        }

        settings.retentionDays = if (settings.isPremium) {
            o.optInt("retentionDays", settings.retentionDays)
        } else {
            ProductLimits.FREE_RETENTION_DAYS
        }
        if (settings.isPremium) {
            val perAppRetention = o.optJSONObject("retentionDaysPerApp")
            if (perAppRetention != null) {
                settings.retentionDaysPerApp = buildMap {
                    val keys = perAppRetention.keys()
                    while (keys.hasNext()) {
                        val packageName = keys.next()
                        put(
                            packageName,
                            perAppRetention.optInt(
                                packageName,
                                settings.retentionDays
                            )
                        )
                    }
                }
            }
            settings.vaultMaxBytes =
                o.optLong("vaultMaxBytes", settings.vaultMaxBytes)
        }
        settings.flashEnabled = o.optBoolean("flashEnabled", settings.flashEnabled)
        settings.overlayEnabled = o.optBoolean("overlayEnabled", settings.overlayEnabled)
        settings.batteryGuardEnabled =
            o.optBoolean("batteryGuardEnabled", settings.batteryGuardEnabled)
        settings.batteryGuardThreshold =
            o.optInt("batteryGuardThreshold", settings.batteryGuardThreshold)
        settings.quietHoursEnabled =
            o.optBoolean("quietHoursEnabled", settings.quietHoursEnabled)
        settings.quietStartMinutes =
            o.optInt("quietStartMinutes", settings.quietStartMinutes)
        settings.quietEndMinutes =
            o.optInt("quietEndMinutes", settings.quietEndMinutes)
        if (settings.isPremium) {
            val bands = o.optJSONArray("additionalQuietHours")
            if (bands != null) {
                settings.additionalQuietHours = buildList {
                    for (i in 0 until bands.length()) {
                        val band = bands.optJSONObject(i) ?: continue
                        add(
                            com.riccardopinato.notificationcontrol.data.QuietHoursBand(
                                startMinutes = band.optInt("startMinutes", 0),
                                endMinutes = band.optInt("endMinutes", 0)
                            )
                        )
                    }
                }
            }
            val exceptions = o.optJSONArray("quietHoursExceptionPackages")
            if (exceptions != null) {
                settings.quietHoursExceptionPackages = buildSet {
                    for (i in 0 until exceptions.length()) {
                        val packageName = exceptions.optString(i)
                        if (packageName.isNotBlank()) add(packageName)
                    }
                }
            }
        }
        settings.screenOffOnly = o.optBoolean("screenOffOnly", settings.screenOffOnly)
        settings.strobeSpeedMs = o.optLong("strobeSpeedMs", settings.strobeSpeedMs)
        settings.strobeCycles = o.optInt("strobeCycles", settings.strobeCycles)
        settings.circleColorHex = o.optString("circleColorHex", settings.circleColorHex)
        settings.circleThickness =
            o.optDouble("circleThickness", settings.circleThickness.toDouble()).toFloat()
        settings.circleGlow =
            o.optDouble("circleGlow", settings.circleGlow.toDouble()).toFloat()
        settings.pulseSpeedMs = o.optLong("pulseSpeedMs", settings.pulseSpeedMs)
        settings.sensitiveProtectionEnabled =
            o.optBoolean(
                "sensitiveProtectionEnabled",
                settings.sensitiveProtectionEnabled
            )
        settings.pausePingEnabled =
            o.optBoolean("pausePingEnabled", settings.pausePingEnabled)
        settings.pausePingCooldownSeconds =
            o.optInt("pausePingCooldownSeconds", settings.pausePingCooldownSeconds)
        if (settings.isPremium) {
            settings.pausePingBudgetEnabled =
                o.optBoolean("pausePingBudgetEnabled", settings.pausePingBudgetEnabled)
            settings.pausePingBudgetMaxAlerts =
                o.optInt("pausePingBudgetMaxAlerts", settings.pausePingBudgetMaxAlerts)
            settings.pausePingBudgetWindowMinutes =
                o.optInt(
                    "pausePingBudgetWindowMinutes",
                    settings.pausePingBudgetWindowMinutes
                )
            val perApp = o.optJSONObject("pausePingPerAppCooldowns")
            if (perApp != null) {
                val restored = buildMap<String, Int> {
                    val keys = perApp.keys()
                    while (keys.hasNext()) {
                        val packageName = keys.next()
                        put(packageName, perApp.optInt(packageName, 0))
                    }
                }
                settings.pausePingPerAppCooldowns = restored
            }
        }
        settings.criticalBypassQuietHours =
            o.optBoolean("criticalBypassQuietHours", settings.criticalBypassQuietHours)
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key)) null else getLong(key)

    private fun JSONObject.intOrNull(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    companion object {
        @Volatile
        internal var restoreTestHook: ((String) -> Unit)? = null

        private val recoveryMutex = Mutex()

        private const val FORMAT_VERSION = 1
        private const val MAX_MEDIA_BYTES = 6 * 1024 * 1024
        private const val RECOVERY_MEDIA_NOTIFICATION = "notification"
        private const val RECOVERY_MEDIA_REVISION = "revision"
        private const val RECOVERY_MEDIA_RESCUE = "rescue"
        private val RECOVERY_OPERATION_ID =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private val RECOVERY_MEDIA_DIRECTORY =
            Regex("^media-[0-9a-fA-F-]{36}$")
        private val RECOVERY_MEDIA_FILE =
            Regex("^[0-9]{5}\\.bin$")
    }
}

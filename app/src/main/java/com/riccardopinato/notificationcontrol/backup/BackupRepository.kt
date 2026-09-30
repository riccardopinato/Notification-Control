package com.riccardopinato.notificationcontrol.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.CriticalAlertEntity
import com.riccardopinato.notificationcontrol.data.CriticalPatternEntity
import com.riccardopinato.notificationcontrol.data.FollowUpEntity
import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationFtsEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity
import com.riccardopinato.notificationcontrol.data.PickupCodeEntity
import com.riccardopinato.notificationcontrol.data.RuleActionEntity
import com.riccardopinato.notificationcontrol.data.RuleEntity
import com.riccardopinato.notificationcontrol.domain.ProductLimits
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class BackupSummary(
    val notifications: Int,
    val rules: Int,
    val followUps: Int
)

class BackupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = NotificationDatabase.get(appContext)
    private val backupDao = database.backupDao()
    private val notificationDao = database.notificationDao()
    private val settings = AppSettings(appContext)
    private val mediaStore = NotificationMediaStore(appContext)

    suspend fun exportTo(uri: Uri, passphrase: CharArray): Result<BackupSummary> = runCatching {
        val notifications = backupDao.allNotifications()
        val messages = backupDao.allMessages()
        val revisions = backupDao.allRevisions()
        val rules = backupDao.allRules()
        val actions = backupDao.allRuleActions()
        val critical = backupDao.allCriticalPatterns()
        val criticalAlerts = backupDao.allCriticalAlerts()
        val followUps = backupDao.allFollowUps()
        val pickupCodes = backupDao.allPickupCodes()
        val luminousProfiles = database.luminousProfileDao().allProfiles()

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
                notifications.forEach { notification ->
                    val bytes = mediaStore.read(notification.thumbnailPath) ?: return@forEach
                    put(
                        JSONObject()
                            .put("notificationKey", notification.sbnKey)
                            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    )
                }
            })
            .put("settings", settingsJson())

        val plain = root.toString().toByteArray(Charsets.UTF_8)
        val encrypted = EncryptedBackupCodec.encrypt(plain, passphrase)
        check(encrypted.size <= EncryptedBackupCodec.maxEncryptedBytes) {
            "Backup exceeds supported size"
        }

        appContext.contentResolver.openOutputStream(uri, "w").use { output ->
            checkNotNull(output) { "Unable to open destination" }
            output.write(encrypted)
            output.flush()
        }

        BackupSummary(
            notifications = notifications.size,
            rules = rules.size,
            followUps = followUps.size
        )
    }

    suspend fun restoreFrom(uri: Uri, passphrase: CharArray): Result<BackupSummary> = runCatching {
        val encrypted = readLimited(uri)
        val plain = EncryptedBackupCodec.decrypt(encrypted, passphrase)
        val root = JSONObject(plain.toString(Charsets.UTF_8))
        require(root.getInt("format") == FORMAT_VERSION) { "Unsupported backup version" }

        val mediaPayloads = parseMedia(root.getJSONArray("media"))
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
        val settingsObject = root.optJSONObject("settings")

        validateReferences(notifications, messages, revisions, rules, actions)

        val previousMedia = notificationDao.allThumbnailPaths().toSet()
        val notificationsWithMedia = notifications.map { notification ->
            notification.copy(
                thumbnailPath = mediaPayloads[notification.sbnKey]
                    ?.let { mediaStore.restorePicture(notification.sbnKey, it) }
            )
        }
        val restoredMedia = notificationsWithMedia.mapNotNull { it.thumbnailPath }.toSet()

        try {
            database.withTransaction {
                database.luminousProfileDao().deleteAll()
                backupDao.deletePickupCodes()
                backupDao.deleteFollowUps()
                backupDao.deleteCriticalAlerts()
                backupDao.deleteCriticalPatterns()
                backupDao.deleteRuleActions()
                backupDao.deleteRules()
                backupDao.deleteFts()
                backupDao.deleteRevisions()
                backupDao.deleteMessages()
                backupDao.deleteNotifications()

                if (notificationsWithMedia.isNotEmpty()) {
                    backupDao.insertNotifications(notificationsWithMedia)
                }
                if (messages.isNotEmpty()) backupDao.insertMessages(messages)
                if (revisions.isNotEmpty()) backupDao.insertRevisions(revisions)
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

                val messagesByNotification = messages.groupBy { it.notificationKey }
                notificationsWithMedia.forEach { notification ->
                    notificationDao.insertFts(
                        NotificationFtsEntity(
                            sbnKey = notification.sbnKey,
                            appLabel = notification.appLabel,
                            title = notification.title,
                            text = notification.text,
                            bigText = notification.bigText,
                            conversationTitle = notification.conversationTitle,
                            messagesText = messagesByNotification[notification.sbnKey]
                                .orEmpty()
                                .joinToString(" ") { "${it.sender.orEmpty()} ${it.text}" }
                        )
                    )
                }
            }
        } catch (error: Throwable) {
            restoredMedia.filterNot { it in previousMedia }.forEach(mediaStore::delete)
            throw error
        }

        previousMedia.filterNot { it in restoredMedia }.forEach(mediaStore::delete)
        mediaStore.cleanupOrphans(restoredMedia)
        settingsObject?.let(::restoreSettings)

        BackupSummary(
            notifications = notificationsWithMedia.size,
            rules = rules.size,
            followUps = followUps.size
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

    private fun validateReferences(
        notifications: List<NotificationEntity>,
        messages: List<MessageEntity>,
        revisions: List<NotificationRevisionEntity>,
        rules: List<RuleEntity>,
        actions: List<RuleActionEntity>
    ) {
        val notificationKeys = notifications.mapTo(hashSetOf()) { it.sbnKey }
        require(messages.all { it.notificationKey in notificationKeys }) {
            "Backup contains orphan messages"
        }
        require(revisions.all { it.notificationKey in notificationKeys }) {
            "Backup contains orphan revisions"
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

    private fun ruleJson(r: RuleEntity) = JSONObject(mapOf(
        "id" to r.id,
        "name" to r.name,
        "enabled" to r.enabled,
        "packageName" to r.packageName,
        "senderQuery" to r.senderQuery,
        "textQuery" to r.textQuery,
        "matchMode" to r.matchMode,
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
        .put("retentionDays", settings.retentionDays)
        .put("flashEnabled", settings.flashEnabled)
        .put("overlayEnabled", settings.overlayEnabled)
        .put("batteryGuardEnabled", settings.batteryGuardEnabled)
        .put("batteryGuardThreshold", settings.batteryGuardThreshold)
        .put("quietHoursEnabled", settings.quietHoursEnabled)
        .put("quietStartMinutes", settings.quietStartMinutes)
        .put("quietEndMinutes", settings.quietEndMinutes)
        .put("screenOffOnly", settings.screenOffOnly)
        .put("strobeSpeedMs", settings.strobeSpeedMs)
        .put("strobeCycles", settings.strobeCycles)
        .put("circleColorHex", settings.circleColorHex)
        .put("circleThickness", settings.circleThickness.toDouble())
        .put("circleGlow", settings.circleGlow.toDouble())
        .put("pulseSpeedMs", settings.pulseSpeedMs)
        .put("pausePingEnabled", settings.pausePingEnabled)
        .put("pausePingCooldownSeconds", settings.pausePingCooldownSeconds)
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

        settings.retentionDays = if (settings.isPremium) {
            o.optInt("retentionDays", settings.retentionDays)
        } else {
            ProductLimits.FREE_RETENTION_DAYS
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
        settings.screenOffOnly = o.optBoolean("screenOffOnly", settings.screenOffOnly)
        settings.strobeSpeedMs = o.optLong("strobeSpeedMs", settings.strobeSpeedMs)
        settings.strobeCycles = o.optInt("strobeCycles", settings.strobeCycles)
        settings.circleColorHex = o.optString("circleColorHex", settings.circleColorHex)
        settings.circleThickness =
            o.optDouble("circleThickness", settings.circleThickness.toDouble()).toFloat()
        settings.circleGlow =
            o.optDouble("circleGlow", settings.circleGlow.toDouble()).toFloat()
        settings.pulseSpeedMs = o.optLong("pulseSpeedMs", settings.pulseSpeedMs)
        settings.pausePingEnabled =
            o.optBoolean("pausePingEnabled", settings.pausePingEnabled)
        settings.pausePingCooldownSeconds =
            o.optInt("pausePingCooldownSeconds", settings.pausePingCooldownSeconds)
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
        private const val FORMAT_VERSION = 1
        private const val MAX_MEDIA_BYTES = 2 * 1024 * 1024
    }
}

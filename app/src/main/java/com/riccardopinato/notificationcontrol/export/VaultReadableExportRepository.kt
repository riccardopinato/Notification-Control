package com.riccardopinato.notificationcontrol.export

import android.content.Context
import android.net.Uri
import android.util.JsonWriter
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class VaultExportResult(
    val notifications: Int,
    val messages: Int,
    val revisions: Int
)

class VaultReadableExportRepository(context: Context) {
    private val appContext = context.applicationContext
    private val settings by lazy { AppSettings(appContext) }
    private val dao by lazy { NotificationDatabase.get(appContext).notificationDao() }

    suspend fun exportCsv(uri: Uri): Result<VaultExportResult> = runCatching {
        val eventKeys = dao.eventKeysForExport()
        var notificationCount = 0
        var messageCount = 0

        appContext.contentResolver.openOutputStream(uri, "wt").use { output ->
            checkNotNull(output) { "Unable to open export destination" }
            BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                writer.appendLine(
                    listOf(
                        "captured_at",
                        "app",
                        "package",
                        "conversation",
                        "title",
                        "content",
                        "messages",
                        "protected",
                        "removed_at"
                    ).joinToString(",")
                )

                eventKeys.forEach { eventKey ->
                    val notification = dao.findByKey(eventKey) ?: return@forEach
                    val messages = dao.messagesFor(eventKey)
                    val messageText = messages.joinToString("\n") { message ->
                        val sender = message.sender?.takeIf(String::isNotBlank)
                        if (sender == null) {
                            message.text
                        } else {
                            sender + ": " + message.text
                        }
                    }

                    writer.appendLine(
                        listOf(
                            VaultReadableExportFormatter.timestamp(notification.updatedAt),
                            notification.appLabel,
                            notification.packageName,
                            notification.conversationTitle.orEmpty(),
                            notification.title.orEmpty(),
                            notification.bigText?.takeIf(String::isNotBlank)
                                ?: notification.text.orEmpty(),
                            messageText,
                            notification.protected.toString(),
                            VaultReadableExportFormatter.timestamp(notification.removedAt)
                        ).joinToString(",") {
                            VaultReadableExportFormatter.csvCell(it)
                        }
                    )
                    notificationCount += 1
                    messageCount += messages.size
                }
            }
        }

        VaultExportResult(
            notifications = notificationCount,
            messages = messageCount,
            revisions = 0
        )
    }

    suspend fun exportJson(uri: Uri): Result<VaultExportResult> = runCatching {
        check(settings.isPremium) { "Premium is required for structured JSON export" }
        val eventKeys = dao.eventKeysForExport()
        var notificationCount = 0
        var messageCount = 0
        var revisionCount = 0

        appContext.contentResolver.openOutputStream(uri, "wt").use { output ->
            checkNotNull(output) { "Unable to open export destination" }
            JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                writer.setIndent("  ")
                writer.beginObject()
                writer.name("formatVersion").value(1)
                writer.name("exportedAt").value(System.currentTimeMillis())
                writer.name("notifications")
                writer.beginArray()

                eventKeys.forEach { eventKey ->
                    val notification = dao.findByKey(eventKey) ?: return@forEach
                    val messages = dao.messagesFor(eventKey)
                    val revisions = dao.revisionsFor(eventKey)
                    writeNotificationJson(
                        writer = writer,
                        notification = notification,
                        messages = messages,
                        revisions = revisions
                    )
                    notificationCount += 1
                    messageCount += messages.size
                    revisionCount += revisions.size
                }

                writer.endArray()
                writer.endObject()
            }
        }

        VaultExportResult(
            notifications = notificationCount,
            messages = messageCount,
            revisions = revisionCount
        )
    }

    private fun writeNotificationJson(
        writer: JsonWriter,
        notification: NotificationEntity,
        messages: List<MessageEntity>,
        revisions: List<NotificationRevisionEntity>
    ) {
        writer.beginObject()
        writer.name("eventId").value(notification.sbnKey)
        writer.name("packageName").value(notification.packageName)
        writer.name("appLabel").value(notification.appLabel)
        writer.nullableString("conversationTitle", notification.conversationTitle)
        writer.nullableString("title", notification.title)
        writer.nullableString("text", notification.text)
        writer.nullableString("bigText", notification.bigText)
        writer.nullableString("subText", notification.subText)
        writer.name("postedAt").value(notification.postedAt)
        writer.name("updatedAt").value(notification.updatedAt)
        writer.nullableLong("removedAt", notification.removedAt)
        writer.name("protected").value(notification.protected)
        writer.name("messages")
        writer.beginArray()
        messages.forEach { message ->
            writer.beginObject()
            writer.nullableString("sender", message.sender)
            writer.name("text").value(message.text)
            writer.name("timestamp").value(message.timestamp)
            writer.nullableString("mimeType", message.mimeType)
            writer.endObject()
        }
        writer.endArray()
        writer.name("revisions")
        writer.beginArray()
        revisions.forEach { revision ->
            writer.beginObject()
            writer.name("capturedAt").value(revision.capturedAt)
            writer.nullableString("title", revision.title)
            writer.nullableString("text", revision.text)
            writer.nullableString("bigText", revision.bigText)
            writer.nullableString("subText", revision.subText)
            writer.nullableString("conversationTitle", revision.conversationTitle)
            writer.endObject()
        }
        writer.endArray()
        writer.endObject()
    }

    private fun JsonWriter.nullableString(fieldName: String, fieldValue: String?) {
        this.name(fieldName)
        if (fieldValue == null) this.nullValue() else this.value(fieldValue)
    }

    private fun JsonWriter.nullableLong(fieldName: String, fieldValue: Long?) {
        this.name(fieldName)
        if (fieldValue == null) this.nullValue() else this.value(fieldValue)
    }
}

internal object VaultReadableExportFormatter {
    private val utcFormatter = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.ROOT
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Synchronized
    fun timestamp(value: Long?): String =
        value?.let { utcFormatter.format(Date(it)) }.orEmpty()

    fun csvCell(value: String?): String {
        val quote = '"'
        val raw = value.orEmpty()
        val safe = if (raw.firstOrNull() in FORMULA_PREFIXES) {
            "'" + raw
        } else {
            raw
        }
        return quote.toString() +
            safe.replace(
                quote.toString(),
                quote.toString() + quote
            ) +
            quote
    }

    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@')
}

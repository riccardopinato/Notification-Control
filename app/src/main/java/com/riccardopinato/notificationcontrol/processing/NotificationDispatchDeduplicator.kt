package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import java.security.MessageDigest
import java.util.LinkedHashMap

class NotificationDispatchDeduplicator(
    private val maxEntries: Int = 512
) {
    private val fingerprints = object : LinkedHashMap<String, String>(
        maxEntries,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, String>?
        ): Boolean = size > maxEntries
    }

    @Synchronized
    fun shouldDispatch(event: CapturedNotification): Boolean {
        val fingerprint = fingerprint(event)
        val previous = fingerprints.put(event.sbnKey, fingerprint)
        return previous != fingerprint
    }

    @Synchronized
    fun clear(platformKey: String) {
        fingerprints.remove(platformKey)
    }

    private fun fingerprint(event: CapturedNotification): String {
        val value = buildString {
            append(event.packageName)
            append('\u0000')
            append(event.notificationId)
            append('\u0000')
            append(event.tag.orEmpty())
            append('\u0000')
            append(event.title.orEmpty())
            append('\u0000')
            append(event.text.orEmpty())
            append('\u0000')
            append(event.bigText.orEmpty())
            append('\u0000')
            append(event.subText.orEmpty())
            append('\u0000')
            append(event.conversationTitle.orEmpty())
            event.messages.forEach {
                append('\u0001')
                append(it.timestamp)
                append('\u0000')
                append(it.sender.orEmpty())
                append('\u0000')
                append(it.text)
                append('\u0000')
                append(it.mimeType.orEmpty())
                append('\u0000')
                append(it.dataUri.orEmpty())
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

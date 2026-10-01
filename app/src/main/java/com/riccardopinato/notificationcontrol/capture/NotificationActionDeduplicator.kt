package com.riccardopinato.notificationcontrol.capture

import java.util.concurrent.ConcurrentHashMap

class NotificationActionDeduplicator(
    private val maxEntries: Int = 512
) {
    private val fingerprints = ConcurrentHashMap<String, String>()

    fun shouldDispatch(event: CapturedNotification): Boolean {
        val hash = NotificationFingerprint.contentHash(event)
        val previous = fingerprints.put(event.sbnKey, hash)
        trimIfNeeded()
        return previous != hash
    }

    fun record(event: CapturedNotification) {
        fingerprints[event.sbnKey] = NotificationFingerprint.contentHash(event)
        trimIfNeeded()
    }

    fun clear(platformKey: String) {
        fingerprints.remove(platformKey)
    }

    private fun trimIfNeeded() {
        if (fingerprints.size <= maxEntries) return
        val removeCount = fingerprints.size - maxEntries
        fingerprints.keys.take(removeCount).forEach(fingerprints::remove)
    }
}

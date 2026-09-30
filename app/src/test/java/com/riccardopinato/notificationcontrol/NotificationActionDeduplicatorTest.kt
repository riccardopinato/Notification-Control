package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.NotificationActionDeduplicator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionDeduplicatorTest {
    @Test
    fun identicalUpdateDispatchesOnlyOnceUntilRemoval() {
        val deduplicator = NotificationActionDeduplicator()

        assertTrue(deduplicator.shouldDispatch(event("same")))
        assertFalse(deduplicator.shouldDispatch(event("same")))

        deduplicator.clear("platform-key")
        assertTrue(deduplicator.shouldDispatch(event("same")))
    }

    @Test
    fun contentChangeDispatchesAgain() {
        val deduplicator = NotificationActionDeduplicator()
        assertTrue(deduplicator.shouldDispatch(event("first")))
        assertTrue(deduplicator.shouldDispatch(event("changed")))
    }

    private fun event(text: String) = CapturedNotification(
        sbnKey = "platform-key",
        packageName = "com.example",
        appLabel = "Example",
        notificationId = 1,
        tag = null,
        groupKey = null,
        category = null,
        channelId = null,
        title = "Title",
        text = text,
        bigText = null,
        subText = null,
        conversationTitle = null,
        thumbnailPath = null,
        postedAt = 10L,
        capturedAt = 20L,
        isOngoing = false,
        isClearable = true,
        messages = emptyList()
    )
}

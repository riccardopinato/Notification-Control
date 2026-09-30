package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.processing.NotificationDispatchDeduplicator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDispatchDeduplicatorTest {
    private val deduplicator = NotificationDispatchDeduplicator()

    @Test
    fun identicalPostedContentDispatchesOnlyOnceUntilRemoval() {
        val event = event("hello")
        assertTrue(deduplicator.shouldDispatch(event))
        assertFalse(deduplicator.shouldDispatch(event))
        deduplicator.clear(event.sbnKey)
        assertTrue(deduplicator.shouldDispatch(event))
    }

    @Test
    fun changedContentDispatchesAgain() {
        assertTrue(deduplicator.shouldDispatch(event("hello")))
        assertTrue(deduplicator.shouldDispatch(event("updated")))
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
        postedAt = 1L,
        capturedAt = 2L,
        isOngoing = false,
        isClearable = true,
        messages = emptyList()
    )
}

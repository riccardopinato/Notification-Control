package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.toRevisionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CaptureMapperTest {
    private fun captured(text: String) = CapturedNotification(
        sbnKey = "key",
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

    @Test
    fun sameContentProducesSameRevisionKey() {
        assertEquals(
            captured("hello").toRevisionEntity().revisionKey,
            captured("hello").toRevisionEntity().revisionKey
        )
    }

    @Test
    fun changedContentProducesNewRevision() {
        assertNotEquals(
            captured("hello").toRevisionEntity().revisionKey,
            captured("changed").toRevisionEntity().revisionKey
        )
    }
}

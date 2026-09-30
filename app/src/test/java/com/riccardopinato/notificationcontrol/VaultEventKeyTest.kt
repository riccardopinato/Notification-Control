package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.VaultEventKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VaultEventKeyTest {
    @Test
    fun keyIsStableForSameCapture() {
        val event = event(postedAt = 100L, capturedAt = 200L)
        assertEquals(VaultEventKey.create(event), VaultEventKey.create(event))
    }

    @Test
    fun reusedPlatformKeyCanProduceDifferentVaultEvent() {
        assertNotEquals(
            VaultEventKey.create(event(postedAt = 100L, capturedAt = 200L)),
            VaultEventKey.create(event(postedAt = 300L, capturedAt = 400L))
        )
    }

    private fun event(postedAt: Long, capturedAt: Long) = CapturedNotification(
        sbnKey = "same-platform-key",
        packageName = "com.example",
        appLabel = "Example",
        notificationId = 1,
        tag = null,
        groupKey = null,
        category = null,
        channelId = null,
        title = "Title",
        text = "Text",
        bigText = null,
        subText = null,
        conversationTitle = null,
        thumbnailPath = null,
        postedAt = postedAt,
        capturedAt = capturedAt,
        isOngoing = false,
        isClearable = true,
        messages = emptyList()
    )
}

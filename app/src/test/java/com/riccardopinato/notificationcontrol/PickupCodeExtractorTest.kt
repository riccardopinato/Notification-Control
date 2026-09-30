package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.domain.PickupCodeExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickupCodeExtractorTest {
    private fun event(text: String) = CapturedNotification(
        sbnKey = "key",
        packageName = "com.example",
        appLabel = "Example",
        notificationId = 1,
        tag = null,
        groupKey = null,
        category = null,
        channelId = null,
        title = "Delivery",
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

    @Test
    fun extractsCodeWhenPickupContextExists() {
        assertEquals(
            "A7B92",
            PickupCodeExtractor.extract(event("Locker pickup code A7B92"))?.code
        )
    }

    @Test
    fun ignoresUnrelatedNumbers() {
        assertNull(PickupCodeExtractor.extract(event("Your balance is 12345")))
    }
}

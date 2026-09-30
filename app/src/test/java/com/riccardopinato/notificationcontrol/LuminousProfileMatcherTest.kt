package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.CapturedMessage
import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity
import com.riccardopinato.notificationcontrol.luminous.LuminousProfileMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuminousProfileMatcherTest {
    private fun event(
        packageName: String = "com.example.chat",
        title: String? = "Anna",
        sender: String? = "Anna"
    ) = CapturedNotification(
        sbnKey = "key",
        packageName = packageName,
        appLabel = "Chat",
        notificationId = 1,
        tag = null,
        groupKey = null,
        category = null,
        channelId = null,
        title = title,
        text = "hello",
        bigText = null,
        subText = null,
        conversationTitle = null,
        thumbnailPath = null,
        postedAt = 1L,
        capturedAt = 2L,
        isOngoing = false,
        isClearable = true,
        messages = listOf(
            CapturedMessage(
                sender = sender,
                text = "hello",
                timestamp = 1L,
                mimeType = null,
                dataUri = null
            )
        )
    )

    @Test
    fun appAndSenderProfileRequiresBothSelectors() {
        val profile = LuminousProfileEntity(
            packageName = "com.example.chat",
            senderQuery = "anna",
            name = "Anna"
        )
        assertTrue(LuminousProfileMatcher.matches(profile, event()))
        assertFalse(
            LuminousProfileMatcher.matches(
                profile,
                event(packageName = "com.other")
            )
        )
    }

    @Test
    fun senderOnlyProfileMatchesTitleOrStructuredSender() {
        val profile = LuminousProfileEntity(
            senderQuery = "anna",
            name = "Anna"
        )
        assertTrue(LuminousProfileMatcher.matches(profile, event()))
        assertFalse(
            LuminousProfileMatcher.matches(
                profile,
                event(title = "Marco", sender = "Marco")
            )
        )
    }

    @Test
    fun selectorlessProfileIsRejectedByMatcher() {
        assertFalse(
            LuminousProfileMatcher.matches(
                LuminousProfileEntity(name = "Invalid"),
                event()
            )
        )
    }
}

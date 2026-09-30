package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.CapturedMessage
import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.CriticalPatternEntity
import com.riccardopinato.notificationcontrol.domain.CriticalPatternMatcher
import com.riccardopinato.notificationcontrol.domain.CriticalPatternType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalPatternMatcherTest {
    @Test
    fun keywordMatchesStructuredMessageText() {
        val pattern = CriticalPatternEntity(
            type = CriticalPatternType.KEYWORD,
            value = "chiamami"
        )
        assertTrue(
            CriticalPatternMatcher.matches(
                pattern,
                event("com.chat", "Anna", "CHIAMAMI appena puoi")
            )
        )
    }

    @Test
    fun senderIsCaseInsensitive() {
        val pattern = CriticalPatternEntity(
            type = CriticalPatternType.SENDER,
            value = "anna"
        )
        assertTrue(
            CriticalPatternMatcher.matches(
                pattern,
                event("com.chat", "ANNA", "hello")
            )
        )
    }

    @Test
    fun appPatternRequiresExactPackage() {
        val pattern = CriticalPatternEntity(
            type = CriticalPatternType.APP,
            value = "com.chat"
        )
        assertTrue(
            CriticalPatternMatcher.matches(
                pattern,
                event("com.chat", "Anna", "hello")
            )
        )
        assertFalse(
            CriticalPatternMatcher.matches(
                pattern,
                event("com.chat.other", "Anna", "hello")
            )
        )
    }

    private fun event(
        packageName: String,
        sender: String,
        text: String
    ) = CapturedNotification(
        sbnKey = "key",
        packageName = packageName,
        appLabel = "Chat",
        notificationId = 1,
        tag = null,
        groupKey = null,
        category = null,
        channelId = null,
        title = sender,
        text = text,
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
                text = text,
                timestamp = 1L,
                mimeType = null,
                dataUri = null
            )
        )
    )
}

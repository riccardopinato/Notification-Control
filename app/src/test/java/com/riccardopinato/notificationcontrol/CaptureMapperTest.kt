package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.CapturedMessage
import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.toMessageEntities
import com.riccardopinato.notificationcontrol.capture.toRevisionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CaptureMapperTest {
    private fun captured(
        text: String,
        messages: List<CapturedMessage> = emptyList()
    ) = CapturedNotification(
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
        messages = messages
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

    @Test
    fun fallbackTimestampChangesDoNotCreateNewRevisionOrMessage() {
        val first = captured(
            "Anna",
            listOf(
                CapturedMessage(
                    sender = null,
                    text = "Anna: ciao",
                    timestamp = 100L,
                    timestampReliable = false
                )
            )
        )
        val replay = captured(
            "Anna",
            listOf(
                CapturedMessage(
                    sender = null,
                    text = "Anna: ciao",
                    timestamp = 900L,
                    timestampReliable = false
                )
            )
        )

        assertEquals(
            first.toRevisionEntity("vault").contentHash,
            replay.toRevisionEntity("vault").contentHash
        )
        assertEquals(
            first.toMessageEntities("vault").single().messageKey,
            replay.toMessageEntities("vault").single().messageKey
        )
    }

    @Test
    fun repeatedFallbackTextKeepsDistinctStableOccurrences() {
        val event = captured(
            "Svezia",
            listOf(
                CapturedMessage(
                    sender = null,
                    text = "Foto",
                    timestamp = 100L,
                    timestampReliable = false
                ),
                CapturedMessage(
                    sender = null,
                    text = "Foto",
                    timestamp = 101L,
                    timestampReliable = false
                )
            )
        )

        val keys = event.toMessageEntities("vault").map { it.messageKey }

        assertEquals(2, keys.distinct().size)
    }

    @Test
    fun structuredMessageTimestampStillParticipatesInIdentity() {
        val first = captured(
            "Anna",
            listOf(CapturedMessage("Anna", "ciao", 100L))
        )
        val second = captured(
            "Anna",
            listOf(CapturedMessage("Anna", "ciao", 200L))
        )

        assertNotEquals(
            first.toMessageEntities("vault").single().messageKey,
            second.toMessageEntities("vault").single().messageKey
        )
    }
}

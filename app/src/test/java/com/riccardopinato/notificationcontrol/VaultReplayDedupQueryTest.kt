package com.riccardopinato.notificationcontrol

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VaultReplayDedupQueryTest {
    private lateinit var database: NotificationDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            NotificationDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exactRecentReplayFindsExistingLogicalEvent() = runBlocking {
        val dao = database.notificationDao()
        dao.upsertNotification(notification())
        dao.insertRevision(revision())

        val found = dao.findRecentEquivalentEvent(
            packageName = "com.whatsapp",
            notificationId = 42,
            tag = "chat-Anna",
            postedAt = 1_000L,
            contentHash = "same-content",
            cutoffMillis = 1_500L
        )

        assertEquals("vault-event-1", found?.sbnKey)
    }

    @Test
    fun staleHistoricRevisionDoesNotCauseReplayCollapse() = runBlocking {
        val dao = database.notificationDao()
        dao.upsertNotification(notification())
        dao.insertRevision(revision())
        dao.insertRevision(
            revision().copy(
                revisionKey = "rev-2",
                capturedAt = 2_500L,
                text = "Changed",
                contentHash = "new-content"
            )
        )

        val found = dao.findRecentEquivalentEvent(
            packageName = "com.whatsapp",
            notificationId = 42,
            tag = "chat-Anna",
            postedAt = 1_000L,
            contentHash = "same-content",
            cutoffMillis = 1_500L
        )

        assertNull(found)
    }

    @Test
    fun differentPostTimeIsNotCollapsed() = runBlocking {
        val dao = database.notificationDao()
        dao.upsertNotification(notification())
        dao.insertRevision(revision())

        val found = dao.findRecentEquivalentEvent(
            packageName = "com.whatsapp",
            notificationId = 42,
            tag = "chat-Anna",
            postedAt = 1_001L,
            contentHash = "same-content",
            cutoffMillis = 1_500L
        )

        assertNull(found)
    }

    private fun notification() = NotificationEntity(
        sbnKey = "vault-event-1",
        platformKey = "platform-old",
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        notificationId = 42,
        tag = "chat-Anna",
        groupKey = "group",
        category = "msg",
        channelId = "messages",
        title = "Anna",
        text = "Svezia",
        bigText = null,
        subText = null,
        conversationTitle = null,
        thumbnailPath = null,
        postedAt = 1_000L,
        updatedAt = 2_000L,
        removedAt = 2_100L,
        removalReason = 2,
        isOngoing = false,
        isClearable = true,
        protected = false
    )

    private fun revision() = NotificationRevisionEntity(
        revisionKey = "rev-1",
        notificationKey = "vault-event-1",
        capturedAt = 2_000L,
        title = "Anna",
        text = "Svezia",
        bigText = null,
        subText = null,
        conversationTitle = null,
        contentHash = "same-content"
    )
}

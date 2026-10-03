package com.riccardopinato.notificationcontrol

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.RestoreJournalEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestoreTransactionSafetyTest {
    private lateinit var database: NotificationDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            NotificationDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun injectedFailureRollsBackVaultAndRecoveryJournal() = runBlocking {
        val backup = database.backupDao()
        backup.insertNotifications(listOf(notification("old")))

        val result = runCatching {
            database.withTransaction {
                backup.deleteNotifications()
                backup.insertNotifications(listOf(notification("new")))
                backup.upsertRestoreJournal(
                    RestoreJournalEntity(
                        sessionId = "restore-session",
                        settingsJson = "{}",
                        createdAt = 123L
                    )
                )
                error("fault-injected-before-commit")
            }
        }

        assertTrue(result.isFailure)
        assertEquals(listOf("old"), backup.allNotifications().map { it.sbnKey })
        assertNull(backup.restoreJournal())
    }

    @Test
    fun committedRestoreKeepsJournalUntilPostCommitRecovery() = runBlocking {
        val backup = database.backupDao()
        backup.insertNotifications(listOf(notification("old")))

        database.withTransaction {
            backup.deleteNotifications()
            backup.insertNotifications(listOf(notification("new")))
            backup.upsertRestoreJournal(
                RestoreJournalEntity(
                    sessionId = "restore-session",
                    settingsJson = """{"retentionDays":30}""",
                    createdAt = 456L
                )
            )
        }

        assertEquals(listOf("new"), backup.allNotifications().map { it.sbnKey })
        assertEquals("restore-session", backup.restoreJournal()?.sessionId)
    }

    private fun notification(key: String) = NotificationEntity(
        sbnKey = key,
        platformKey = "platform-$key",
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
        postedAt = 1L,
        updatedAt = 1L,
        removedAt = null,
        removalReason = null,
        isOngoing = false,
        isClearable = true,
        protected = false
    )
}

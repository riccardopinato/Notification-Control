package com.riccardopinato.notificationcontrol

import android.app.Application
import androidx.room.Room
import com.riccardopinato.notificationcontrol.capture.CapturedMessage
import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.processing.NotificationEventConsumer
import com.riccardopinato.notificationcontrol.processing.NotificationEventProcessor
import com.riccardopinato.notificationcontrol.processing.NotificationVaultRepository
import com.riccardopinato.notificationcontrol.processing.ProcessingContext
import com.riccardopinato.notificationcontrol.processing.ProcessingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationEventProcessorReplayTest {
    private lateinit var app: Application
    private lateinit var database: NotificationDatabase

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(
            app,
            NotificationDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun platformKeyReplayCreatesOneVaultRowAndOneConsumerDispatch() = runBlocking {
        val settings = AppSettings(app).apply {
            monitoredPackages = setOf("com.whatsapp")
        }
        val vault = NotificationVaultRepository(
            settings = settings,
            database = database,
            mediaStore = NotificationMediaStore(app)
        )
        var dispatches = 0
        val processor = NotificationEventProcessor(
            vaultRepository = vault,
            consumers = listOf(
                object : NotificationEventConsumer {
                    override suspend fun consume(
                        event: CapturedNotification,
                        context: ProcessingContext
                    ) {
                        dispatches++
                    }
                }
            )
        )

        val now = System.currentTimeMillis()
        processor.process(
            event(platformKey = "whatsapp-old", capturedAt = now),
            ProcessingMode.POSTED
        )
        processor.markRemoved("whatsapp-old", 2)
        processor.process(
            event(platformKey = "whatsapp-new", capturedAt = now + 100L),
            ProcessingMode.POSTED
        )

        assertEquals(1, database.notificationDao().observeCount().first())
        assertEquals(1, dispatches)
    }

    private fun event(
        platformKey: String,
        capturedAt: Long
    ) = CapturedNotification(
        sbnKey = platformKey,
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        notificationId = 42,
        tag = "chat-Anna",
        groupKey = "whatsapp-group",
        category = "msg",
        channelId = "messages",
        title = "Anna",
        text = "Svezia",
        bigText = null,
        subText = null,
        conversationTitle = "Anna",
        thumbnailPath = null,
        postedAt = capturedAt - 1_000L,
        capturedAt = capturedAt,
        isOngoing = false,
        isClearable = true,
        messages = listOf(
            CapturedMessage(
                sender = "Anna",
                text = "Svezia",
                timestamp = capturedAt - 1_000L
            )
        )
    )
}

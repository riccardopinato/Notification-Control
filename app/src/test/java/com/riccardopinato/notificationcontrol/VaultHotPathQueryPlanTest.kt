package com.riccardopinato.notificationcontrol

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultHotPathQueryPlanTest {
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
    fun largeVaultUsesIndexesForCaptureAndAppPagingHotPaths() = runBlocking {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            val insert = db.compileStatement(
                """
                INSERT INTO notifications(
                    sbnKey, platformKey, packageName, appLabel, notificationId,
                    postedAt, updatedAt, isOngoing, isClearable, protected
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 1, 0)
                """.trimIndent()
            )
            repeat(5_000) { index ->
                insert.clearBindings()
                insert.bindString(1, "event-$index")
                insert.bindString(2, "platform-$index")
                insert.bindString(3, "com.example.${index % 8}")
                insert.bindString(4, "Example ${index % 8}")
                insert.bindLong(5, index.toLong())
                insert.bindLong(6, 1_000_000L + index)
                insert.bindLong(7, 2_000_000L + index)
                insert.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        db.query("SELECT COUNT(*) FROM notifications").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5_000, cursor.getInt(0))
        }

        val activePlan = explain(
            """
            SELECT * FROM notifications
            WHERE platformKey = 'platform-4999' AND removedAt IS NULL
            ORDER BY updatedAt DESC LIMIT 1
            """.trimIndent()
        )
        assertTrue(
            activePlan.any {
                it.contains("index_notifications_platformKey_removedAt_updatedAt") ||
                    it.contains("index_notifications_platformKey")
            }
        )

        val appPagingPlan = explain(
            """
            SELECT * FROM notifications
            WHERE packageName = 'com.example.3'
            ORDER BY updatedAt DESC LIMIT 100
            """.trimIndent()
        )
        assertTrue(
            appPagingPlan.any {
                it.contains("index_notifications_packageName_updatedAt")
            }
        )

        val startedNs = System.nanoTime()
        repeat(100) { iteration ->
            val key = "platform-${4_900 + (iteration % 100)}"
            database.notificationDao().findActiveByPlatformKey(key)
        }
        val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
        println("STEP26_HOT_PATH_PROFILE largeVault=5000 lookups=100 elapsedMs=$elapsedMs")
        assertTrue("Indexed hot-path lookup unexpectedly slow: $elapsedMs ms", elapsedMs < 5_000L)
    }

    private fun explain(sql: String): List<String> {
        val details = mutableListOf<String>()
        database.openHelper.writableDatabase.query(
            SimpleSQLiteQuery("EXPLAIN QUERY PLAN $sql")
        ).use { cursor ->
            val detailIndex = cursor.getColumnIndexOrThrow("detail")
            while (cursor.moveToNext()) {
                details += cursor.getString(detailIndex)
            }
        }
        return details
    }
}

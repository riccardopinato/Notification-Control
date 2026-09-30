package com.riccardopinato.notificationcontrol

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationDatabaseMigrationTest {
    @Test
    fun migration5To6BackfillsPlatformKeyWithoutLosingHistory() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "notification-control-migration-5-6.db"
        context.deleteDatabase(databaseName)

        val v5 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(5) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createNotificationsV5(db)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )

        v5.writableDatabase.execSQL(
            """
            INSERT INTO notifications(
                sbnKey, packageName, appLabel, notificationId, tag, groupKey,
                category, channelId, title, text, bigText, subText,
                conversationTitle, thumbnailPath, postedAt, updatedAt,
                removedAt, removalReason, isOngoing, isClearable, protected
            ) VALUES(
                'legacy-key', 'com.example', 'Example', 7, NULL, NULL,
                NULL, NULL, 'Title', 'Body', NULL, NULL,
                NULL, NULL, 100, 200,
                300, 4, 0, 1, 1
            )
            """.trimIndent()
        )
        v5.close()

        val v6 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createNotificationsV5(db)
                            NotificationDatabase.MIGRATION_5_6.migrate(db)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {
                            assertEquals(5, oldVersion)
                            assertEquals(6, newVersion)
                            NotificationDatabase.MIGRATION_5_6.migrate(db)
                        }
                    }
                )
                .build()
        )

        val db = v6.writableDatabase
        db.query(
            "SELECT sbnKey, platformKey, removedAt, protected " +
                "FROM notifications WHERE sbnKey = 'legacy-key'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy-key", cursor.getString(0))
            assertEquals("legacy-key", cursor.getString(1))
            assertEquals(300L, cursor.getLong(2))
            assertEquals(1, cursor.getInt(3))
        }

        var platformIndexFound = false
        db.query("PRAGMA index_list('notifications')").use { cursor ->
            val nameColumn = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (
                    nameColumn >= 0 &&
                    cursor.getString(nameColumn) == "index_notifications_platformKey"
                ) {
                    platformIndexFound = true
                }
            }
        }
        assertTrue(platformIndexFound)

        v6.close()
        context.deleteDatabase(databaseName)
    }

    private fun createNotificationsV5(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notifications (
                sbnKey TEXT NOT NULL,
                packageName TEXT NOT NULL,
                appLabel TEXT NOT NULL,
                notificationId INTEGER NOT NULL,
                tag TEXT,
                groupKey TEXT,
                category TEXT,
                channelId TEXT,
                title TEXT,
                text TEXT,
                bigText TEXT,
                subText TEXT,
                conversationTitle TEXT,
                thumbnailPath TEXT,
                postedAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                removedAt INTEGER,
                removalReason INTEGER,
                isOngoing INTEGER NOT NULL,
                isClearable INTEGER NOT NULL,
                protected INTEGER NOT NULL,
                PRIMARY KEY(sbnKey)
            )
            """.trimIndent()
        )
    }
}

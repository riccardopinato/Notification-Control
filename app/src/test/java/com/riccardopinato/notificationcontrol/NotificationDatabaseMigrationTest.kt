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
    fun migration5To6BackfillsPlatformKeyWithoutLosingVaultRow() {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-5-6-" + System.nanoTime() + ".db"
        val callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(callback)
                .build()
        )

        val db = helper.writableDatabase
        db.execSQL(
            """
            CREATE TABLE notifications (
                sbnKey TEXT NOT NULL PRIMARY KEY,
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
                protected INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO notifications(
                sbnKey, packageName, appLabel, notificationId,
                postedAt, updatedAt, isOngoing, isClearable, protected
            ) VALUES('platform-key', 'com.example', 'Example', 7, 10, 11, 0, 1, 0)
            """.trimIndent()
        )

        NotificationDatabase.MIGRATION_5_6.migrate(db)

        db.query(
            "SELECT sbnKey, platformKey, packageName FROM notifications"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("platform-key", cursor.getString(0))
            assertEquals("platform-key", cursor.getString(1))
            assertEquals("com.example", cursor.getString(2))
        }

        db.query("PRAGMA index_list('notifications')").use { cursor ->
            var found = false
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "index_notifications_platformKey") {
                    found = true
                }
            }
            assertTrue(found)
        }

        helper.close()
        context.deleteDatabase(name)
    }
}

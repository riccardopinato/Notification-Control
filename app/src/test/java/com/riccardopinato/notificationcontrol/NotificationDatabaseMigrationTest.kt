package com.riccardopinato.notificationcontrol

import android.app.Application
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
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

    @Test
    fun migration6To7CreatesCriticalEscalationStorage() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "notification-control-migration-6-7.db"
        context.deleteDatabase(databaseName)

        val v6 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )
        v6.writableDatabase
        v6.close()

        val v7 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(7) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            NotificationDatabase.MIGRATION_6_7.migrate(db)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {
                            assertEquals(6, oldVersion)
                            assertEquals(7, newVersion)
                            NotificationDatabase.MIGRATION_6_7.migrate(db)
                        }
                    }
                )
                .build()
        )

        val db = v7.writableDatabase
        var tableFound = false
        db.query(
            "SELECT name FROM sqlite_master " +
                "WHERE type='table' AND name='critical_alerts'"
        ).use { cursor ->
            tableFound = cursor.moveToFirst()
        }
        assertTrue(tableFound)

        db.execSQL(
            """
            INSERT INTO critical_alerts(
                eventKey, sourcePackage, sourceLabel, title,
                createdAt, updatedAt, status, escalationStep, nextAt
            ) VALUES(
                'event-1', 'com.example', 'Example', 'Urgent',
                100, 100, 'ACTIVE', 0, 200
            )
            """.trimIndent()
        )
        db.query(
            "SELECT eventKey, status, escalationStep FROM critical_alerts"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("event-1", cursor.getString(0))
            assertEquals("ACTIVE", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }

        v7.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration7To8AddsAdvancedRuleConditionsWithSafeDefaults() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "notification-control-migration-7-8.db"
        context.deleteDatabase(databaseName)

        val v7 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(7) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE rules (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    name TEXT NOT NULL,
                                    enabled INTEGER NOT NULL,
                                    packageName TEXT,
                                    senderQuery TEXT,
                                    textQuery TEXT,
                                    matchMode TEXT NOT NULL,
                                    priority INTEGER NOT NULL,
                                    createdAt INTEGER NOT NULL
                                )
                                """.trimIndent()
                            )
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
        v7.writableDatabase.execSQL(
            """
            INSERT INTO rules(
                name, enabled, packageName, senderQuery, textQuery,
                matchMode, priority, createdAt
            ) VALUES(
                'Legacy rule', 1, 'com.example', NULL, 'urgent',
                'ALL', 0, 100
            )
            """.trimIndent()
        )
        v7.close()

        val v8 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(8) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {
                            assertEquals(7, oldVersion)
                            assertEquals(8, newVersion)
                            NotificationDatabase.MIGRATION_7_8.migrate(db)
                        }
                    }
                )
                .build()
        )

        v8.writableDatabase.query(
            "SELECT timeStartMinutes, timeEndMinutes, screenState " +
                "FROM rules WHERE name='Legacy rule'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertEquals("ANY", cursor.getString(2))
        }

        v8.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration8To9AddsVaultIndexesAndBackfillsMessageSearch() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "notification-control-migration-8-9.db"
        context.deleteDatabase(databaseName)

        val v8 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(8) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE notifications (
                                    sbnKey TEXT NOT NULL PRIMARY KEY,
                                    platformKey TEXT NOT NULL,
                                    packageName TEXT NOT NULL,
                                    updatedAt INTEGER NOT NULL,
                                    protected INTEGER NOT NULL,
                                    postedAt INTEGER NOT NULL,
                                    removedAt INTEGER
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE messages (
                                    messageKey TEXT NOT NULL PRIMARY KEY,
                                    notificationKey TEXT NOT NULL,
                                    sender TEXT,
                                    text TEXT NOT NULL,
                                    timestamp INTEGER NOT NULL,
                                    mimeType TEXT,
                                    dataUri TEXT
                                )
                                """.trimIndent()
                            )
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

        v8.writableDatabase.execSQL(
            """
            INSERT INTO notifications(
                sbnKey, platformKey, packageName, updatedAt,
                protected, postedAt, removedAt
            ) VALUES(
                'event-1', 'platform-1', 'com.example', 200,
                0, 100, NULL
            )
            """.trimIndent()
        )
        v8.writableDatabase.execSQL(
            """
            INSERT INTO messages(
                messageKey, notificationKey, sender, text,
                timestamp, mimeType, dataUri
            ) VALUES(
                'message-1', 'event-1', 'Anna', 'ciao mondo',
                150, NULL, NULL
            )
            """.trimIndent()
        )
        v8.close()

        val v9 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(9) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {
                            assertEquals(8, oldVersion)
                            assertEquals(9, newVersion)
                            NotificationDatabase.MIGRATION_8_9.migrate(db)
                        }
                    }
                )
                .build()
        )

        val db = v9.writableDatabase
        val expectedIndexes = setOf(
            "index_notifications_updatedAt",
            "index_notifications_packageName_updatedAt",
            "index_notifications_protected_postedAt",
            "index_notifications_platformKey_removedAt_updatedAt"
        )
        val actualIndexes = mutableSetOf<String>()
        db.query("PRAGMA index_list('notifications')").use { cursor ->
            val nameColumn = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameColumn >= 0) {
                    actualIndexes += cursor.getString(nameColumn)
                }
            }
        }
        assertTrue(actualIndexes.containsAll(expectedIndexes))

        db.query(
            "SELECT messageKey, notificationKey, sender, text " +
                "FROM message_fts WHERE message_fts MATCH 'mondo*'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("message-1", cursor.getString(0))
            assertEquals("event-1", cursor.getString(1))
            assertEquals("Anna", cursor.getString(2))
            assertEquals("ciao mondo", cursor.getString(3))
        }

        v9.close()
        context.deleteDatabase(databaseName)
    }


    @Test
    fun migration9To10PreservesExistingPreviewOnLatestRevision() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "notification-control-migration-9-10.db"
        context.deleteDatabase(databaseName)

        val v9 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(9) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE notifications (
                                    sbnKey TEXT NOT NULL PRIMARY KEY,
                                    thumbnailPath TEXT
                                )
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TABLE notification_revisions (
                                    revisionKey TEXT NOT NULL PRIMARY KEY,
                                    notificationKey TEXT NOT NULL,
                                    capturedAt INTEGER NOT NULL,
                                    title TEXT,
                                    text TEXT,
                                    bigText TEXT,
                                    subText TEXT,
                                    conversationTitle TEXT,
                                    contentHash TEXT NOT NULL
                                )
                                """.trimIndent()
                            )
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

        v9.writableDatabase.execSQL(
            "INSERT INTO notifications(sbnKey, thumbnailPath) " +
                "VALUES('event-1', '/vault/photo.webp')"
        )
        v9.writableDatabase.execSQL(
            """
            INSERT INTO notification_revisions(
                revisionKey, notificationKey, capturedAt, contentHash
            ) VALUES
                ('rev-old', 'event-1', 100, 'hash-old'),
                ('rev-new', 'event-1', 200, 'hash-new')
            """.trimIndent()
        )
        v9.close()

        val v10 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(10) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {
                            assertEquals(9, oldVersion)
                            assertEquals(10, newVersion)
                            NotificationDatabase.MIGRATION_9_10.migrate(db)
                        }
                    }
                )
                .build()
        )

        v10.writableDatabase.query(
            "SELECT revisionKey, thumbnailPath FROM notification_revisions " +
                "ORDER BY capturedAt ASC"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("rev-old", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.moveToNext())
            assertEquals("rev-new", cursor.getString(0))
            assertEquals("/vault/photo.webp", cursor.getString(1))
        }

        v10.close()
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

    @Test
    fun migration10To11AddsPersistentMediaRecoveryTables() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "notification-control-migration-10-11.db"
        context.deleteDatabase(databaseName)

        val v10 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(10) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE notification_revisions (
                                    revisionKey TEXT NOT NULL PRIMARY KEY
                                )
                                """.trimIndent()
                            )
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
        v10.writableDatabase
        v10.close()

        val v11 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(11) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {
                            assertEquals(10, oldVersion)
                            assertEquals(11, newVersion)
                            NotificationDatabase.MIGRATION_10_11.migrate(db)
                        }
                    }
                )
                .build()
        )

        val db = v11.writableDatabase
        val tables = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name IN ('media_recovery_pending', 'media_rescue')"
        ).use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        assertEquals(
            setOf("media_recovery_pending", "media_rescue"),
            tables
        )

        db.query("PRAGMA table_info(media_recovery_pending)").use { cursor ->
            val columns = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
            assertTrue("baselineGeneration" in columns)
            assertTrue("referencePerceptualHash" in columns)
            assertTrue("expiresAt" in columns)
        }

        v11.close()
        context.deleteDatabase(databaseName)
    }

}

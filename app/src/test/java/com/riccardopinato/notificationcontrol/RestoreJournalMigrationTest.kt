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
class RestoreJournalMigrationTest {
    @Test
    fun migration11To12CreatesRestoreJournal() {
        val context = RuntimeEnvironment.getApplication()
        val name = "notification-control-migration-11-12.db"
        context.deleteDatabase(name)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(12) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {
                            assertEquals(11, oldVersion)
                            assertEquals(12, newVersion)
                            NotificationDatabase.MIGRATION_11_12.migrate(db)
                        }
                    }
                )
                .build()
        )

        helper.writableDatabase.version = 11
        helper.close()

        val migrated = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(12) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {
                            NotificationDatabase.MIGRATION_11_12.migrate(db)
                        }
                    }
                )
                .build()
        )
        val db = migrated.writableDatabase
        db.execSQL(
            "INSERT INTO restore_journal(id, operationId, createdAt) VALUES(1, 'op', 42)"
        )
        db.query("SELECT operationId, createdAt FROM restore_journal WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("op", it.getString(0))
            assertEquals(42L, it.getLong(1))
        }
        migrated.close()
        context.deleteDatabase(name)
    }
}

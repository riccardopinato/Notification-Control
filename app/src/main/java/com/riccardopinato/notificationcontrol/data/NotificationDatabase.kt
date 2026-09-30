package com.riccardopinato.notificationcontrol.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NotificationEntity::class,
        MessageEntity::class,
        NotificationRevisionEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var instance: NotificationDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notification_revisions (
                        revisionKey TEXT NOT NULL,
                        notificationKey TEXT NOT NULL,
                        capturedAt INTEGER NOT NULL,
                        title TEXT,
                        text TEXT,
                        bigText TEXT,
                        subText TEXT,
                        conversationTitle TEXT,
                        contentHash TEXT NOT NULL,
                        PRIMARY KEY(revisionKey),
                        FOREIGN KEY(notificationKey) REFERENCES notifications(sbnKey) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_revisions_notificationKey ON notification_revisions(notificationKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_revisions_capturedAt ON notification_revisions(capturedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_revisions_contentHash ON notification_revisions(contentHash)")
            }
        }

        fun get(context: Context): NotificationDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NotificationDatabase::class.java,
                "notification_control.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}

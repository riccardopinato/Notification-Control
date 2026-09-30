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
        NotificationRevisionEntity::class,
        NotificationFtsEntity::class
    ],
    version = 3,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS notification_fts
                    USING FTS4(
                        sbnKey,
                        appLabel,
                        title,
                        text,
                        bigText,
                        conversationTitle,
                        messagesText
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO notification_fts(
                        sbnKey,
                        appLabel,
                        title,
                        text,
                        bigText,
                        conversationTitle,
                        messagesText
                    )
                    SELECT
                        n.sbnKey,
                        n.appLabel,
                        n.title,
                        n.text,
                        n.bigText,
                        n.conversationTitle,
                        COALESCE((
                            SELECT GROUP_CONCAT(m.text, ' ')
                            FROM messages m
                            WHERE m.notificationKey = n.sbnKey
                        ), '')
                    FROM notifications n
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): NotificationDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NotificationDatabase::class.java,
                "notification_control.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}

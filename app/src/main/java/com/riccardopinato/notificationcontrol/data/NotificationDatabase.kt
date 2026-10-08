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
        NotificationFtsEntity::class,
        MessageFtsEntity::class,
        RuleEntity::class,
        RuleActionEntity::class,
        CriticalPatternEntity::class,
        CriticalAlertEntity::class,
        FollowUpEntity::class,
        PickupCodeEntity::class,
        LuminousProfileEntity::class,
        MediaRecoveryPendingEntity::class,
        MediaRescueEntity::class,
        RestoreJournalEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun automationDao(): AutomationDao
    abstract fun backupDao(): BackupDao
    abstract fun luminousProfileDao(): LuminousProfileDao
    abstract fun mediaRecoveryDao(): MediaRecoveryDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rules (
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
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rules_enabled ON rules(enabled)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rules_priority ON rules(priority)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rules_packageName ON rules(packageName)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rule_actions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ruleId INTEGER NOT NULL,
                        actionType TEXT NOT NULL,
                        actionValue TEXT,
                        FOREIGN KEY(ruleId) REFERENCES rules(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_actions_ruleId ON rule_actions(ruleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_actions_actionType ON rule_actions(actionType)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS critical_patterns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        value TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_critical_patterns_type ON critical_patterns(type)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_critical_patterns_type_value ON critical_patterns(type, value)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS follow_ups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        notificationKey TEXT,
                        sourcePackage TEXT,
                        sourceLabel TEXT,
                        title TEXT NOT NULL,
                        body TEXT,
                        dueAt INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        repeatMinutes INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_follow_ups_status ON follow_ups(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_follow_ups_dueAt ON follow_ups(dueAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_follow_ups_notificationKey ON follow_ups(notificationKey)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pickup_codes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        code TEXT NOT NULL,
                        sourcePackage TEXT NOT NULL,
                        sourceLabel TEXT NOT NULL,
                        notificationKey TEXT NOT NULL,
                        contextText TEXT,
                        createdAt INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL,
                        dismissed INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pickup_codes_expiresAt ON pickup_codes(expiresAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pickup_codes_dismissed ON pickup_codes(dismissed)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pickup_codes_code_sourcePackage_notificationKey ON pickup_codes(code, sourcePackage, notificationKey)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS luminous_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        packageName TEXT,
                        senderQuery TEXT,
                        colorHex TEXT NOT NULL,
                        flashEnabled INTEGER NOT NULL,
                        overlayEnabled INTEGER NOT NULL,
                        strobeCycles INTEGER NOT NULL,
                        strobeSpeedMs INTEGER NOT NULL,
                        circleThickness REAL NOT NULL,
                        circleGlow REAL NOT NULL,
                        pulseSpeedMs INTEGER NOT NULL,
                        displayDurationMs INTEGER NOT NULL,
                        priority INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_luminous_profiles_enabled ON luminous_profiles(enabled)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_luminous_profiles_packageName ON luminous_profiles(packageName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_luminous_profiles_priority ON luminous_profiles(priority)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications " +
                        "ADD COLUMN platformKey TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("UPDATE notifications SET platformKey = sbnKey")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_platformKey " +
                        "ON notifications(platformKey)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS critical_alerts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventKey TEXT NOT NULL,
                        sourcePackage TEXT NOT NULL,
                        sourceLabel TEXT NOT NULL,
                        title TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        escalationStep INTEGER NOT NULL,
                        nextAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_critical_alerts_eventKey ON critical_alerts(eventKey)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_critical_alerts_status ON critical_alerts(status)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_critical_alerts_nextAt ON critical_alerts(nextAt)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rules ADD COLUMN timeStartMinutes INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE rules ADD COLUMN timeEndMinutes INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE rules ADD COLUMN screenState TEXT NOT NULL DEFAULT 'ANY'"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_updatedAt " +
                        "ON notifications(updatedAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_packageName_updatedAt " +
                        "ON notifications(packageName, updatedAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_protected_postedAt " +
                        "ON notifications(protected, postedAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_platformKey_removedAt_updatedAt " +
                        "ON notifications(platformKey, removedAt, updatedAt)"
                )
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS message_fts
                    USING FTS4(
                        messageKey,
                        notificationKey,
                        sender,
                        text
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO message_fts(
                        messageKey,
                        notificationKey,
                        sender,
                        text
                    )
                    SELECT
                        messageKey,
                        notificationKey,
                        sender,
                        text
                    FROM messages
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notification_revisions ADD COLUMN thumbnailPath TEXT"
                )
                db.execSQL(
                    """
                    UPDATE notification_revisions
                    SET thumbnailPath = (
                        SELECT n.thumbnailPath
                        FROM notifications n
                        WHERE n.sbnKey = notification_revisions.notificationKey
                    )
                    WHERE capturedAt = (
                        SELECT MAX(r2.capturedAt)
                        FROM notification_revisions r2
                        WHERE r2.notificationKey = notification_revisions.notificationKey
                    )
                      AND EXISTS (
                        SELECT 1
                        FROM notifications n2
                        WHERE n2.sbnKey = notification_revisions.notificationKey
                          AND n2.thumbnailPath IS NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS media_recovery_pending (
                        revisionKey TEXT NOT NULL,
                        notificationKey TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        postedAt INTEGER NOT NULL,
                        capturedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL,
                        baselineGeneration INTEGER,
                        mediaStoreVersion TEXT,
                        referencePerceptualHash TEXT,
                        attempts INTEGER NOT NULL,
                        lastAttemptAt INTEGER,
                        PRIMARY KEY(revisionKey),
                        FOREIGN KEY(revisionKey) REFERENCES notification_revisions(revisionKey)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_recovery_pending_notificationKey " +
                        "ON media_recovery_pending(notificationKey)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_recovery_pending_packageName " +
                        "ON media_recovery_pending(packageName)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_recovery_pending_expiresAt " +
                        "ON media_recovery_pending(expiresAt)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS media_rescue (
                        rescueKey TEXT NOT NULL,
                        sourceKey TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        sourceKind TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        sourceUri TEXT,
                        mediaTimestamp INTEGER NOT NULL,
                        observedAt INTEGER NOT NULL,
                        mimeType TEXT,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        perceptualHash TEXT,
                        confidence INTEGER NOT NULL,
                        PRIMARY KEY(rescueKey)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_rescue_packageName " +
                        "ON media_rescue(packageName)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_rescue_observedAt " +
                        "ON media_rescue(observedAt)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_media_rescue_sourceKey " +
                        "ON media_rescue(sourceKey)"
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS restore_journal (
                        id INTEGER NOT NULL,
                        operationId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
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
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12
                )
                .build()
                .also { instance = it }
        }
    }
}

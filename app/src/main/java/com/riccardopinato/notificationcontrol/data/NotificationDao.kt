package com.riccardopinato.notificationcontrol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Upsert
    suspend fun upsertNotification(entity: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRevision(revision: NotificationRevisionEntity)

    @Insert
    suspend fun insertFts(entity: NotificationFtsEntity)

    @Query("DELETE FROM notification_fts WHERE sbnKey = :sbnKey")
    suspend fun deleteFts(sbnKey: String)

    @Query("DELETE FROM notification_fts WHERE sbnKey IN (:keys)")
    suspend fun deleteFts(keys: List<String>)

    @Query(
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
                SELECT GROUP_CONCAT(
                    COALESCE(m.sender, '') || ' ' || m.text,
                    ' '
                )
                FROM messages m
                WHERE m.notificationKey = n.sbnKey
            ), '')
        FROM notifications n
        WHERE n.sbnKey = :sbnKey
        """
    )
    suspend fun rebuildFts(sbnKey: String)

    @Transaction
    suspend fun upsert(
        entity: NotificationEntity,
        messages: List<MessageEntity>,
        revision: NotificationRevisionEntity
    ) {
        upsertNotification(entity)
        insertRevision(revision)
        if (messages.isNotEmpty()) insertMessages(messages)
        deleteFts(entity.sbnKey)
        rebuildFts(entity.sbnKey)
    }

    @Query("SELECT * FROM notifications ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 250): Flow<List<NotificationEntity>>

    @Query(
        """
        SELECT * FROM notifications
        WHERE (:packageName IS NULL OR packageName = :packageName)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    fun observeFilteredByApp(
        packageName: String?,
        limit: Int
    ): Flow<List<NotificationEntity>>

    @Query(
        """
        SELECT DISTINCT n.* FROM notifications n
        INNER JOIN notification_fts f ON f.sbnKey = n.sbnKey
        WHERE (:packageName IS NULL OR n.packageName = :packageName)
          AND notification_fts MATCH :ftsQuery
        ORDER BY n.updatedAt DESC
        LIMIT :limit
        """
    )
    fun observeSearch(
        ftsQuery: String,
        packageName: String?,
        limit: Int
    ): Flow<List<NotificationEntity>>

    @Query(
        """
        SELECT packageName, MAX(appLabel) AS appLabel, COUNT(*) AS count
        FROM notifications
        GROUP BY packageName
        ORDER BY appLabel COLLATE NOCASE ASC
        """
    )
    fun observeAppFilters(): Flow<List<VaultAppFilter>>

    @Query("SELECT COUNT(*) FROM notifications")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE protected = 1")
    suspend fun countProtected(): Int

    @Query(
        """
        SELECT * FROM notifications
        WHERE platformKey = :platformKey AND removedAt IS NULL
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun findActiveByPlatformKey(platformKey: String): NotificationEntity?

    @Query(
        """
        UPDATE notifications
        SET removedAt = :removedAt,
            removalReason = :reason,
            updatedAt = :removedAt
        WHERE sbnKey = (
            SELECT sbnKey FROM notifications
            WHERE platformKey = :platformKey AND removedAt IS NULL
            ORDER BY updatedAt DESC
            LIMIT 1
        )
        """
    )
    suspend fun markLatestRemovedByPlatformKey(
        platformKey: String,
        removedAt: Long,
        reason: Int?
    )

    @Query("UPDATE notifications SET protected = :protected WHERE sbnKey = :sbnKey")
    suspend fun setProtected(sbnKey: String, protected: Boolean)

    @Query("SELECT sbnKey FROM notifications WHERE protected = 0 AND postedAt < :cutoffMillis")
    suspend fun eventKeysOlderThan(cutoffMillis: Long): List<String>

    @Query("SELECT thumbnailPath FROM notifications WHERE protected = 0 AND postedAt < :cutoffMillis AND thumbnailPath IS NOT NULL")
    suspend fun thumbnailPathsOlderThan(cutoffMillis: Long): List<String>

    @Query("SELECT thumbnailPath FROM notifications WHERE thumbnailPath IS NOT NULL")
    suspend fun allThumbnailPaths(): List<String>

    @Query("DELETE FROM notifications WHERE protected = 0 AND postedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Transaction
    suspend fun deleteExpiredAndReturnMedia(cutoffMillis: Long): List<String> {
        val keys = eventKeysOlderThan(cutoffMillis)
        val media = thumbnailPathsOlderThan(cutoffMillis)
        if (keys.isNotEmpty()) deleteFts(keys)
        deleteOlderThan(cutoffMillis)
        return media
    }

    @Transaction
    suspend fun deleteEverything() {
        deleteAllFts()
        deleteAll()
    }

    @Query("DELETE FROM notification_fts")
    suspend fun deleteAllFts()

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("SELECT * FROM notifications WHERE sbnKey = :sbnKey LIMIT 1")
    suspend fun findByKey(sbnKey: String): NotificationEntity?

    @Query("SELECT * FROM messages WHERE notificationKey = :sbnKey ORDER BY timestamp ASC, messageKey ASC")
    fun observeMessagesFor(sbnKey: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM notification_revisions WHERE notificationKey = :sbnKey ORDER BY capturedAt ASC")
    fun observeRevisionsFor(sbnKey: String): Flow<List<NotificationRevisionEntity>>

    @Query("SELECT * FROM notification_revisions WHERE notificationKey = :sbnKey ORDER BY capturedAt ASC")
    suspend fun revisionsFor(sbnKey: String): List<NotificationRevisionEntity>
}

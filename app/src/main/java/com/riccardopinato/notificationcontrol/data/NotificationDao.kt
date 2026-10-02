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

    @Query("DELETE FROM notification_fts WHERE sbnKey = :eventKey")
    suspend fun deleteFts(eventKey: String)

    @Query("SELECT * FROM messages WHERE notificationKey = :eventKey ORDER BY timestamp ASC")
    suspend fun messagesFor(eventKey: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE notificationKey = :eventKey ORDER BY timestamp ASC")
    fun observeMessages(eventKey: String): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM notification_revisions " +
            "WHERE notificationKey = :eventKey ORDER BY capturedAt ASC"
    )
    fun observeRevisions(eventKey: String): Flow<List<NotificationRevisionEntity>>

    @Query(
        "SELECT * FROM notification_revisions " +
            "WHERE notificationKey = :eventKey ORDER BY capturedAt DESC LIMIT 1"
    )
    suspend fun latestRevisionFor(eventKey: String): NotificationRevisionEntity?

    @Transaction
    suspend fun upsert(
        entity: NotificationEntity,
        messages: List<MessageEntity>,
        revision: NotificationRevisionEntity
    ) {
        upsertNotification(entity)
        insertRevision(revision)
        if (messages.isNotEmpty()) insertMessages(messages)

        val historicMessages = messagesFor(entity.sbnKey)
        deleteFts(entity.sbnKey)
        insertFts(
            NotificationFtsEntity(
                sbnKey = entity.sbnKey,
                appLabel = entity.appLabel,
                title = entity.title,
                text = entity.text,
                bigText = entity.bigText,
                conversationTitle = entity.conversationTitle,
                messagesText = historicMessages.joinToString(" ") {
                    it.sender.orEmpty() + " " + it.text
                }
            )
        )
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
        SELECT * FROM notifications
        WHERE platformKey = :platformKey
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun findLatestByPlatformKey(platformKey: String): NotificationEntity?

    @Query(
        """
        SELECT n.* FROM notifications n
        INNER JOIN notification_revisions r ON r.notificationKey = n.sbnKey
        WHERE n.packageName = :packageName
          AND n.removedAt IS NOT NULL
          AND n.removedAt >= :sinceMillis
          AND r.contentHash = :contentHash
          AND r.capturedAt = (
              SELECT MAX(r2.capturedAt)
              FROM notification_revisions r2
              WHERE r2.notificationKey = n.sbnKey
          )
        ORDER BY n.removedAt DESC
        LIMIT 1
        """
    )
    suspend fun findRecentRemovedReplay(
        packageName: String,
        contentHash: String,
        sinceMillis: Long
    ): NotificationEntity?

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
    suspend fun markRemovedByPlatformKey(
        platformKey: String,
        removedAt: Long,
        reason: Int?
    )

    @Query("UPDATE notifications SET protected = :protected WHERE sbnKey = :eventKey")
    suspend fun setProtected(eventKey: String, protected: Boolean)

    @Query(
        """
        SELECT thumbnailPath FROM notifications
        WHERE protected = 0
          AND postedAt < :cutoffMillis
          AND thumbnailPath IS NOT NULL
        """
    )
    suspend fun thumbnailPathsOlderThan(cutoffMillis: Long): List<String>

    @Query("SELECT thumbnailPath FROM notifications WHERE thumbnailPath IS NOT NULL")
    suspend fun allThumbnailPaths(): List<String>

    @Query("SELECT DISTINCT packageName FROM notifications")
    suspend fun packagesInVault(): List<String>

    @Query(
        "SELECT sbnKey FROM notifications " +
            "WHERE packageName = :packageName AND protected = 0 " +
            "AND postedAt < :cutoffMillis ORDER BY postedAt ASC"
    )
    suspend fun expiredKeysForPackage(
        packageName: String,
        cutoffMillis: Long
    ): List<String>

    @Query(
        """
        SELECT COALESCE(SUM(
            LENGTH(COALESCE(title, '')) +
            LENGTH(COALESCE(text, '')) +
            LENGTH(COALESCE(bigText, '')) +
            LENGTH(COALESCE(subText, '')) +
            LENGTH(COALESCE(conversationTitle, ''))
        ), 0)
        FROM notifications
        """
    )
    suspend fun approximateNotificationTextBytes(): Long

    @Query(
        "SELECT COALESCE(SUM(LENGTH(text) + LENGTH(COALESCE(sender, ''))), 0) " +
            "FROM messages"
    )
    suspend fun approximateMessageBytes(): Long

    @Query(
        """
        SELECT COALESCE(SUM(
            LENGTH(COALESCE(title, '')) +
            LENGTH(COALESCE(text, '')) +
            LENGTH(COALESCE(bigText, '')) +
            LENGTH(COALESCE(subText, '')) +
            LENGTH(COALESCE(conversationTitle, ''))
        ), 0)
        FROM notification_revisions
        """
    )
    suspend fun approximateRevisionBytes(): Long

    @Query(
        "SELECT sbnKey FROM notifications " +
            "WHERE protected = 0 ORDER BY postedAt ASC LIMIT :limit"
    )
    suspend fun oldestUnprotectedKeys(limit: Int): List<String>

    @Query("SELECT thumbnailPath FROM notifications WHERE sbnKey IN (:keys) AND thumbnailPath IS NOT NULL")
    suspend fun thumbnailPathsForKeys(keys: List<String>): List<String>

    @Query("DELETE FROM notification_fts WHERE sbnKey IN (:keys)")
    suspend fun deleteFtsByKeys(keys: List<String>)

    @Query("DELETE FROM notifications WHERE sbnKey IN (:keys)")
    suspend fun deleteNotificationsByKeys(keys: List<String>)

    @Transaction
    suspend fun deleteByKeysAndReturnMedia(keys: List<String>): List<String> {
        if (keys.isEmpty()) return emptyList()
        val media = thumbnailPathsForKeys(keys)
        deleteFtsByKeys(keys)
        deleteNotificationsByKeys(keys)
        return media
    }

    @Query(
        """
        DELETE FROM notification_fts
        WHERE sbnKey IN (
            SELECT sbnKey FROM notifications
            WHERE protected = 0 AND postedAt < :cutoffMillis
        )
        """
    )
    suspend fun deleteFtsOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM notifications WHERE protected = 0 AND postedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Transaction
    suspend fun deleteExpiredAndReturnMedia(cutoffMillis: Long): List<String> {
        val media = thumbnailPathsOlderThan(cutoffMillis)
        deleteFtsOlderThan(cutoffMillis)
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

    @Query("SELECT * FROM notifications WHERE sbnKey = :eventKey LIMIT 1")
    suspend fun findByKey(eventKey: String): NotificationEntity?

    @Query(
        "SELECT * FROM notification_revisions " +
            "WHERE notificationKey = :eventKey ORDER BY capturedAt ASC"
    )
    suspend fun revisionsFor(eventKey: String): List<NotificationRevisionEntity>
}

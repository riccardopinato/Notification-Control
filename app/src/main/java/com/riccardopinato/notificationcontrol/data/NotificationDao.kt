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

    @Transaction
    suspend fun upsert(
        entity: NotificationEntity,
        messages: List<MessageEntity>,
        revision: NotificationRevisionEntity
    ) {
        upsertNotification(entity)
        insertRevision(revision)
        if (messages.isNotEmpty()) insertMessages(messages)
    }

    @Query("SELECT * FROM notifications ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 250): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE protected = 1")
    suspend fun countProtected(): Int

    @Query("UPDATE notifications SET removedAt = :removedAt, removalReason = :reason, updatedAt = :removedAt WHERE sbnKey = :sbnKey")
    suspend fun markRemoved(sbnKey: String, removedAt: Long, reason: Int?)

    @Query("UPDATE notifications SET protected = :protected WHERE sbnKey = :sbnKey")
    suspend fun setProtected(sbnKey: String, protected: Boolean)

    @Query("SELECT thumbnailPath FROM notifications WHERE protected = 0 AND postedAt < :cutoffMillis AND thumbnailPath IS NOT NULL")
    suspend fun thumbnailPathsOlderThan(cutoffMillis: Long): List<String>

    @Query("SELECT thumbnailPath FROM notifications WHERE thumbnailPath IS NOT NULL")
    suspend fun allThumbnailPaths(): List<String>

    @Query("DELETE FROM notifications WHERE protected = 0 AND postedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Transaction
    suspend fun deleteExpiredAndReturnMedia(cutoffMillis: Long): List<String> {
        val media = thumbnailPathsOlderThan(cutoffMillis)
        deleteOlderThan(cutoffMillis)
        return media
    }

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("SELECT * FROM notifications WHERE sbnKey = :sbnKey LIMIT 1")
    suspend fun findByKey(sbnKey: String): NotificationEntity?

    @Query("SELECT * FROM notification_revisions WHERE notificationKey = :sbnKey ORDER BY capturedAt ASC")
    suspend fun revisionsFor(sbnKey: String): List<NotificationRevisionEntity>
}

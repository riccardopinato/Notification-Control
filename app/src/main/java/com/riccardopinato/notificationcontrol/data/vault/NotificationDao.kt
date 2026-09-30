package com.riccardopinato.notificationcontrol.data.vault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotification(entity: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM notification_messages WHERE notificationKey = :notificationKey")
    suspend fun deleteMessagesForNotification(notificationKey: String)

    @Transaction
    suspend fun replaceNotificationAndMessages(
        entity: NotificationEntity,
        messages: List<MessageEntity>
    ) {
        upsertNotification(entity)
        deleteMessagesForNotification(entity.notificationKey)
        if (messages.isNotEmpty()) upsertMessages(messages)
    }

    @Query("SELECT * FROM notification_events WHERE notificationKey = :notificationKey LIMIT 1")
    suspend fun findByKey(notificationKey: String): NotificationEntity?

    @Query(
        """
        UPDATE notification_events
        SET removedAt = :removedAt,
            removalReason = :reason,
            lastUpdatedAt = :removedAt
        WHERE notificationKey = :notificationKey
        """
    )
    suspend fun markRemoved(notificationKey: String, removedAt: Long, reason: Int?)

    @Query("SELECT * FROM notification_events ORDER BY lastUpdatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 250): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notification_events")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM notification_events WHERE protectedFromCleanup = 0 AND lastUpdatedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM notification_messages WHERE notificationKey NOT IN (SELECT notificationKey FROM notification_events)")
    suspend fun deleteOrphanMessages(): Int
}

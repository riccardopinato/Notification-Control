package com.riccardopinato.notificationcontrol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BackupDao {
    @Query("SELECT * FROM notifications ORDER BY postedAt ASC")
    suspend fun allNotifications(): List<NotificationEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun allMessages(): List<MessageEntity>

    @Query("SELECT * FROM notification_revisions ORDER BY capturedAt ASC")
    suspend fun allRevisions(): List<NotificationRevisionEntity>

    @Query("SELECT * FROM rules ORDER BY id ASC")
    suspend fun allRules(): List<RuleEntity>

    @Query("SELECT * FROM rule_actions ORDER BY id ASC")
    suspend fun allRuleActions(): List<RuleActionEntity>

    @Query("SELECT * FROM critical_patterns ORDER BY id ASC")
    suspend fun allCriticalPatterns(): List<CriticalPatternEntity>

    @Query("SELECT * FROM critical_alerts ORDER BY id ASC")
    suspend fun allCriticalAlerts(): List<CriticalAlertEntity>

    @Query("SELECT * FROM follow_ups ORDER BY id ASC")
    suspend fun allFollowUps(): List<FollowUpEntity>

    @Query("SELECT * FROM pickup_codes ORDER BY id ASC")
    suspend fun allPickupCodes(): List<PickupCodeEntity>

    @Query("DELETE FROM pickup_codes")
    suspend fun deletePickupCodes()

    @Query("DELETE FROM follow_ups")
    suspend fun deleteFollowUps()

    @Query("DELETE FROM critical_patterns")
    suspend fun deleteCriticalPatterns()

    @Query("DELETE FROM critical_alerts")
    suspend fun deleteCriticalAlerts()

    @Query("DELETE FROM rule_actions")
    suspend fun deleteRuleActions()

    @Query("DELETE FROM rules")
    suspend fun deleteRules()

    @Query("DELETE FROM notification_fts")
    suspend fun deleteFts()

    @Query("DELETE FROM message_fts")
    suspend fun deleteMessageFts()

    @Query("DELETE FROM notification_revisions")
    suspend fun deleteRevisions()

    @Query("DELETE FROM messages")
    suspend fun deleteMessages()

    @Query("DELETE FROM notifications")
    suspend fun deleteNotifications()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(items: List<NotificationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(items: List<MessageEntity>)

    @Insert
    suspend fun insertMessageFts(items: List<MessageFtsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevisions(items: List<NotificationRevisionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(items: List<RuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRuleActions(items: List<RuleActionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCriticalPatterns(items: List<CriticalPatternEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCriticalAlerts(items: List<CriticalAlertEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowUps(items: List<FollowUpEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPickupCodes(items: List<PickupCodeEntity>)
}

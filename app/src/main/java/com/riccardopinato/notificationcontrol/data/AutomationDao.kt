package com.riccardopinato.notificationcontrol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Transaction
    @Query("SELECT * FROM rules ORDER BY priority DESC, id ASC")
    fun observeRules(): Flow<List<RuleWithActions>>

    @Transaction
    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun enabledRules(): List<RuleWithActions>

    @Transaction
    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    fun observeEnabledRules(): Flow<List<RuleWithActions>>

    @Query("SELECT COUNT(*) FROM rules")
    suspend fun ruleCount(): Int

    @Query("SELECT * FROM rules WHERE id = :id LIMIT 1")
    suspend fun ruleById(id: Long): RuleEntity?

    @Insert
    suspend fun insertRule(rule: RuleEntity): Long

    @Insert
    suspend fun insertRuleActions(actions: List<RuleActionEntity>)

    @Transaction
    suspend fun createRule(rule: RuleEntity, actionTypes: List<Pair<String, String?>>): Long {
        val id = insertRule(rule)
        if (actionTypes.isNotEmpty()) {
            insertRuleActions(
                actionTypes.map { (type, value) ->
                    RuleActionEntity(ruleId = id, actionType = type, actionValue = value)
                }
            )
        }
        return id
    }

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Query("DELETE FROM rule_actions WHERE ruleId = :ruleId")
    suspend fun deleteRuleActions(ruleId: Long)

    @Transaction
    suspend fun replaceRule(
        rule: RuleEntity,
        actionTypes: List<Pair<String, String?>>
    ) {
        updateRule(rule)
        deleteRuleActions(rule.id)
        if (actionTypes.isNotEmpty()) {
            insertRuleActions(
                actionTypes.map { (type, value) ->
                    RuleActionEntity(
                        ruleId = rule.id,
                        actionType = type,
                        actionValue = value
                    )
                }
            )
        }
    }

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setRuleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("SELECT * FROM critical_patterns ORDER BY type, createdAt")
    fun observeCriticalPatterns(): Flow<List<CriticalPatternEntity>>

    @Query("SELECT * FROM critical_patterns WHERE enabled = 1")
    suspend fun enabledCriticalPatterns(): List<CriticalPatternEntity>

    @Query("SELECT * FROM critical_patterns WHERE enabled = 1")
    fun observeEnabledCriticalPatterns(): Flow<List<CriticalPatternEntity>>

    @Query("SELECT COUNT(*) FROM critical_patterns WHERE type = :type")
    suspend fun criticalPatternCount(type: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCriticalPattern(entity: CriticalPatternEntity): Long

    @Query("DELETE FROM critical_patterns WHERE id = :id")
    suspend fun deleteCriticalPattern(id: Long)

    @Query("SELECT * FROM critical_alerts WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun observeActiveCriticalAlerts(): Flow<List<CriticalAlertEntity>>

    @Query("SELECT * FROM critical_alerts WHERE status = 'ACTIVE' ORDER BY nextAt ASC")
    suspend fun activeCriticalAlerts(): List<CriticalAlertEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCriticalAlert(entity: CriticalAlertEntity): Long

    @Query("SELECT * FROM critical_alerts WHERE id = :id LIMIT 1")
    suspend fun criticalAlertById(id: Long): CriticalAlertEntity?

    @Query(
        "UPDATE critical_alerts SET status = 'HANDLED', updatedAt = :now " +
            "WHERE id = :id"
    )
    suspend fun handleCriticalAlert(id: Long, now: Long)

    @Query(
        "UPDATE critical_alerts SET escalationStep = :step, nextAt = :nextAt, " +
            "updatedAt = :now WHERE id = :id AND status = 'ACTIVE'"
    )
    suspend fun advanceCriticalAlert(
        id: Long,
        step: Int,
        nextAt: Long,
        now: Long
    )

    @Query("SELECT * FROM follow_ups WHERE status = 'ACTIVE' ORDER BY dueAt ASC")
    fun observeActiveFollowUps(): Flow<List<FollowUpEntity>>

    @Query("SELECT * FROM follow_ups WHERE status = 'ACTIVE' ORDER BY dueAt ASC")
    suspend fun activeFollowUps(): List<FollowUpEntity>

    @Query("SELECT COUNT(*) FROM follow_ups WHERE status = 'ACTIVE'")
    suspend fun activeFollowUpCount(): Int

    @Insert
    suspend fun insertFollowUp(entity: FollowUpEntity): Long

    @Update
    suspend fun updateFollowUp(entity: FollowUpEntity)

    @Query("SELECT * FROM follow_ups WHERE id = :id LIMIT 1")
    suspend fun followUpById(id: Long): FollowUpEntity?

    @Query("UPDATE follow_ups SET status = 'DONE', updatedAt = :now WHERE id = :id")
    suspend fun completeFollowUp(id: Long, now: Long)

    @Query("UPDATE follow_ups SET dueAt = :dueAt, updatedAt = :now WHERE id = :id AND status = 'ACTIVE'")
    suspend fun snoozeFollowUp(id: Long, dueAt: Long, now: Long)

    @Query(
        "UPDATE follow_ups SET dueAt = :dueAt, repeatMinutes = :repeatMinutes, " +
            "updatedAt = :now WHERE id = :id AND status = 'ACTIVE'"
    )
    suspend fun updateFollowUpSchedule(
        id: Long,
        dueAt: Long,
        repeatMinutes: Int?,
        now: Long
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPickupCode(entity: PickupCodeEntity): Long

    @Query("SELECT * FROM pickup_codes WHERE dismissed = 0 ORDER BY createdAt DESC")
    fun observePickupCodes(): Flow<List<PickupCodeEntity>>

    @Query("UPDATE pickup_codes SET dismissed = 1 WHERE id = :id")
    suspend fun dismissPickupCode(id: Long)

    @Query("DELETE FROM pickup_codes WHERE expiresAt < :now OR dismissed = 1")
    suspend fun cleanupPickupCodes(now: Long): Int
}

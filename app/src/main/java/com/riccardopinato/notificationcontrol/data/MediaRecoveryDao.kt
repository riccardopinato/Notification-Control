package com.riccardopinato.notificationcontrol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaRecoveryDao {
    @Upsert
    suspend fun upsertPending(item: MediaRecoveryPendingEntity)

    @Query(
        "SELECT * FROM media_recovery_pending " +
            "WHERE expiresAt >= :now ORDER BY createdAt ASC LIMIT :limit"
    )
    suspend fun activePending(now: Long, limit: Int = 64): List<MediaRecoveryPendingEntity>

    @Query(
        "UPDATE media_recovery_pending SET attempts = attempts + 1, lastAttemptAt = :now " +
            "WHERE revisionKey IN (:revisionKeys)"
    )
    suspend fun markAttempted(revisionKeys: List<String>, now: Long)

    @Query("DELETE FROM media_recovery_pending WHERE revisionKey = :revisionKey")
    suspend fun deletePending(revisionKey: String)

    @Query("DELETE FROM media_recovery_pending WHERE expiresAt < :now")
    suspend fun deleteExpiredPending(now: Long): Int

    @Query("SELECT COUNT(*) FROM media_recovery_pending WHERE expiresAt >= :now")
    suspend fun countActivePending(now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRescue(item: MediaRescueEntity): Long

    @Query("SELECT * FROM media_rescue WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun rescueBySourceKey(sourceKey: String): MediaRescueEntity?

    @Query("SELECT * FROM media_rescue ORDER BY observedAt DESC LIMIT :limit")
    fun observeRescue(limit: Int = 100): Flow<List<MediaRescueEntity>>

    @Query("SELECT COUNT(*) FROM media_rescue")
    fun observeRescueCount(): Flow<Int>

    @Query("SELECT localPath FROM media_rescue")
    suspend fun allRescuePaths(): List<String>

    @Query("SELECT DISTINCT packageName FROM media_rescue")
    suspend fun rescuePackages(): List<String>

    @Query(
        "SELECT localPath FROM media_rescue " +
            "WHERE packageName = :packageName AND observedAt < :cutoffMillis"
    )
    suspend fun rescuePathsOlderThanForPackage(
        packageName: String,
        cutoffMillis: Long
    ): List<String>

    @Query(
        "DELETE FROM media_rescue " +
            "WHERE packageName = :packageName AND observedAt < :cutoffMillis"
    )
    suspend fun deleteRescueOlderThanForPackage(
        packageName: String,
        cutoffMillis: Long
    ): Int

    @Query("DELETE FROM media_rescue WHERE sourceKey = :sourceKey")
    suspend fun deleteRescueBySourceKey(sourceKey: String)

    @Query("DELETE FROM media_recovery_pending")
    suspend fun deleteAllPending()

    @Query("DELETE FROM media_rescue")
    suspend fun deleteAllRescue()
}

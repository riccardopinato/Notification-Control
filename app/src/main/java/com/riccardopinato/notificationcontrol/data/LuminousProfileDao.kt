package com.riccardopinato.notificationcontrol.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LuminousProfileDao {
    @Query("SELECT * FROM luminous_profiles ORDER BY priority DESC, createdAt DESC")
    fun observeProfiles(): Flow<List<LuminousProfileEntity>>

    @Query("SELECT * FROM luminous_profiles WHERE enabled = 1 ORDER BY priority DESC, createdAt DESC")
    suspend fun enabledProfiles(): List<LuminousProfileEntity>

    @Query("SELECT * FROM luminous_profiles WHERE enabled = 1 ORDER BY priority DESC, createdAt DESC")
    fun observeEnabledProfiles(): Flow<List<LuminousProfileEntity>>

    @Query("SELECT COUNT(*) FROM luminous_profiles WHERE enabled = 1")
    suspend fun enabledProfileCount(): Int

    @Query("SELECT * FROM luminous_profiles ORDER BY id ASC")
    suspend fun allProfiles(): List<LuminousProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: LuminousProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<LuminousProfileEntity>)

    @Query("UPDATE luminous_profiles SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM luminous_profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM luminous_profiles")
    suspend fun deleteAll()
}

package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "restore_journal")
data class RestoreJournalEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val operationId: String,
    val createdAt: Long
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

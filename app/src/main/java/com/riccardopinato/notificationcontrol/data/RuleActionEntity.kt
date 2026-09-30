package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rule_actions",
    foreignKeys = [
        ForeignKey(
            entity = RuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ruleId"), Index("actionType")]
)
data class RuleActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val actionType: String,
    val actionValue: String? = null
)

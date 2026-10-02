package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_recovery_pending",
    foreignKeys = [
        ForeignKey(
            entity = NotificationRevisionEntity::class,
            parentColumns = ["revisionKey"],
            childColumns = ["revisionKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("notificationKey"),
        Index("packageName"),
        Index("expiresAt")
    ]
)
data class MediaRecoveryPendingEntity(
    @PrimaryKey val revisionKey: String,
    val notificationKey: String,
    val packageName: String,
    val postedAt: Long,
    val capturedAt: Long,
    val createdAt: Long,
    val expiresAt: Long,
    val baselineGeneration: Long?,
    val mediaStoreVersion: String?,
    val referencePerceptualHash: String?,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null
)

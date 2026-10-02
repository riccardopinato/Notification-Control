package com.riccardopinato.notificationcontrol.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_rescue",
    indices = [
        Index("packageName"),
        Index("observedAt"),
        Index("sourceKey", unique = true)
    ]
)
data class MediaRescueEntity(
    @PrimaryKey val rescueKey: String,
    val sourceKey: String,
    val packageName: String,
    val sourceKind: String,
    val localPath: String,
    val sourceUri: String?,
    val mediaTimestamp: Long,
    val observedAt: Long,
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val perceptualHash: String?,
    val confidence: Int
)

package com.riccardopinato.notificationcontrol.storage

import android.content.Context
import java.io.File

data class StorageStats(
    val databaseBytes: Long = 0L,
    val mediaBytes: Long = 0L
) {
    val totalBytes: Long get() = databaseBytes + mediaBytes
}

class StorageStatsRepository(context: Context) {
    private val appContext = context.applicationContext

    fun read(): StorageStats {
        val db = appContext.getDatabasePath("notification_control.db")
        val databaseBytes = listOf(
            db,
            File(db.path + "-wal"),
            File(db.path + "-shm")
        ).sumOf { if (it.exists()) it.length() else 0L }

        val mediaDir = File(appContext.filesDir, "notification_thumbnails")
        val mediaBytes = mediaDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

        return StorageStats(databaseBytes = databaseBytes, mediaBytes = mediaBytes)
    }
}

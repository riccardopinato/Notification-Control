package com.riccardopinato.notificationcontrol.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.domain.RetentionPolicy
import java.util.concurrent.TimeUnit

class RetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        val cutoff = RetentionPolicy.cutoffMillis(
            System.currentTimeMillis(),
            settings.isPremium,
            settings.retentionDays
        )
        if (cutoff == Long.MIN_VALUE) return Result.success()

        val dao = NotificationDatabase.get(applicationContext).notificationDao()
        val mediaStore = NotificationMediaStore(applicationContext)

        val mediaToDelete = dao.deleteExpiredAndReturnMedia(cutoff)
        mediaToDelete.forEach(mediaStore::delete)
        mediaStore.cleanupOrphans(dao.allThumbnailPaths())
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "notification_vault_retention"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(2, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}

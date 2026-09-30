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
import com.riccardopinato.notificationcontrol.storage.StorageStatsRepository
import java.util.concurrent.TimeUnit

class RetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        val database = NotificationDatabase.get(applicationContext)
        val cutoff = RetentionPolicy.cutoffMillis(
            System.currentTimeMillis(),
            settings.isPremium,
            settings.retentionDays
        )

        val dao = database.notificationDao()
        val mediaStore = NotificationMediaStore(applicationContext)

        if (cutoff != Long.MIN_VALUE) {
            val mediaToDelete = dao.deleteExpiredAndReturnMedia(cutoff)
            mediaToDelete.forEach(mediaStore::delete)
            mediaStore.cleanupOrphans(dao.allThumbnailPaths())
        }

        if (settings.isPremium && settings.vaultMaxBytes != Long.MAX_VALUE) {
            enforceVaultBudget(
                settings.vaultMaxBytes,
                dao,
                mediaStore
            )
        }

        database.automationDao().cleanupPickupCodes(System.currentTimeMillis())
        return Result.success()
    }

    private suspend fun enforceVaultBudget(
        maxBytes: Long,
        dao: com.riccardopinato.notificationcontrol.data.NotificationDao,
        mediaStore: NotificationMediaStore
    ) {
        repeat(40) {
            val managedBytes =
                dao.approximateNotificationTextBytes() +
                    dao.approximateMessageBytes() +
                    dao.approximateRevisionBytes() +
                    StorageStatsRepository(applicationContext).read().mediaBytes

            if (managedBytes <= maxBytes) return

            val keys = dao.oldestUnprotectedKeys(50)
            if (keys.isEmpty()) return

            dao.deleteByKeysAndReturnMedia(keys).forEach(mediaStore::delete)
        }
        mediaStore.cleanupOrphans(dao.allThumbnailPaths())
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

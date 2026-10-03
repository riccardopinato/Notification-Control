package com.riccardopinato.notificationcontrol.automation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.riccardopinato.notificationcontrol.backup.BackupRepository
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import java.util.concurrent.TimeUnit

object AutomationReconciler {
    suspend fun reconcile(context: Context) {
        val appContext = context.applicationContext
        val database = NotificationDatabase.get(appContext)
        val automationDao = database.automationDao()

        FollowUpScheduler.cancelAll(appContext)
        automationDao.activeFollowUps().forEach { item ->
            FollowUpScheduler.schedule(appContext, item.id, item.dueAt)
        }

        CriticalAlertScheduler.cancelAll(appContext)
        automationDao.activeCriticalAlerts().forEach { item ->
            CriticalAlertScheduler.schedule(appContext, item.id, item.nextAt)
        }

        val referencedMedia =
            database.notificationDao().allThumbnailPaths() +
                database.mediaRecoveryDao().allRescuePaths()
        NotificationMediaStore(appContext).cleanupOrphans(referencedMedia)
    }
}

class AutomationRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            BackupRepository(applicationContext).reconcilePendingRestore()
            AutomationReconciler.reconcile(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}

object AutomationRecoveryScheduler {
    private const val UNIQUE_NAME = "notification_control_automation_recovery"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutomationRecoveryWorker>()
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

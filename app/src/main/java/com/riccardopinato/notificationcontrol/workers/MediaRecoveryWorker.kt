package com.riccardopinato.notificationcontrol.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.riccardopinato.notificationcontrol.capture.MediaRecoveryCoordinator
import java.util.concurrent.TimeUnit

class MediaRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val coordinator = MediaRecoveryCoordinator(applicationContext)
        val remaining = coordinator.resolvePending()
        coordinator.cleanupRescue()
        return if (remaining > 0) Result.retry() else Result.success()
    }
}

object MediaRecoveryScheduler {
    private const val UNIQUE_NAME = "notification_media_recovery"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<MediaRecoveryWorker>()
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
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

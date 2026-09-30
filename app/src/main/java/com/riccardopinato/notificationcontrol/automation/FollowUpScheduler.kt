package com.riccardopinato.notificationcontrol.automation

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object FollowUpScheduler {
    fun schedule(context: Context, followUpId: Long, dueAt: Long) {
        val delayMs = (dueAt - System.currentTimeMillis()).coerceAtLeast(1_000L)
        val request = OneTimeWorkRequestBuilder<FollowUpWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(FollowUpWorker.KEY_FOLLOW_UP_ID, followUpId)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(followUpId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, followUpId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(followUpId))
    }

    private fun workName(id: Long) = "follow_up_$id"
}

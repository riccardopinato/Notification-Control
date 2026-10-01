package com.riccardopinato.notificationcontrol.automation

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CriticalAlertScheduler {
    private const val TAG = "notification_control_critical_alert"

    fun schedule(context: Context, alertId: Long, dueAt: Long) {
        val delay = (dueAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<CriticalAlertWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(CriticalAlertWorker.KEY_ALERT_ID, alertId)
                    .build()
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(alertId),
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, alertId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(alertId))
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    private fun workName(id: Long) = "critical_alert_" + id
}

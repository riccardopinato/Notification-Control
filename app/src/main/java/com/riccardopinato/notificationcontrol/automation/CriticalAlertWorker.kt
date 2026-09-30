package com.riccardopinato.notificationcontrol.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.riccardopinato.notificationcontrol.MainActivity
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.NotificationDatabase

class CriticalAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ALERT_ID, -1L)
        if (id <= 0L) return Result.success()

        val settings = AppSettings(applicationContext)
        if (!settings.isPremium) return Result.success()

        val dao = NotificationDatabase.get(applicationContext).automationDao()
        val entity = dao.criticalAlertById(id) ?: return Result.success()
        if (entity.status != "ACTIVE") return Result.success()

        ensureChannel()
        if (notificationsAllowed()) {
            val intent = Intent(applicationContext, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                (CRITICAL_NOTIFICATION_BASE + (id % 100_000)).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(
                applicationContext,
                CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_notification_control)
                .setContentTitle(
                    applicationContext.getString(R.string.critical_escalation_notification)
                )
                .setContentText(entity.sourceLabel)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(
                (CRITICAL_NOTIFICATION_BASE + (id % 100_000)).toInt(),
                notification
            )
        }

        val nextStep = entity.escalationStep + 1
        val delayMinutes = when (nextStep) {
            1 -> 5
            2 -> 15
            else -> 30
        }
        val nextAt = System.currentTimeMillis() + delayMinutes * 60_000L
        dao.advanceCriticalAlert(
            id = id,
            step = nextStep,
            nextAt = nextAt,
            now = System.currentTimeMillis()
        )
        CriticalAlertScheduler.schedule(applicationContext, id, nextAt)
        return Result.success()
    }

    private fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(applicationContext).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.critical_alert_title),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    companion object {
        const val KEY_ALERT_ID = "critical_alert_id"
        private const val CHANNEL_ID = "notification_control_critical"
        private const val CRITICAL_NOTIFICATION_BASE = 62_000
    }
}

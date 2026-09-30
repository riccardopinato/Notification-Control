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
import com.riccardopinato.notificationcontrol.data.NotificationDatabase

class FollowUpWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_FOLLOW_UP_ID, -1L)
        if (id <= 0L) return Result.success()

        val dao = NotificationDatabase.get(applicationContext).automationDao()
        val entity = dao.followUpById(id) ?: return Result.success()
        if (entity.status != "ACTIVE") return Result.success()

        ensureChannel()

        val notificationAllowed =
            NotificationManagerCompat.from(applicationContext).areNotificationsEnabled() &&
                (
                    Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    )

        if (notificationAllowed) {
            val openIntent = Intent(applicationContext, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                id.toInt(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_control)
                .setContentTitle(entity.title)
                .setContentText(entity.body ?: applicationContext.getString(R.string.follow_up_due))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(entity.body ?: applicationContext.getString(R.string.follow_up_due))
                )
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            NotificationManagerCompat.from(applicationContext)
                .notify((FOLLOW_UP_NOTIFICATION_BASE + (id % 100_000)).toInt(), notification)
        }

        entity.repeatMinutes?.takeIf { it > 0 }?.let { repeat ->
            val next = System.currentTimeMillis() + repeat * 60_000L
            dao.snoozeFollowUp(id, next, System.currentTimeMillis())
            FollowUpScheduler.schedule(applicationContext, id, next)
        }

        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.follow_up_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    companion object {
        const val KEY_FOLLOW_UP_ID = "follow_up_id"
        private const val CHANNEL_ID = "notification_control_follow_up"
        private const val FOLLOW_UP_NOTIFICATION_BASE = 42_000
    }
}

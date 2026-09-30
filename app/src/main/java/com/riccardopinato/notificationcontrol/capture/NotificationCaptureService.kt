package com.riccardopinato.notificationcontrol.capture

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.domain.SuppressionPolicy
import com.riccardopinato.notificationcontrol.hardware.DevicePostureMonitor
import com.riccardopinato.notificationcontrol.hardware.FlashCoordinator
import com.riccardopinato.notificationcontrol.ui.overlay.LuminousCircleOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settings: AppSettings
    private lateinit var parser: NotificationParser
    private lateinit var database: NotificationDatabase
    private lateinit var health: ListenerHealthStore
    private lateinit var posture: DevicePostureMonitor
    private lateinit var suppressionPolicy: SuppressionPolicy
    private lateinit var flash: FlashCoordinator
    private lateinit var overlay: LuminousCircleOverlay

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        parser = NotificationParser(this)
        database = NotificationDatabase.get(this)
        health = ListenerHealthStore(this)
        posture = DevicePostureMonitor(this)
        suppressionPolicy = SuppressionPolicy(this, settings, posture)
        flash = FlashCoordinator.get(this)
        overlay = LuminousCircleOverlay(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        health.connected = true
        health.lastConnectedAt = System.currentTimeMillis()
        posture.start()
        reconcileActiveNotifications()
    }

    override fun onListenerDisconnected() {
        health.connected = false
        posture.stop()
        requestRebind(ComponentName(this, NotificationCaptureService::class.java))
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        health.connected = false
        posture.stop()
        overlay.hide()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (!shouldCapture(notification)) return
        health.lastEventAt = System.currentTimeMillis()
        capture(notification, triggerVisualAlert = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        serviceScope.launch {
            database.notificationDao().markRemoved(notification.key, System.currentTimeMillis(), null)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        val notification = sbn ?: return
        serviceScope.launch {
            database.notificationDao().markRemoved(notification.key, System.currentTimeMillis(), reason)
        }
    }

    private fun reconcileActiveNotifications() {
        serviceScope.launch {
            val notifications = runCatching { activeNotifications.orEmpty().toList() }.getOrDefault(emptyList())
            notifications.filter(::shouldCapture).forEach { capture(it, triggerVisualAlert = false) }
            health.lastReconciliationAt = System.currentTimeMillis()
        }
    }

    private fun shouldCapture(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName || sbn.isOngoing) return false
        return sbn.packageName in settings.monitoredPackages
    }

    private fun capture(sbn: StatusBarNotification, triggerVisualAlert: Boolean) {
        serviceScope.launch {
            val captured = parser.parse(sbn, captureThumbnail = settings.isPremium)
            val dao = database.notificationDao()
            val previous = dao.findByKey(captured.sbnKey)
            dao.upsert(
                captured.toNotificationEntity(previous?.protected == true, previous?.thumbnailPath),
                captured.toMessageEntities()
            )
            if (triggerVisualAlert) triggerLuminous(captured)
        }
    }

    private fun triggerLuminous(captured: CapturedNotification) {
        if (suppressionPolicy.evaluate().suppressed) return
        if (settings.flashEnabled) flash.startStrobe(settings.strobeCycles, settings.strobeSpeedMs, settings.strobeSpeedMs)
        if (settings.overlayEnabled) overlay.show(captured.appLabel)
    }
}

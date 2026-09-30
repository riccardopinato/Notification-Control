package com.riccardopinato.notificationcontrol.capture

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.riccardopinato.notificationcontrol.automation.AutomationRepository
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.domain.CriticalMatcher
import com.riccardopinato.notificationcontrol.domain.PausePingController
import com.riccardopinato.notificationcontrol.domain.RuleEngine
import com.riccardopinato.notificationcontrol.domain.SuppressionPolicy
import com.riccardopinato.notificationcontrol.hardware.DevicePostureMonitor
import com.riccardopinato.notificationcontrol.hardware.FlashCoordinator
import com.riccardopinato.notificationcontrol.processing.CriticalConsumer
import com.riccardopinato.notificationcontrol.processing.FollowUpConsumer
import com.riccardopinato.notificationcontrol.processing.LuminousConsumer
import com.riccardopinato.notificationcontrol.processing.NotificationEventProcessor
import com.riccardopinato.notificationcontrol.processing.NotificationVaultRepository
import com.riccardopinato.notificationcontrol.processing.PausePingConsumer
import com.riccardopinato.notificationcontrol.processing.PickupCodeConsumer
import com.riccardopinato.notificationcontrol.processing.ProcessingMode
import com.riccardopinato.notificationcontrol.processing.RulesConsumer
import com.riccardopinato.notificationcontrol.processing.VaultConsumer
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
    private lateinit var health: ListenerHealthStore
    private lateinit var posture: DevicePostureMonitor
    private lateinit var processor: NotificationEventProcessor
    private lateinit var overlay: LuminousCircleOverlay

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        val mediaStore = NotificationMediaStore(this)
        parser = NotificationParser(this, mediaStore)
        health = ListenerHealthStore(this)
        posture = DevicePostureMonitor(this)

        val database = NotificationDatabase.get(this)
        val vaultRepository = NotificationVaultRepository(
            settings = settings,
            database = database,
            mediaStore = mediaStore
        )
        val automationRepository = AutomationRepository(
            context = this,
            settings = settings,
            dao = database.automationDao()
        )
        val suppressionPolicy = SuppressionPolicy(this, settings, posture)
        val flash = FlashCoordinator.get(this)
        overlay = LuminousCircleOverlay(this)

        processor = NotificationEventProcessor(
            vaultRepository = vaultRepository,
            consumers = listOf(
                VaultConsumer(vaultRepository),
                RulesConsumer(RuleEngine(database.automationDao())),
                CriticalConsumer(CriticalMatcher(database.automationDao())),
                PausePingConsumer(PausePingController(settings)),
                FollowUpConsumer(automationRepository),
                PickupCodeConsumer(vaultRepository, automationRepository),
                LuminousConsumer(
                    settings = settings,
                    suppressionPolicy = suppressionPolicy,
                    flash = flash,
                    overlay = overlay
                )
            )
        )
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
        if (!shouldConsider(notification)) return

        health.lastEventAt = System.currentTimeMillis()
        serviceScope.launch {
            val captured = parser.parse(
                notification,
                captureThumbnail = processor.shouldCaptureThumbnail(notification.packageName)
            )
            processor.process(captured, ProcessingMode.POSTED)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        serviceScope.launch { processor.markRemoved(notification.key, null) }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int
    ) {
        val notification = sbn ?: return
        serviceScope.launch { processor.markRemoved(notification.key, reason) }
    }

    private fun reconcileActiveNotifications() {
        serviceScope.launch {
            val notifications = runCatching { activeNotifications.orEmpty().toList() }
                .getOrDefault(emptyList())
            notifications.filter(::shouldConsider).forEach { sbn ->
                val captured = parser.parse(
                    sbn,
                    captureThumbnail = processor.shouldCaptureThumbnail(sbn.packageName)
                )
                processor.process(captured, ProcessingMode.RECONCILIATION)
            }
            health.lastReconciliationAt = System.currentTimeMillis()
        }
    }

    private fun shouldConsider(sbn: StatusBarNotification): Boolean =
        sbn.packageName != packageName && !sbn.isOngoing
}

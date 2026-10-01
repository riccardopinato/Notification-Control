package com.riccardopinato.notificationcontrol.capture

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.riccardopinato.notificationcontrol.automation.AutomationRepository
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.LuminousProfileDao
import com.riccardopinato.notificationcontrol.data.NotificationDatabase
import com.riccardopinato.notificationcontrol.domain.CriticalMatcher
import com.riccardopinato.notificationcontrol.domain.PausePingController
import com.riccardopinato.notificationcontrol.domain.RuleEngine
import com.riccardopinato.notificationcontrol.domain.RuleRuntimeStateProvider
import com.riccardopinato.notificationcontrol.domain.SuppressionPolicy
import com.riccardopinato.notificationcontrol.hardware.DevicePostureMonitor
import com.riccardopinato.notificationcontrol.hardware.FlashCoordinator
import com.riccardopinato.notificationcontrol.luminous.LuminousProfileResolver
import com.riccardopinato.notificationcontrol.processing.CriticalConsumer
import com.riccardopinato.notificationcontrol.processing.FollowUpConsumer
import com.riccardopinato.notificationcontrol.processing.LuminousConsumer
import com.riccardopinato.notificationcontrol.processing.NotificationEventProcessor
import com.riccardopinato.notificationcontrol.processing.NotificationRuntimeCache
import com.riccardopinato.notificationcontrol.processing.NotificationVaultRepository
import com.riccardopinato.notificationcontrol.processing.PausePingConsumer
import com.riccardopinato.notificationcontrol.processing.PickupCodeConsumer
import com.riccardopinato.notificationcontrol.processing.ProcessingMode
import com.riccardopinato.notificationcontrol.processing.RulesConsumer
import com.riccardopinato.notificationcontrol.ui.overlay.LuminousCircleOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventQueue = Channel<ListenerCommand>(Channel.UNLIMITED)

    private lateinit var settings: AppSettings
    private lateinit var parser: NotificationParser
    private lateinit var health: ListenerHealthStore
    private lateinit var posture: DevicePostureMonitor
    private lateinit var luminousProfileDao: LuminousProfileDao
    private lateinit var runtimeCache: NotificationRuntimeCache
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
        luminousProfileDao = database.luminousProfileDao()
        runtimeCache = NotificationRuntimeCache(
            scope = serviceScope,
            automationDao = database.automationDao(),
            luminousProfileDao = luminousProfileDao
        )

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
                RulesConsumer(
                    ruleEngine = RuleEngine(
                        enabledRulesProvider = runtimeCache::enabledRules,
                        premiumProvider = { settings.isPremium }
                    ),
                    runtimeStateProvider = RuleRuntimeStateProvider(this)
                ),
                CriticalConsumer(
                    matcher = CriticalMatcher(
                        runtimeCache::enabledCriticalPatterns
                    ),
                    automationRepository = automationRepository
                ),
                PausePingConsumer(PausePingController(settings)),
                FollowUpConsumer(automationRepository),
                PickupCodeConsumer(vaultRepository, automationRepository),
                LuminousConsumer(
                    settings = settings,
                    suppressionPolicy = suppressionPolicy,
                    flash = flash,
                    overlay = overlay,
                    profileResolver = LuminousProfileResolver(
                        settings = settings,
                        enabledProfilesProvider =
                            runtimeCache::enabledLuminousProfiles
                    )
                )
            )
        )

        serviceScope.launch {
            for (command in eventQueue) {
                runCatching { processCommand(command) }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        health.connected = true
        health.lastConnectedAt = System.currentTimeMillis()
        eventQueue.trySend(ListenerCommand.Reconcile)
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
        eventQueue.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (!shouldConsider(notification)) return
        health.lastEventAt = System.currentTimeMillis()
        eventQueue.trySend(ListenerCommand.Posted(notification))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        eventQueue.trySend(
            ListenerCommand.Removed(
                platformKey = notification.key,
                reason = null
            )
        )
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int
    ) {
        val notification = sbn ?: return
        eventQueue.trySend(
            ListenerCommand.Removed(
                platformKey = notification.key,
                reason = reason
            )
        )
    }

    private suspend fun processCommand(command: ListenerCommand) {
        when (command) {
            is ListenerCommand.Posted -> {
                updatePostureMonitoring()
                val notification = command.notification
                val captured = parser.parse(
                    notification,
                    captureThumbnail =
                        processor.shouldCaptureThumbnail(notification.packageName)
                )
                processor.process(captured, ProcessingMode.POSTED)
            }

            is ListenerCommand.Removed -> {
                processor.markRemoved(command.platformKey, command.reason)
            }

            ListenerCommand.Reconcile -> {
                updatePostureMonitoring()
                val notifications = runCatching {
                    activeNotifications.orEmpty().toList()
                }.getOrDefault(emptyList())

                notifications
                    .asSequence()
                    .filter(::shouldConsider)
                    .forEach { sbn ->
                        val captured = parser.parse(
                            sbn,
                            captureThumbnail =
                                processor.shouldCaptureThumbnail(sbn.packageName)
                        )
                        processor.process(
                            captured,
                            ProcessingMode.RECONCILIATION
                        )
                    }
                health.lastReconciliationAt = System.currentTimeMillis()
            }
        }
    }

    private suspend fun updatePostureMonitoring() {
        val visualAlertsMayRun =
            settings.flashEnabled ||
                settings.overlayEnabled ||
                (
                    settings.isPremium &&
                        runtimeCache.hasEnabledLuminousProfiles()
                )

        if (visualAlertsMayRun) {
            posture.start()
        } else {
            posture.stop()
        }
    }

    private fun shouldConsider(sbn: StatusBarNotification): Boolean =
        sbn.packageName != packageName && !sbn.isOngoing

    private sealed interface ListenerCommand {
        data class Posted(
            val notification: StatusBarNotification
        ) : ListenerCommand

        data class Removed(
            val platformKey: String,
            val reason: Int?
        ) : ListenerCommand

        data object Reconcile : ListenerCommand
    }
}

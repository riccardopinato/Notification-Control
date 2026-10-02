package com.riccardopinato.notificationcontrol.capture

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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
import android.app.Notification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NotificationCaptureService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventQueue = Channel<ListenerCommand>(Channel.UNLIMITED)

    private lateinit var settings: AppSettings
    private lateinit var parser: NotificationParser
    private lateinit var mediaStore: NotificationMediaStore
    private lateinit var vaultRepository: NotificationVaultRepository
    private lateinit var health: ListenerHealthStore
    private lateinit var posture: DevicePostureMonitor
    private lateinit var luminousProfileDao: LuminousProfileDao
    private lateinit var runtimeCache: NotificationRuntimeCache
    private lateinit var processor: NotificationEventProcessor
    private lateinit var overlay: LuminousCircleOverlay
    private val mediaRecoveryJobs = ConcurrentHashMap<String, Job>()

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        mediaStore = NotificationMediaStore(this)
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

        vaultRepository = NotificationVaultRepository(
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
                    .onFailure {
                        Log.e(
                            TAG,
                            "Notification pipeline command failed",
                            it
                        )
                    }
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
        mediaRecoveryJobs.clear()
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
                val captureThumbnail =
                    processor.shouldCaptureThumbnail(notification.packageName)
                val captured = parser.parse(
                    notification,
                    captureThumbnail = captureThumbnail
                )
                val persisted = processor.process(captured, ProcessingMode.POSTED)
                if (
                    captureThumbnail &&
                    captured.thumbnailPath == null &&
                    persisted.vaultKey != null
                ) {
                    scheduleWhatsAppMediaRecovery(
                        notification = notification,
                        captured = captured,
                        eventKey = persisted.vaultKey
                    )
                }
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

    private fun scheduleWhatsAppMediaRecovery(
        notification: StatusBarNotification,
        captured: CapturedNotification,
        eventKey: String
    ) {
        if (!WhatsAppMediaRecoveryPolicy.supportsPackage(captured.packageName)) return
        if (!mediaStore.canRecoverWhatsAppImages()) return

        val template =
            notification.notification.extras?.getString(Notification.EXTRA_TEMPLATE)
        val hasImageSignal = WhatsAppMediaRecoveryPolicy.hasImageSignal(
            mimeTypes = captured.messages.map { it.mimeType },
            textCandidates = listOf(captured.text, captured.bigText, captured.subText),
            templateName = template
        )
        if (!hasImageSignal) return
        if (mediaRecoveryJobs[eventKey]?.isActive == true) return

        val stableKey =
            NotificationMediaStore.stableKey(captured.sbnKey, captured.postedAt)
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            for (delayMillis in MEDIA_RECOVERY_DELAYS_MS) {
                if (delayMillis > 0L) delay(delayMillis)
                val path = mediaStore.recoverWhatsAppImage(
                    packageName = captured.packageName,
                    postedAt = captured.postedAt,
                    stableKey = stableKey
                )
                if (path != null) {
                    vaultRepository.attachRecoveredThumbnail(eventKey, path)
                    break
                }
            }
        }

        val previous = mediaRecoveryJobs.putIfAbsent(eventKey, job)
        if (previous != null) {
            job.cancel()
            return
        }
        job.invokeOnCompletion {
            mediaRecoveryJobs.remove(eventKey, job)
        }
        job.start()
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
        NotificationAdmissionPolicy.shouldConsider(
            packageName = sbn.packageName,
            selfPackageName = packageName,
            isOngoing = sbn.isOngoing,
            flags = sbn.notification.flags
        )

    companion object {
        private const val TAG = "NotificationCapture"
        private val MEDIA_RECOVERY_DELAYS_MS = longArrayOf(0L, 1_200L, 3_200L)
    }

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

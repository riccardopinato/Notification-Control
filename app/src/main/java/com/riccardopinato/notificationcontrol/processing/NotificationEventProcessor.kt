package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.capture.NotificationActionDeduplicator

class NotificationEventProcessor(
    private val vaultRepository: NotificationVaultRepository,
    private val consumers: List<NotificationEventConsumer>,
    private val actionDeduplicator: NotificationActionDeduplicator =
        NotificationActionDeduplicator()
) {
    fun shouldCaptureThumbnail(packageName: String): Boolean =
        vaultRepository.shouldCaptureThumbnail(packageName)

    suspend fun process(event: CapturedNotification, mode: ProcessingMode) {
        vaultRepository.persist(event)

        if (mode == ProcessingMode.RECONCILIATION) {
            actionDeduplicator.record(event)
            return
        }

        if (!actionDeduplicator.shouldDispatch(event)) return

        val context = ProcessingContext(mode)
        consumers.forEach { it.consume(event, context) }
    }

    suspend fun markRemoved(platformKey: String, reason: Int?) {
        actionDeduplicator.clear(platformKey)
        vaultRepository.markRemoved(platformKey, reason)
    }
}

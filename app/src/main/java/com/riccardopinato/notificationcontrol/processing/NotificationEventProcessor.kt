package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification

class NotificationEventProcessor(
    private val vaultRepository: NotificationVaultRepository,
    private val consumers: List<NotificationEventConsumer>,
    private val deduplicator: NotificationDispatchDeduplicator =
        NotificationDispatchDeduplicator()
) {
    fun shouldCaptureThumbnail(packageName: String): Boolean =
        vaultRepository.shouldCaptureThumbnail(packageName)

    suspend fun process(event: CapturedNotification, mode: ProcessingMode) {
        val allowSideEffects =
            mode != ProcessingMode.POSTED || deduplicator.shouldDispatch(event)
        val context = ProcessingContext(
            mode = mode,
            allowSideEffects = allowSideEffects
        )
        consumers.forEach { it.consume(event, context) }
    }

    suspend fun markRemoved(key: String, reason: Int?) {
        vaultRepository.markRemoved(key, reason)
        deduplicator.clear(key)
    }
}

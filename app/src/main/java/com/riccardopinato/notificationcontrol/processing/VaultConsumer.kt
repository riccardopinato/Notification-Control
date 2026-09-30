package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification

class VaultConsumer(
    private val repository: NotificationVaultRepository
) : NotificationEventConsumer {
    override suspend fun consume(event: CapturedNotification, context: ProcessingContext) {
        context.vaultEventKey = repository.persist(event)
    }
}

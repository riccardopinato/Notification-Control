package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.automation.AutomationRepository
import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.domain.PickupCodeExtractor

class PickupCodeConsumer(
    private val vaultRepository: NotificationVaultRepository,
    private val automationRepository: AutomationRepository
) : NotificationEventConsumer {
    override suspend fun consume(event: CapturedNotification, context: ProcessingContext) {
        if (context.mode != ProcessingMode.POSTED) return
        if (!vaultRepository.shouldPersist(event.packageName)) return
        val candidate = PickupCodeExtractor.extract(event) ?: return
        automationRepository.storePickupCode(event, candidate)
    }
}

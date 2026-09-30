package com.riccardopinato.notificationcontrol.processing

import com.riccardopinato.notificationcontrol.capture.CapturedNotification

interface NotificationEventConsumer {
    suspend fun consume(event: CapturedNotification, context: ProcessingContext)
}

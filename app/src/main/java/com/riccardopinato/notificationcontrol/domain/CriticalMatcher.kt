package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AutomationDao

class CriticalMatcher(private val dao: AutomationDao) {
    suspend fun isCritical(event: CapturedNotification): Boolean =
        dao.enabledCriticalPatterns().any {
            CriticalPatternMatcher.matches(it, event)
        }
}

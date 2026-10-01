package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AutomationDao

class CriticalMatcher(
    private val enabledPatternsProvider: suspend () -> List<com.riccardopinato.notificationcontrol.data.CriticalPatternEntity>
) {
    constructor(dao: AutomationDao) : this(dao::enabledCriticalPatterns)

    suspend fun isCritical(event: CapturedNotification): Boolean =
        enabledPatternsProvider().any {
            CriticalPatternMatcher.matches(it, event)
        }
}

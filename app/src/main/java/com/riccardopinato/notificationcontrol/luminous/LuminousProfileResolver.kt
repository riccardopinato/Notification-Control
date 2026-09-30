package com.riccardopinato.notificationcontrol.luminous

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.LuminousProfileDao
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity

class LuminousProfileResolver(
    private val settings: AppSettings,
    private val dao: LuminousProfileDao
) {
    suspend fun resolve(event: CapturedNotification): LuminousProfileEntity? {
        if (!settings.isPremium) return null
        return dao.enabledProfiles().firstOrNull {
            LuminousProfileMatcher.matches(it, event)
        }
    }
}

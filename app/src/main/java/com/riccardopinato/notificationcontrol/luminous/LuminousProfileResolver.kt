package com.riccardopinato.notificationcontrol.luminous

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.LuminousProfileDao
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity

class LuminousProfileResolver(
    private val settings: AppSettings,
    private val enabledProfilesProvider: suspend () -> List<LuminousProfileEntity>
) {
    constructor(
        settings: AppSettings,
        dao: LuminousProfileDao
    ) : this(settings, dao::enabledProfiles)

    suspend fun resolve(event: CapturedNotification): LuminousProfileEntity? {
        if (!settings.isPremium) return null
        return enabledProfilesProvider().firstOrNull {
            LuminousProfileMatcher.matches(it, event)
        }
    }
}

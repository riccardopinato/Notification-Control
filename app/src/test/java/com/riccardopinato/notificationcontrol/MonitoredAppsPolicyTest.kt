package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.domain.MonitoredAppsPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoredAppsPolicyTest {
    @Test fun freeRejectsFourthApp() {
        val current = setOf("a", "b", "c")
        assertFalse(MonitoredAppsPolicy.toggle(current, "d", premium = false).accepted)
    }
    @Test fun premiumAcceptsFourthApp() {
        val current = setOf("a", "b", "c")
        assertTrue(MonitoredAppsPolicy.toggle(current, "d", premium = true).accepted)
    }
}

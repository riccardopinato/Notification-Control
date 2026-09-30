package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.domain.PausePingController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PausePingControllerTest {
    @Test
    fun repeatedAlertWithinCooldownIsSuppressedButCriticalBypasses() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 20 }
        )

        assertFalse(controller.shouldSuppress("com.example", critical = false, now = 1_000L))
        assertTrue(controller.shouldSuppress("com.example", critical = false, now = 2_000L))
        assertFalse(controller.shouldSuppress("com.example", critical = true, now = 3_000L))
    }
}

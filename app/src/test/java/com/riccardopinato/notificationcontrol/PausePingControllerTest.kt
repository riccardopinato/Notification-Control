package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.domain.PausePingController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PausePingControllerTest {
    @Test
    fun repeatedAlertWithinCooldownIsSuppressedButCriticalBypasses() {
        val settings = AppSettings(RuntimeEnvironment.getApplication())
        settings.pausePingEnabled = true
        settings.pausePingCooldownSeconds = 20
        val controller = PausePingController(settings)

        assertFalse(controller.shouldSuppress("com.example", critical = false, now = 1_000L))
        assertTrue(controller.shouldSuppress("com.example", critical = false, now = 2_000L))
        assertFalse(controller.shouldSuppress("com.example", critical = true, now = 3_000L))
    }
}

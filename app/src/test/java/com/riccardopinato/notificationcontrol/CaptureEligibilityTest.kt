package com.riccardopinato.notificationcontrol

import android.app.Notification
import com.riccardopinato.notificationcontrol.capture.CaptureEligibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureEligibilityTest {
    @Test
    fun groupSummaryIsNotCaptured() {
        assertFalse(
            CaptureEligibility.shouldConsider(
                ownPackageName = "com.riccardopinato.notificationcontrol",
                candidatePackageName = "com.whatsapp",
                isOngoing = false,
                notificationFlags = Notification.FLAG_GROUP_SUMMARY
            )
        )
    }

    @Test
    fun normalWhatsappChildNotificationIsCaptured() {
        assertTrue(
            CaptureEligibility.shouldConsider(
                ownPackageName = "com.riccardopinato.notificationcontrol",
                candidatePackageName = "com.whatsapp",
                isOngoing = false,
                notificationFlags = 0
            )
        )
    }
}

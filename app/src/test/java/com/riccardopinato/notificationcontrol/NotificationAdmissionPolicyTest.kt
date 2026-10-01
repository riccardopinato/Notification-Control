package com.riccardopinato.notificationcontrol

import android.app.Notification
import com.riccardopinato.notificationcontrol.capture.NotificationAdmissionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAdmissionPolicyTest {
    @Test
    fun groupSummaryIsRejected() {
        assertFalse(
            NotificationAdmissionPolicy.shouldConsider(
                packageName = "com.whatsapp",
                selfPackageName = "com.example",
                isOngoing = false,
                flags = Notification.FLAG_GROUP_SUMMARY
            )
        )
    }

    @Test
    fun regularMessageNotificationIsAccepted() {
        assertTrue(
            NotificationAdmissionPolicy.shouldConsider(
                packageName = "com.whatsapp",
                selfPackageName = "com.example",
                isOngoing = false,
                flags = 0
            )
        )
    }

    @Test
    fun ownAndOngoingNotificationsAreRejected() {
        assertFalse(
            NotificationAdmissionPolicy.shouldConsider(
                packageName = "com.example",
                selfPackageName = "com.example",
                isOngoing = false,
                flags = 0
            )
        )
        assertFalse(
            NotificationAdmissionPolicy.shouldConsider(
                packageName = "com.whatsapp",
                selfPackageName = "com.example",
                isOngoing = true,
                flags = 0
            )
        )
    }
}

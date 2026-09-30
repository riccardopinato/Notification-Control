package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.domain.QuietHoursPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursPolicyTest {
    @Test fun overnightWindowWorks() {
        assertTrue(QuietHoursPolicy.isActive(true, 22 * 60, 7 * 60, 23 * 60))
        assertTrue(QuietHoursPolicy.isActive(true, 22 * 60, 7 * 60, 6 * 60))
        assertFalse(QuietHoursPolicy.isActive(true, 22 * 60, 7 * 60, 12 * 60))
    }
}

package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.domain.RetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionPolicyTest {
    @Test fun freeAlwaysUsesSevenDays() {
        val day = 24L * 60L * 60L * 1000L
        assertEquals(10 * day - 7 * day, RetentionPolicy.cutoffMillis(10 * day, premium = false, configuredDays = 30))
    }
    @Test fun premiumForeverDisablesCutoff() {
        assertEquals(Long.MIN_VALUE, RetentionPolicy.cutoffMillis(123L, premium = true, configuredDays = Int.MAX_VALUE))
    }
}

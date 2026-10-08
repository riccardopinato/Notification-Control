package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.domain.OnboardingCompletionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingCompletionPolicyTest {
    @Test
    fun productionStillRequiresRealNotificationAccess() {
        assertFalse(
            OnboardingCompletionPolicy.canFinish(
                notificationAccessGranted = false,
                selectedAppCount = 1,
                qaValidationBuild = false
            )
        )
        assertTrue(
            OnboardingCompletionPolicy.canFinish(
                notificationAccessGranted = true,
                selectedAppCount = 1,
                qaValidationBuild = false
            )
        )
    }

    @Test
    fun qaValidationCanReachFullUiWithoutFakingCapabilityState() {
        assertTrue(
            OnboardingCompletionPolicy.canFinish(
                notificationAccessGranted = false,
                selectedAppCount = 1,
                qaValidationBuild = true
            )
        )
        assertFalse(
            OnboardingCompletionPolicy.canFinish(
                notificationAccessGranted = false,
                selectedAppCount = 0,
                qaValidationBuild = true
            )
        )
    }
}

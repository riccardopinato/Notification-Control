package com.riccardopinato.notificationcontrol.domain

object OnboardingCompletionPolicy {
    fun canFinish(
        notificationAccessGranted: Boolean,
        selectedAppCount: Int,
        qaValidationBuild: Boolean
    ): Boolean =
        selectedAppCount > 0 &&
            (notificationAccessGranted || qaValidationBuild)
}

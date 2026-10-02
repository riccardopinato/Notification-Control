package com.riccardopinato.notificationcontrol.capture

import android.app.Notification

object CaptureEligibility {
    fun shouldConsider(
        ownPackageName: String,
        candidatePackageName: String,
        isOngoing: Boolean,
        notificationFlags: Int
    ): Boolean {
        val isGroupSummary =
            notificationFlags and Notification.FLAG_GROUP_SUMMARY != 0

        return candidatePackageName != ownPackageName &&
            !isOngoing &&
            !isGroupSummary
    }
}

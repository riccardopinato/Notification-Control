package com.riccardopinato.notificationcontrol.capture

import android.app.Notification

object NotificationAdmissionPolicy {
    fun shouldConsider(
        packageName: String,
        selfPackageName: String,
        isOngoing: Boolean,
        flags: Int
    ): Boolean {
        if (packageName == selfPackageName) return false
        if (isOngoing) return false
        if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) return false
        return true
    }
}

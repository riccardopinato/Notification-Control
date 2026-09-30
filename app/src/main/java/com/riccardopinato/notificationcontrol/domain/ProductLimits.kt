package com.riccardopinato.notificationcontrol.domain

object ProductLimits {
    const val FREE_MONITORED_APPS = 3
    const val FREE_RETENTION_DAYS = 7
    val PREMIUM_RETENTION_OPTIONS_DAYS = listOf(1, 3, 7, 30, Int.MAX_VALUE)
}

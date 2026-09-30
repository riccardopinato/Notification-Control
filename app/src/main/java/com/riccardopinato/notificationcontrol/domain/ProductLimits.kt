package com.riccardopinato.notificationcontrol.domain

object ProductLimits {
    const val FREE_MONITORED_APPS = 3
    const val FREE_RETENTION_DAYS = 7
    const val FREE_PROTECTED_ITEMS = 10
    const val FREE_ACTIVE_FOLLOW_UPS = 3
    const val FREE_RULES = 2
    const val FREE_CRITICAL_CONTACTS = 1
    const val FREE_CRITICAL_WORDS = 3
    const val FREE_CRITICAL_APPS = 1

    val PREMIUM_RETENTION_OPTIONS_DAYS = listOf(1, 3, 7, 30, Int.MAX_VALUE)
}

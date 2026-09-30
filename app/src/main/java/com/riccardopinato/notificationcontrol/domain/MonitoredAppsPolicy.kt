package com.riccardopinato.notificationcontrol.domain

object MonitoredAppsPolicy {
    data class Result(val accepted: Boolean, val packages: Set<String>, val reason: String? = null)
    fun toggle(current: Set<String>, packageName: String, premium: Boolean): Result {
        if (packageName in current) return Result(true, current - packageName)
        if (!premium && current.size >= ProductLimits.FREE_MONITORED_APPS) return Result(false, current, "free_limit")
        return Result(true, current + packageName)
    }
}

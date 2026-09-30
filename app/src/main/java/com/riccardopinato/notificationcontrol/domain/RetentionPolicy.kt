package com.riccardopinato.notificationcontrol.domain

object RetentionPolicy {
    fun cutoffMillis(nowMillis: Long, premium: Boolean, configuredDays: Int): Long {
        val days = if (premium) configuredDays else ProductLimits.FREE_RETENTION_DAYS
        if (days == Int.MAX_VALUE) return Long.MIN_VALUE
        return nowMillis - days.toLong() * 24L * 60L * 60L * 1000L
    }
}

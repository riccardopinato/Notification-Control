package com.riccardopinato.notificationcontrol.capture

object MediaRescueRetentionPolicy {
    const val MINIMUM_DAYS = 30

    fun daysForPackage(
        packageName: String,
        globalDays: Int,
        perAppDays: Map<String, Int>
    ): Int {
        val configured = perAppDays[packageName] ?: globalDays
        return if (configured == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            maxOf(MINIMUM_DAYS, configured)
        }
    }
}

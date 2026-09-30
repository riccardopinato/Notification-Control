package com.riccardopinato.notificationcontrol.domain

import java.util.Calendar

object QuietHoursPolicy {
    fun isActive(enabled: Boolean, startMinutes: Int, endMinutes: Int, nowMinutes: Int): Boolean {
        if (!enabled) return false
        if (startMinutes == endMinutes) return true
        return if (startMinutes < endMinutes) nowMinutes in startMinutes until endMinutes else nowMinutes >= startMinutes || nowMinutes < endMinutes
    }
    fun nowMinutes(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }
}

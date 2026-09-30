package com.riccardopinato.notificationcontrol.domain

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryPolicy {
    fun shouldSuppress(context: Context, enabled: Boolean, threshold: Int): Boolean {
        if (!enabled) return false
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0
        if (charging) return false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return false
        return level * 100 / scale <= threshold
    }
}

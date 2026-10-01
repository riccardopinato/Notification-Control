package com.riccardopinato.notificationcontrol.domain

import android.content.Context
import android.os.PowerManager

class RuleRuntimeStateProvider(context: Context) {
    private val powerManager =
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun current(): RuleRuntimeState =
        RuleRuntimeState(
            minuteOfDay = QuietHoursPolicy.nowMinutes(),
            screenInteractive = powerManager.isInteractive
        )
}

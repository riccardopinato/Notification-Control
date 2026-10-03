package com.riccardopinato.notificationcontrol

import android.app.Application
import androidx.work.Configuration
import com.riccardopinato.notificationcontrol.automation.AutomationRecoveryScheduler
import com.riccardopinato.notificationcontrol.capture.ListenerHealthStore

class NotificationControlApplication :
    Application(),
    Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        ListenerHealthStore(this).connected = false
        AutomationRecoveryScheduler.enqueue(this)
    }
}

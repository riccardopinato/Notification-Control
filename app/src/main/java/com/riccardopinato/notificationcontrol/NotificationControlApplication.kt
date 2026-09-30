package com.riccardopinato.notificationcontrol

import android.app.Application
import com.riccardopinato.notificationcontrol.billing.PlayBillingManager
import com.riccardopinato.notificationcontrol.capture.ListenerHealthStore
import com.riccardopinato.notificationcontrol.workers.RetentionWorker

class NotificationControlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ListenerHealthStore(this).connected = false
        RetentionWorker.schedule(this)
        PlayBillingManager.get(this).connect()
    }
}

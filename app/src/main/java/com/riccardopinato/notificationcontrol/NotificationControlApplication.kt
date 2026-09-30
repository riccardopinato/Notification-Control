package com.riccardopinato.notificationcontrol

import android.app.Application
import com.riccardopinato.notificationcontrol.workers.RetentionWorker

class NotificationControlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RetentionWorker.schedule(this)
    }
}

package com.riccardopinato.notificationcontrol

import android.app.Application
import androidx.work.Configuration
import com.riccardopinato.notificationcontrol.backup.BackupRepository
import com.riccardopinato.notificationcontrol.capture.ListenerHealthStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationControlApplication :
    Application(),
    Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        ListenerHealthStore(this).connected = false
        applicationScope.launch {
            runCatching {
                BackupRepository(this@NotificationControlApplication)
                    .reconcileInterruptedRecoveryPoint()
            }
        }
    }
}

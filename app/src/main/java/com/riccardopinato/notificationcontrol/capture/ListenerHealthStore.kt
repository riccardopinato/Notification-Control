package com.riccardopinato.notificationcontrol.capture

import android.content.Context
import androidx.core.content.edit

class ListenerHealthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("listener_health", Context.MODE_PRIVATE)

    var connected: Boolean
        get() = prefs.getBoolean("connected", false)
        set(value) = prefs.edit { putBoolean("connected", value) }

    var lastConnectedAt: Long
        get() = prefs.getLong("last_connected_at", 0L)
        set(value) = prefs.edit { putLong("last_connected_at", value) }

    var lastEventAt: Long
        get() = prefs.getLong("last_event_at", 0L)
        set(value) = prefs.edit { putLong("last_event_at", value) }

    var lastReconciliationAt: Long
        get() = prefs.getLong("last_reconciliation_at", 0L)
        set(value) = prefs.edit { putLong("last_reconciliation_at", value) }
}

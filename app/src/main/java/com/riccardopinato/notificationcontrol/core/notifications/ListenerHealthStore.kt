package com.riccardopinato.notificationcontrol.core.notifications

import android.content.Context

enum class ListenerHealthState { NEVER_CONNECTED, CONNECTED, DISCONNECTED, REBINDING }

class ListenerHealthStore(context: Context) {
    private val prefs = context.getSharedPreferences("notification_listener_health", Context.MODE_PRIVATE)

    var state: ListenerHealthState
        get() = runCatching {
            ListenerHealthState.valueOf(
                prefs.getString("state", ListenerHealthState.NEVER_CONNECTED.name)
                    ?: ListenerHealthState.NEVER_CONNECTED.name
            )
        }.getOrDefault(ListenerHealthState.NEVER_CONNECTED)
        set(value) = prefs.edit().putString("state", value.name).apply()

    var lastConnectedAt: Long
        get() = prefs.getLong("last_connected_at", 0L)
        set(value) = prefs.edit().putLong("last_connected_at", value).apply()

    var lastEventAt: Long
        get() = prefs.getLong("last_event_at", 0L)
        set(value) = prefs.edit().putLong("last_event_at", value).apply()
}

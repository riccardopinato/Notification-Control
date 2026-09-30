package com.riccardopinato.notificationcontrol.security

import android.content.Context
import android.os.SystemClock
import com.riccardopinato.notificationcontrol.data.AppSettings
import java.util.concurrent.atomic.AtomicLong

class VaultSecurityManager(context: Context) {
    private val settings = AppSettings(context)

    fun isEnabled(): Boolean = settings.vaultLockEnabled

    fun isLocked(): Boolean =
        settings.vaultLockEnabled && SystemClock.elapsedRealtime() >= unlockedUntilElapsed.get()

    fun markUnlocked() {
        val duration = settings.vaultLockTimeoutMinutes.coerceIn(1, 60) * 60_000L
        unlockedUntilElapsed.set(SystemClock.elapsedRealtime() + duration)
    }

    fun lock() {
        unlockedUntilElapsed.set(0L)
    }

    fun setEnabled(enabled: Boolean) {
        settings.vaultLockEnabled = enabled
        if (enabled) lock() else unlockedUntilElapsed.set(Long.MAX_VALUE)
    }

    companion object {
        private val unlockedUntilElapsed = AtomicLong(0L)
    }
}

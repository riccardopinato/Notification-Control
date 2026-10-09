package com.riccardopinato.notificationcontrol.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.riccardopinato.notificationcontrol.capture.ListenerHealthStore
import com.riccardopinato.notificationcontrol.capture.NotificationMediaStore
import com.riccardopinato.notificationcontrol.capture.WhatsAppSafMediaSource
import com.riccardopinato.notificationcontrol.data.AppSettings

enum class PhotoAccessScope {
    FULL,
    SELECTED_ONLY,
    NONE
}

internal fun resolvePhotoAccessScope(
    sdkInt: Int,
    fullGranted: Boolean,
    selectedGranted: Boolean
): PhotoAccessScope = when {
    fullGranted -> PhotoAccessScope.FULL
    sdkInt >= 34 && selectedGranted -> PhotoAccessScope.SELECTED_ONLY
    else -> PhotoAccessScope.NONE
}

data class PhysicalQaSnapshot(
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val notificationListenerEnabled: Boolean,
    val notificationListenerConnected: Boolean,
    val lastListenerConnectedAt: Long,
    val lastNotificationEventAt: Long,
    val lastReconciliationAt: Long,
    val photoAccessScope: PhotoAccessScope,
    val whatsAppSafLinked: Boolean,
    val mediaStoreVersion: String?,
    val mediaStoreGeneration: Long?,
    val premium: Boolean,
    val runtimePerformance: RuntimePerformanceSnapshot
)

class PhysicalQaProbe(context: Context) {
    private val appContext = context.applicationContext

    fun snapshot(): PhysicalQaSnapshot {
        val settings = AppSettings(appContext)
        val health = ListenerHealthStore(appContext)
        val fullPhotoGranted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            else ->
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
        }
        val selectedGranted =
            Build.VERSION.SDK_INT >= 34 &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED
        val photoScope = resolvePhotoAccessScope(
            sdkInt = Build.VERSION.SDK_INT,
            fullGranted = fullPhotoGranted,
            selectedGranted = selectedGranted
        )

        val safLinked = settings.whatsAppMediaTreeUri
            ?.let { WhatsAppSafMediaSource(appContext).hasPersistedAccess(it) }
            ?: false

        val mediaSnapshot = if (photoScope == PhotoAccessScope.FULL) {
            NotificationMediaStore(appContext).snapshot()
        } else {
            null
        }

        return PhysicalQaSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            notificationListenerEnabled =
                NotificationManagerCompat.getEnabledListenerPackages(appContext)
                    .contains(appContext.packageName),
            notificationListenerConnected = health.connected,
            lastListenerConnectedAt = health.lastConnectedAt,
            lastNotificationEventAt = health.lastEventAt,
            lastReconciliationAt = health.lastReconciliationAt,
            photoAccessScope = photoScope,
            whatsAppSafLinked = safLinked,
            mediaStoreVersion = mediaSnapshot?.version,
            mediaStoreGeneration = mediaSnapshot?.generation,
            premium = settings.isPremium,
            runtimePerformance = RuntimePerformanceTelemetry.snapshot()
        )
    }
}

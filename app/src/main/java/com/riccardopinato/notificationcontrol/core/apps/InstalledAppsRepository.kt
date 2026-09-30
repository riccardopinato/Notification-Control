package com.riccardopinato.notificationcontrol.core.apps

import android.content.Context
import android.content.Intent

data class InstalledApp(
    val packageName: String,
    val label: String
)

class InstalledAppsRepository(private val context: Context) {
    fun launcherApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                InstalledApp(pkg, info.loadLabel(context.packageManager)?.toString() ?: pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

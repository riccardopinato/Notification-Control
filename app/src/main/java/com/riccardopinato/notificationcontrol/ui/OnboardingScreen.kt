package com.riccardopinato.notificationcontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.domain.ProductLimits

@Composable
fun OnboardingScreen(settings: SettingsUiState, apps: List<InstalledApp>, onToggleApp: (String) -> Boolean, requestNotificationAccess: () -> Unit, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val access = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    when (step) {
        0 -> OnboardingPage(stringResource(R.string.welcome_title), stringResource(R.string.welcome_body), { Icon(Icons.Default.NotificationsActive, null) }, stringResource(R.string.start)) { step = 1 }
        1 -> OnboardingPage(
            stringResource(R.string.notification_access_title), stringResource(R.string.notification_access_body), { Icon(Icons.Default.Security, null) },
            if (access) stringResource(R.string.continue_label) else stringResource(R.string.grant_access),
            if (access) stringResource(R.string.access_granted) else stringResource(R.string.privacy_local_body)
        ) { if (access) step = 2 else requestNotificationAccess() }
        2 -> AppSelectionPage(apps, settings.monitoredPackages, settings.isPremium, onToggleApp) { step = 3 }
        else -> OnboardingPage(stringResource(R.string.security_title), stringResource(R.string.security_body), { Icon(Icons.Default.Security, null) }, stringResource(R.string.finish_setup), stringResource(R.string.privacy_local_title), onDone)
    }
}

@Composable
private fun OnboardingPage(title: String, body: String, icon: @Composable () -> Unit, button: String, supporting: String? = null, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        icon(); Spacer(Modifier.height(20.dp)); Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp)); Text(body, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) { Spacer(Modifier.height(16.dp)); Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text(supporting, Modifier.padding(16.dp)) } }
        Spacer(Modifier.height(28.dp)); Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(button) }
    }
}

@Composable
private fun AppSelectionPage(apps: List<InstalledApp>, selected: Set<String>, premium: Boolean, onToggle: (String) -> Boolean, onNext: () -> Unit) {
    var limitError by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(stringResource(R.string.choose_apps_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.choose_apps_body, ProductLimits.FREE_MONITORED_APPS))
        Text(stringResource(R.string.selected_count, selected.size, if (premium) apps.size else ProductLimits.FREE_MONITORED_APPS), fontWeight = FontWeight.SemiBold)
        if (limitError) Text(stringResource(R.string.free_limit_reached, ProductLimits.FREE_MONITORED_APPS), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(app.packageName, style = MaterialTheme.typography.labelSmall) }
                    Switch(app.packageName in selected, onCheckedChange = { limitError = !onToggle(app.packageName) })
                }
                HorizontalDivider()
            }
        }
        Button(onClick = onNext, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.continue_label)) }
    }
}

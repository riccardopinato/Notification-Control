package com.riccardopinato.notificationcontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.capture.ListenerHealthStore
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(modifier: Modifier, contentPadding: PaddingValues, settings: SettingsUiState, count: Int, requestNotificationAccess: () -> Unit, onConfigureApps: () -> Unit) {
    val context = LocalContext.current
    val permission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val health = remember { ListenerHealthStore(context) }
    LazyColumn(modifier.fillMaxSize().padding(contentPadding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(stringResource(R.string.home_subtitle)) }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(20.dp)) { Text(stringResource(R.string.vault_saved, count), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(stringResource(R.string.apps_monitored, settings.monitoredPackages.size)) } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.listener_health), fontWeight = FontWeight.Bold)
            val status = when { health.connected -> stringResource(R.string.listener_connected); permission -> stringResource(R.string.listener_permission_only); else -> stringResource(R.string.listener_off) }
            Text(status, color = if (permission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (!permission) TextButton(onClick = requestNotificationAccess) { Text(stringResource(R.string.open_notification_access)) }
        } } }
        item { FilledTonalButton(onClick = onConfigureApps, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.configure_apps)) } }
    }
}

@Composable
fun VaultScreen(modifier: Modifier, contentPadding: PaddingValues, notifications: List<NotificationEntity>, onProtect: (String, Boolean) -> Unit) {
    LazyColumn(modifier.fillMaxSize().padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.vault_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (notifications.isEmpty()) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(stringResource(R.string.vault_empty), fontWeight = FontWeight.Bold); Text(stringResource(R.string.vault_empty_hint)) } } }
        items(notifications, key = { it.sbnKey }) { n -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(n.appLabel, fontWeight = FontWeight.Bold); Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(n.updatedAt)), style = MaterialTheme.typography.labelMedium) }
            n.title?.takeIf { it.isNotBlank() }?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            val content = n.bigText?.takeIf { it.isNotBlank() } ?: n.text
            content?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            if (n.removedAt != null) Text(stringResource(R.string.removed), style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { onProtect(n.sbnKey, !n.protected) }) { Text(if (n.protected) stringResource(R.string.unprotect) else stringResource(R.string.protect)) }
        } } }
    }
}

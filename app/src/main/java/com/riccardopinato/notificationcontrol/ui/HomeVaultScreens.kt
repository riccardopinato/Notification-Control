package com.riccardopinato.notificationcontrol.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.capture.ListenerHealthStore
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.VaultAppFilter
import com.riccardopinato.notificationcontrol.storage.StorageStats
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    settings: SettingsUiState,
    count: Int,
    requestNotificationAccess: () -> Unit,
    onConfigureApps: () -> Unit
) {
    val context = LocalContext.current
    val permission = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
    val health = remember { ListenerHealthStore(context) }

    LazyColumn(
        modifier.fillMaxSize().padding(contentPadding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.home_subtitle))
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.vault_saved, count),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(stringResource(R.string.apps_monitored, settings.monitoredPackages.size))
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.listener_health), fontWeight = FontWeight.Bold)
                    val status = when {
                        health.connected -> stringResource(R.string.listener_connected)
                        permission -> stringResource(R.string.listener_permission_only)
                        else -> stringResource(R.string.listener_off)
                    }
                    Text(
                        status,
                        color = if (permission) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    if (!permission) {
                        TextButton(onClick = requestNotificationAccess) {
                            Text(stringResource(R.string.open_notification_access))
                        }
                    }
                }
            }
        }
        item {
            FilledTonalButton(
                onClick = onConfigureApps,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.configure_apps))
            }
        }
    }
}

@Composable
fun VaultLockedScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    onUnlock: () -> Unit
) {
    Box(
        modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.vault_locked_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.vault_locked_body),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Button(onClick = onUnlock) {
                    Text(stringResource(R.string.unlock_vault))
                }
            }
        }
    }
}

@Composable
fun VaultScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    notifications: List<NotificationEntity>,
    search: String,
    packageFilter: String?,
    appFilters: List<VaultAppFilter>,
    storageStats: StorageStats,
    currentLimit: Int,
    onSearchChange: (String) -> Unit,
    onPackageFilterChange: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onProtect: (String, Boolean) -> Unit
) {
    LazyColumn(
        modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                stringResource(R.string.vault_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.search_vault)) }
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = packageFilter == null,
                        onClick = { onPackageFilterChange(null) },
                        label = { Text(stringResource(R.string.all_apps)) }
                    )
                }
                items(appFilters, key = { it.packageName }) { app ->
                    FilterChip(
                        selected = packageFilter == app.packageName,
                        onClick = {
                            onPackageFilterChange(
                                if (packageFilter == app.packageName) null else app.packageName
                            )
                        },
                        label = { Text(app.appLabel + " · " + app.count) }
                    )
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.vault_storage))
                    Text(formatBytes(storageStats.totalBytes), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (notifications.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(stringResource(R.string.vault_empty), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.vault_empty_hint))
                    }
                }
            }
        }

        items(notifications, key = { it.sbnKey }) { n ->
            NotificationVaultCard(n, onProtect)
        }

        if (notifications.size >= currentLimit) {
            item {
                FilledTonalButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.load_more))
                }
            }
        }
    }
}

@Composable
private fun NotificationVaultCard(
    notification: NotificationEntity,
    onProtect: (String, Boolean) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            notification.thumbnailPath?.let { Thumbnail(it) }
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(notification.appLabel, fontWeight = FontWeight.Bold)
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(notification.updatedAt)),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                notification.title?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontWeight = FontWeight.SemiBold)
                }
                val content = notification.bigText?.takeIf { it.isNotBlank() }
                    ?: notification.text
                content?.takeIf { it.isNotBlank() }?.let {
                    Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                if (notification.removedAt != null) {
                    Text(
                        stringResource(R.string.removed),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(
                    onClick = {
                        onProtect(notification.sbnKey, !notification.protected)
                    }
                ) {
                    Text(
                        if (notification.protected) {
                            stringResource(R.string.unprotect)
                        } else {
                            stringResource(R.string.protect)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(path: String) {
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            contentScale = ContentScale.Crop
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1_024) return "$bytes B"
    val kb = bytes / 1_024.0
    if (kb < 1_024) return String.format("%.1f KB", kb)
    val mb = kb / 1_024.0
    return String.format("%.1f MB", mb)
}

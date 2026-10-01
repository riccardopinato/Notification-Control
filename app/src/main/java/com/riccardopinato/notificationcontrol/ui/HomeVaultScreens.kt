package com.riccardopinato.notificationcontrol.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.capture.ListenerHealthStore
import com.riccardopinato.notificationcontrol.data.CriticalAlertEntity
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.PickupCodeEntity
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
    criticalAlerts: List<CriticalAlertEntity>,
    pickupCodes: List<PickupCodeEntity>,
    sensitiveLocked: Boolean,
    requestNotificationAccess: () -> Unit,
    onUnlockSensitive: () -> Unit,
    onHandleCritical: (Long) -> Unit,
    onDismissPickup: (Long) -> Unit,
    onConfigureApps: () -> Unit
) {
    val context = LocalContext.current
    val permission = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
    val health = remember { ListenerHealthStore(context) }
    val activeCode = pickupCodes.firstOrNull {
        !it.dismissed && it.expiresAt > System.currentTimeMillis()
    }

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
        criticalAlerts.firstOrNull()?.let { alert ->
            item {
                CriticalAlertCard(
                    alert = alert,
                    sensitiveLocked = sensitiveLocked,
                    onHandle = { onHandleCritical(alert.id) }
                )
            }
        }

        activeCode?.let { code ->
            item {
                if (sensitiveLocked) {
                    SensitiveContentLockedCard(onUnlockSensitive)
                } else {
                    PickupCodeCard(
                        code = code,
                        onCopy = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.pickup_code),
                                code.code
                            )
                        )
                        },
                        onDismiss = { onDismissPickup(code.id) }
                    )
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
private fun CriticalAlertCard(
    alert: CriticalAlertEntity,
    sensitiveLocked: Boolean,
    onHandle: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                stringResource(R.string.critical_pending_title),
                fontWeight = FontWeight.Bold
            )
            if (!sensitiveLocked) {
                Text(alert.sourceLabel, fontWeight = FontWeight.SemiBold)
                alert.title?.takeIf { it.isNotBlank() }?.let { Text(it) }
            } else {
                Text(stringResource(R.string.critical_pending_locked))
            }
            Text(
                stringResource(
                    R.string.critical_next_escalation,
                    DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Date(alert.nextAt))
                ),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onHandle) {
                Text(stringResource(R.string.mark_handled))
            }
        }
    }
}

@Composable
private fun SensitiveContentLockedCard(onUnlock: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                stringResource(R.string.sensitive_content_locked),
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.sensitive_content_locked_body))
            TextButton(onClick = onUnlock) {
                Text(stringResource(R.string.unlock_vault))
            }
        }
    }
}

@Composable
private fun PickupCodeCard(
    code: PickupCodeEntity,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                stringResource(R.string.pickup_code),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                code.code,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(code.sourceLabel, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy) {
                    Text(stringResource(R.string.copy))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
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
    notifications: LazyPagingItems<NotificationEntity>,
    search: String,
    packageFilter: String?,
    appFilters: List<VaultAppFilter>,
    storageStats: StorageStats,
    onSearchChange: (String) -> Unit,
    onPackageFilterChange: (String?) -> Unit,
    onProtect: (String, Boolean) -> Unit,
    onFollowUp: (NotificationEntity) -> Unit,
    onOpenDetail: (NotificationEntity) -> Unit
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

        when (val refresh = notifications.loadState.refresh) {
            is LoadState.Loading -> {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is LoadState.Error -> {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            refresh.error.localizedMessage
                                ?: stringResource(R.string.vault_empty_hint),
                            Modifier.padding(18.dp)
                        )
                    }
                }
            }

            is LoadState.NotLoading -> {
                if (notifications.itemCount == 0) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text(
                                    stringResource(R.string.vault_empty),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(stringResource(R.string.vault_empty_hint))
                            }
                        }
                    }
                }
            }
        }

        items(
            count = notifications.itemCount,
            key = { index ->
                notifications.peek(index)?.sbnKey ?: "vault-item-$index"
            }
        ) { index ->
            notifications[index]?.let { notification ->
                NotificationVaultCard(
                    notification,
                    onProtect,
                    onFollowUp,
                    onOpenDetail
                )
            }
        }

        if (notifications.loadState.append is LoadState.Loading) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun NotificationVaultCard(
    notification: NotificationEntity,
    onProtect: (String, Boolean) -> Unit,
    onFollowUp: (NotificationEntity) -> Unit,
    onOpenDetail: (NotificationEntity) -> Unit
) {
    val formattedTime = remember(notification.updatedAt) {
        DateFormat.getTimeInstance(DateFormat.SHORT)
            .format(Date(notification.updatedAt))
    }

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
                        formattedTime,
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
                Row {
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
                    TextButton(onClick = { onFollowUp(notification) }) {
                        Text(stringResource(R.string.follow_up))
                    }
                    TextButton(onClick = { onOpenDetail(notification) }) {
                        Text(stringResource(R.string.vault_details))
                    }
                }
            }
        }
    }
}

private object ThumbnailMemoryCache {
    private const val MAX_KB = 8 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
    }

    fun get(path: String): ImageBitmap? = cache.get(path)

    fun put(path: String, image: ImageBitmap) {
        cache.put(path, image)
    }
}

@Composable
private fun Thumbnail(path: String) {
    val cached = remember(path) { ThumbnailMemoryCache.get(path) }
    val image by produceState<ImageBitmap?>(initialValue = cached, path) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()?.also {
                        ThumbnailMemoryCache.put(path, it)
                    }
                }.getOrNull()
            }
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

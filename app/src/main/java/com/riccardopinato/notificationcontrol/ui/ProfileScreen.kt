package com.riccardopinato.notificationcontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.domain.ProductLimits

@Composable
fun ProfileScreen(modifier: Modifier, contentPadding: PaddingValues, state: SettingsUiState, onConfigureApps: () -> Unit, onDeleteAll: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().padding(contentPadding).padding(16.dp)) {
        item { Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { InfoCard(stringResource(R.string.account_title), stringResource(R.string.account_optional)) }
        item { InfoCard(stringResource(R.string.language_title), stringResource(R.string.language_device)) }
        item { InfoCard(stringResource(R.string.storage_title), stringResource(R.string.storage_free)) }
        item { InfoCard(stringResource(R.string.privacy_title), stringResource(R.string.privacy_body)) }
        item { Button(onClick = onConfigureApps, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.configure_apps)) } }
        item { FilledTonalButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.delete_vault)) } }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.delete_vault)) }, text = { Text(stringResource(R.string.delete_vault_confirm)) }, confirmButton = { TextButton(onClick = { onDeleteAll(); confirmDelete = false }) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun InfoCard(title: String, body: String) { Card(Modifier.fillMaxWidth().padding(top = 8.dp)) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(body) } } }
@Composable
fun AppPickerDialog(apps: List<InstalledApp>, selected: Set<String>, premium: Boolean, onToggle: (String) -> Boolean, onDismiss: () -> Unit) {
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_apps_title)) },
        text = { LazyColumn(Modifier.height(420.dp)) {
            items(apps, key = { it.packageName }) { app -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Switch(checked = app.packageName in selected, onCheckedChange = { error = !onToggle(app.packageName) }) } }
            if (error && !premium) item { Text(stringResource(R.string.free_limit_reached, ProductLimits.FREE_MONITORED_APPS), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

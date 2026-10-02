package com.riccardopinato.notificationcontrol.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.data.SavedVaultFilter
import com.riccardopinato.notificationcontrol.data.VaultAppFilter
import com.riccardopinato.notificationcontrol.export.VaultReadableExportRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VaultProductivitySection(
    search: String,
    packageFilter: String?,
    appFilters: List<VaultAppFilter>,
    isPremium: Boolean,
    onSearchChange: (String) -> Unit,
    onPackageFilterChange: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SavedVaultFiltersCard(
            search = search,
            packageFilter = packageFilter,
            appFilters = appFilters,
            isPremium = isPremium,
            onSearchChange = onSearchChange,
            onPackageFilterChange = onPackageFilterChange
        )
        ReadableVaultExportCard(isPremium = isPremium)
    }
}

@Composable
private fun SavedVaultFiltersCard(
    search: String,
    packageFilter: String?,
    appFilters: List<VaultAppFilter>,
    isPremium: Boolean,
    onSearchChange: (String) -> Unit,
    onPackageFilterChange: (String?) -> Unit
) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context) }
    var filters by remember { mutableStateOf(settings.savedVaultFilters) }
    var naming by remember { mutableStateOf(false) }
    var filterName by remember { mutableStateOf("") }

    val hasCriteria = search.isNotBlank() || packageFilter != null
    val currentAppLabel = appFilters.firstOrNull { it.packageName == packageFilter }?.appLabel

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.vault_saved_filters_title),
                    fontWeight = FontWeight.SemiBold
                )
                if (isPremium) {
                    TextButton(
                        onClick = {
                            filterName = search.trim().takeIf(String::isNotBlank)
                                ?: currentAppLabel.orEmpty()
                            naming = true
                        },
                        enabled = hasCriteria
                    ) {
                        Text(stringResource(R.string.vault_saved_filter_save))
                    }
                }
            }

            if (!isPremium) {
                Text(
                    stringResource(R.string.vault_saved_filters_premium_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (filters.isEmpty()) {
                Text(
                    stringResource(R.string.vault_saved_filters_empty),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filters, key = { it.id }) { filter ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected =
                                    filter.query == search.trim() &&
                                        filter.packageName == packageFilter,
                                onClick = {
                                    onSearchChange(filter.query)
                                    onPackageFilterChange(filter.packageName)
                                },
                                label = { Text(filter.name) }
                            )
                            IconButton(
                                onClick = {
                                    filters = filters.filterNot { it.id == filter.id }
                                    settings.savedVaultFilters = filters
                                }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(
                                        R.string.vault_saved_filter_delete
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.vault_saved_filter_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = filterName,
                    onValueChange = { filterName = it.take(80) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.vault_saved_filter_name)) }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = filterName.isNotBlank() && hasCriteria,
                    onClick = {
                        val normalizedName = filterName.trim()
                        val existing = filters.firstOrNull {
                            it.name.equals(normalizedName, ignoreCase = true)
                        }
                        val next = SavedVaultFilter(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = normalizedName,
                            query = search.trim(),
                            packageName = packageFilter
                        )
                        filters = (
                            filters.filterNot { it.id == next.id } + next
                        ).sortedBy { it.name }
                        settings.savedVaultFilters = filters
                        naming = false
                    }
                ) {
                    Text(stringResource(R.string.vault_saved_filter_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { naming = false }) {
                    Text(stringResource(R.string.vault_saved_filter_cancel))
                }
            }
        )
    }
}

@Composable
private fun ReadableVaultExportCard(isPremium: Boolean) {
    val context = LocalContext.current
    val repository = remember(context) { VaultReadableExportRepository(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { repository.exportCsv(uri) }
            status = result.fold(
                onSuccess = {
                    context.getString(R.string.vault_export_success, it.notifications)
                },
                onFailure = {
                    context.getString(R.string.vault_export_failed)
                }
            )
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { repository.exportJson(uri) }
            status = result.fold(
                onSuccess = {
                    context.getString(R.string.vault_export_success, it.notifications)
                },
                onFailure = {
                    context.getString(R.string.vault_export_failed)
                }
            )
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.vault_export_title),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.vault_export_body),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(R.string.vault_export_plaintext_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        csvLauncher.launch(
                            "notification-control-vault-" + exportStamp() + ".csv"
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.vault_export_csv))
                }
                Button(
                    onClick = {
                        jsonLauncher.launch(
                            "notification-control-vault-" + exportStamp() + ".json"
                        )
                    },
                    enabled = isPremium,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.vault_export_json))
                }
            }
            if (!isPremium) {
                Text(
                    stringResource(R.string.vault_export_json_premium_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun exportStamp(): String =
    SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())

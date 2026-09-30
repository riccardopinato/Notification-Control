package com.riccardopinato.notificationcontrol.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riccardopinato.notificationcontrol.R

@Composable
fun SearchableAppPickerButton(
    apps: List<InstalledApp>,
    selected: InstalledApp?,
    placeholder: String,
    allowNone: Boolean,
    onSelected: (InstalledApp?) -> Unit
) {
    var open by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(selected?.label ?: placeholder)
    }

    if (!open) return

    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(normalized, ignoreCase = true) ||
                    it.packageName.contains(normalized, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.choose_app)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_apps)) }
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (allowNone) {
                        item {
                            TextButton(
                                onClick = {
                                    onSelected(null)
                                    open = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(placeholder)
                            }
                        }
                    }
                    items(filtered, key = { it.packageName }) { app ->
                        TextButton(
                            onClick = {
                                onSelected(app)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(app.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { open = false }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

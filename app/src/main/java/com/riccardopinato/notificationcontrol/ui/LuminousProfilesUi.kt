package com.riccardopinato.notificationcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity

@Composable
fun LuminousProfilesSection(
    state: SettingsUiState,
    apps: List<InstalledApp>,
    profiles: List<LuminousProfileEntity>,
    onCreate: (
        String,
        String?,
        String?,
        String,
        Boolean,
        Boolean,
        Int,
        Long,
        Float,
        Float,
        Long,
        Long
    ) -> Unit,
    onEnabled: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.luminous_profiles_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.luminous_profiles_body),
                style = MaterialTheme.typography.bodySmall
            )

            if (!state.isPremium) {
                Text(
                    stringResource(R.string.luminous_profile_premium_required),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Button(
                onClick = { showCreate = true },
                enabled = state.isPremium,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Text(stringResource(R.string.luminous_profile_add))
            }

            profiles.forEach { profile ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            profileSummary(profile, apps),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            profile.colorHex + " · " +
                                profile.strobeCycles + "x · " +
                                profile.strobeSpeedMs + " ms",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = profile.enabled,
                        onCheckedChange = { onEnabled(profile.id, it) }
                    )
                    TextButton(onClick = { onDelete(profile.id) }) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }

    if (showCreate) {
        AddLuminousProfileDialog(
            state = state,
            apps = apps,
            onDismiss = { showCreate = false },
            onCreate = {
                onCreate(
                    it.name,
                    it.packageName,
                    it.senderQuery,
                    it.colorHex,
                    it.flashEnabled,
                    it.overlayEnabled,
                    it.strobeCycles,
                    it.strobeSpeedMs,
                    it.circleThickness,
                    it.circleGlow,
                    it.pulseSpeedMs,
                    it.displayDurationMs
                )
                showCreate = false
            }
        )
    }
}

private data class ProfileDraft(
    val name: String,
    val packageName: String?,
    val senderQuery: String?,
    val colorHex: String,
    val flashEnabled: Boolean,
    val overlayEnabled: Boolean,
    val strobeCycles: Int,
    val strobeSpeedMs: Long,
    val circleThickness: Float,
    val circleGlow: Float,
    val pulseSpeedMs: Long,
    val displayDurationMs: Long
)

@Composable
private fun AddLuminousProfileDialog(
    state: SettingsUiState,
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onCreate: (ProfileDraft) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var sender by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(state.circleColorHex) }
    var flash by remember { mutableStateOf(true) }
    var overlay by remember { mutableStateOf(true) }
    var cycles by remember { mutableStateOf(state.strobeCycles.toFloat()) }
    var speed by remember { mutableStateOf(state.strobeSpeedMs.toFloat()) }
    var pulse by remember { mutableStateOf(state.pulseSpeedMs.toFloat()) }
    var durationSeconds by remember { mutableStateOf(15f) }
    var appMenu by remember { mutableStateOf(false) }

    val selectorValid = selectedApp != null || sender.isNotBlank()
    val modeValid = flash || overlay

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.luminous_profile_add)) },
        text = {
            LazyColumn(
                Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text(stringResource(R.string.luminous_profile_name))
                        }
                    )
                }
                item {
                    Box {
                        OutlinedButton(
                            onClick = { appMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                selectedApp?.label
                                    ?: stringResource(R.string.rule_any_app)
                            )
                        }
                        DropdownMenu(
                            expanded = appMenu,
                            onDismissRequest = { appMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.rule_any_app))
                                },
                                onClick = {
                                    selectedApp = null
                                    appMenu = false
                                }
                            )
                            apps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app.label) },
                                    onClick = {
                                        selectedApp = app
                                        appMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = sender,
                        onValueChange = { sender = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text(stringResource(R.string.luminous_sender_contains))
                        }
                    )
                }
                item {
                    Text(
                        stringResource(R.string.luminous_color),
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "#6750A4",
                            "#0A84FF",
                            "#30D158",
                            "#FF9F0A",
                            "#FF2D55",
                            "#FF453A"
                        ).forEach { hex ->
                            FilterChip(
                                selected = color == hex,
                                onClick = { color = hex },
                                label = { Text(hex.removePrefix("#")) }
                            )
                        }
                    }
                }
                item {
                    LuminousModeRow(
                        stringResource(R.string.flash_alerts),
                        flash
                    ) { flash = it }
                    LuminousModeRow(
                        stringResource(R.string.overlay_alerts),
                        overlay
                    ) { overlay = it }
                }
                item {
                    Text(
                        stringResource(
                            R.string.strobe_cycles,
                            cycles.toInt()
                        )
                    )
                    Slider(
                        value = cycles,
                        onValueChange = { cycles = it },
                        valueRange = 1f..15f
                    )
                    Text(
                        stringResource(
                            R.string.strobe_speed,
                            speed.toLong()
                        )
                    )
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 50f..800f
                    )
                    Text(
                        stringResource(
                            R.string.luminous_pulse_ms,
                            pulse.toLong()
                        )
                    )
                    Slider(
                        value = pulse,
                        onValueChange = { pulse = it },
                        valueRange = 250f..3_000f
                    )
                    Text(
                        stringResource(
                            R.string.luminous_duration_seconds,
                            durationSeconds.toInt()
                        )
                    )
                    Slider(
                        value = durationSeconds,
                        onValueChange = { durationSeconds = it },
                        valueRange = 3f..60f
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        ProfileDraft(
                            name = name,
                            packageName = selectedApp?.packageName,
                            senderQuery = sender,
                            colorHex = color,
                            flashEnabled = flash,
                            overlayEnabled = overlay,
                            strobeCycles = cycles.toInt(),
                            strobeSpeedMs = speed.toLong(),
                            circleThickness = state.circleThickness,
                            circleGlow = state.circleGlow,
                            pulseSpeedMs = pulse.toLong(),
                            displayDurationMs =
                                durationSeconds.toLong() * 1_000L
                        )
                    )
                },
                enabled = selectorValid && modeValid
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LuminousModeRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun profileSummary(
    profile: LuminousProfileEntity,
    apps: List<InstalledApp>
): String {
    val app = profile.packageName?.let { packageName ->
        apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
    }
    val sender = profile.senderQuery?.takeIf { it.isNotBlank() }
    return listOfNotNull(app, sender).joinToString(" · ")
}

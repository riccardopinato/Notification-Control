package com.riccardopinato.notificationcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.FilledTonalButton
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
import com.riccardopinato.notificationcontrol.data.CriticalPatternEntity
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity
import com.riccardopinato.notificationcontrol.data.RuleWithActions
import com.riccardopinato.notificationcontrol.domain.CriticalPatternType
import com.riccardopinato.notificationcontrol.domain.RuleActionType

@Composable
fun RulesScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    state: SettingsUiState,
    apps: List<InstalledApp>,
    rules: List<RuleWithActions>,
    criticalPatterns: List<CriticalPatternEntity>,
    luminousProfiles: List<LuminousProfileEntity>,
    requestCameraPermission: () -> Unit,
    requestOverlayPermission: () -> Unit,
    onCreateRule: (
        String,
        String?,
        String?,
        String?,
        String,
        List<Pair<String, String?>>
    ) -> Unit,
    onUpdateRule: (
        Long,
        String,
        String?,
        String?,
        String?,
        String,
        List<Pair<String, String?>>
    ) -> Unit,
    onRuleEnabled: (Long, Boolean) -> Unit,
    onDeleteRule: (Long) -> Unit,
    onAddCriticalPattern: (String, String) -> Unit,
    onDeleteCriticalPattern: (Long) -> Unit,
    onCreateLuminousProfile: (
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
    onLuminousProfileEnabled: (Long, Boolean) -> Unit,
    onDeleteLuminousProfile: (Long) -> Unit,
    onPausePingEnabled: (Boolean) -> Unit,
    onPausePingCooldown: (Int) -> Unit,
    setFlash: (Boolean) -> Unit,
    setOverlay: (Boolean) -> Unit,
    setScreenOffOnly: (Boolean) -> Unit,
    setBatteryGuard: (Boolean) -> Unit,
    setQuietHours: (Boolean) -> Unit,
    setCycles: (Int) -> Unit,
    setSpeed: (Long) -> Unit,
    setThickness: (Float) -> Unit,
    setGlow: (Float) -> Unit
) {
    var addRule by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RuleWithActions?>(null) }
    var showLuminous by remember { mutableStateOf(false) }
    var criticalKeyword by remember { mutableStateOf("") }
    var criticalSender by remember { mutableStateOf("") }
    var selectedCriticalApp by remember { mutableStateOf<InstalledApp?>(null) }

    if (showLuminous) {
        Column(
            modifier.fillMaxSize().padding(contentPadding)
        ) {
            TextButton(onClick = { showLuminous = false }) {
                Text(stringResource(R.string.back))
            }
            LuminousScreen(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp),
                state = state,
                requestCameraPermission = requestCameraPermission,
                requestOverlayPermission = requestOverlayPermission,
                setFlash = setFlash,
                setOverlay = setOverlay,
                setScreenOffOnly = setScreenOffOnly,
                setBatteryGuard = setBatteryGuard,
                setQuietHours = setQuietHours,
                setCycles = setCycles,
                setSpeed = setSpeed,
                setThickness = setThickness,
                setGlow = setGlow
            )
        }
        return
    }

    LazyColumn(
        modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                stringResource(R.string.rules_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.rules_subtitle))
        }
        item {
            Button(
                onClick = { addRule = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_rule))
            }
        }

        items(rules, key = { it.rule.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.rule.name, fontWeight = FontWeight.Bold)
                            Text(ruleSummary(item), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = item.rule.enabled,
                            onCheckedChange = { onRuleEnabled(item.rule.id, it) }
                        )
                    }
                    Row {
                        TextButton(onClick = { editingRule = item }) {
                            Text(stringResource(R.string.edit))
                        }
                        TextButton(onClick = { onDeleteRule(item.rule.id) }) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }

        item {
            Text(
                stringResource(R.string.critical_alert_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.critical_alert_body))
        }
        item {
            AddCriticalRow(
                value = criticalKeyword,
                onValueChange = { criticalKeyword = it },
                label = stringResource(R.string.critical_keyword),
                onAdd = {
                    onAddCriticalPattern(CriticalPatternType.KEYWORD, criticalKeyword)
                    criticalKeyword = ""
                }
            )
        }
        item {
            AddCriticalRow(
                value = criticalSender,
                onValueChange = { criticalSender = it },
                label = stringResource(R.string.critical_sender),
                onAdd = {
                    onAddCriticalPattern(CriticalPatternType.SENDER, criticalSender)
                    criticalSender = ""
                }
            )
        }
        item {
            AppPatternPicker(
                apps = apps,
                selected = selectedCriticalApp,
                onSelected = { selectedCriticalApp = it },
                onAdd = {
                    selectedCriticalApp?.let {
                        onAddCriticalPattern(CriticalPatternType.APP, it.packageName)
                        selectedCriticalApp = null
                    }
                }
            )
        }

        items(criticalPatterns, key = { "critical-" + it.id }) { pattern ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        pattern.type + " · " + pattern.value,
                        Modifier.weight(1f)
                    )
                    TextButton(onClick = { onDeleteCriticalPattern(pattern.id) }) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }

        item {
            LuminousProfilesSection(
                state = state,
                apps = apps,
                profiles = luminousProfiles,
                onCreate = onCreateLuminousProfile,
                onEnabled = onLuminousProfileEnabled,
                onDelete = onDeleteLuminousProfile
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.pause_ping_title),
                                fontWeight = FontWeight.Bold
                            )
                            Text(stringResource(R.string.pause_ping_body))
                        }
                        Switch(
                            checked = state.pausePingEnabled,
                            onCheckedChange = onPausePingEnabled
                        )
                    }
                    Text(
                        stringResource(
                            R.string.pause_ping_cooldown,
                            state.pausePingCooldownSeconds
                        )
                    )
                    Slider(
                        value = state.pausePingCooldownSeconds.toFloat(),
                        onValueChange = { onPausePingCooldown(it.toInt()) },
                        valueRange = 5f..120f
                    )
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = { showLuminous = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_luminous_settings))
            }
        }
    }

    if (addRule) {
        AddRuleDialog(
            apps = apps,
            existing = null,
            onDismiss = { addRule = false },
            onSave = { _, name, appPackage, sender, text, matchMode, actions ->
                onCreateRule(
                    name,
                    appPackage,
                    sender,
                    text,
                    matchMode,
                    actions
                )
                addRule = false
            }
        )
    }

    editingRule?.let { existing ->
        AddRuleDialog(
            apps = apps,
            existing = existing,
            onDismiss = { editingRule = null },
            onSave = { id, name, appPackage, sender, text, matchMode, actions ->
                if (id != null) {
                    onUpdateRule(
                        id,
                        name,
                        appPackage,
                        sender,
                        text,
                        matchMode,
                        actions
                    )
                }
                editingRule = null
            }
        )
    }
}

@Composable
private fun AddRuleDialog(
    apps: List<InstalledApp>,
    existing: RuleWithActions?,
    onDismiss: () -> Unit,
    onSave: (
        Long?,
        String,
        String?,
        String?,
        String?,
        String,
        List<Pair<String, String?>>
    ) -> Unit
) {
    val existingActions = existing?.actions.orEmpty()
    var name by remember(existing?.rule?.id) {
        mutableStateOf(existing?.rule?.name.orEmpty())
    }
    var keyword by remember(existing?.rule?.id) {
        mutableStateOf(existing?.rule?.textQuery.orEmpty())
    }
    var sender by remember(existing?.rule?.id) {
        mutableStateOf(existing?.rule?.senderQuery.orEmpty())
    }
    var selectedApp by remember(existing?.rule?.id) {
        mutableStateOf(
            existing?.rule?.packageName?.let { packageName ->
                apps.firstOrNull { it.packageName == packageName }
                    ?: InstalledApp(packageName, packageName)
            }
        )
    }
    var matchMode by remember(existing?.rule?.id) {
        mutableStateOf(existing?.rule?.matchMode ?: "ALL")
    }
    var flash by remember(existing?.rule?.id) {
        mutableStateOf(existingActions.any { it.actionType == RuleActionType.FLASH })
    }
    var overlay by remember(existing?.rule?.id) {
        mutableStateOf(existingActions.any { it.actionType == RuleActionType.OVERLAY })
    }
    var critical by remember(existing?.rule?.id) {
        mutableStateOf(existingActions.any { it.actionType == RuleActionType.CRITICAL })
    }
    var followUp by remember(existing?.rule?.id) {
        mutableStateOf(existingActions.any { it.actionType == RuleActionType.FOLLOW_UP })
    }
    var followUpMinutes by remember(existing?.rule?.id) {
        mutableStateOf(
            existingActions.firstOrNull {
                it.actionType == RuleActionType.FOLLOW_UP
            }?.actionValue?.toIntOrNull()?.coerceIn(15, 1_440) ?: 60
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) {
                    stringResource(R.string.add_rule)
                } else {
                    stringResource(R.string.edit_rule)
                }
            )
        },
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
                        label = { Text(stringResource(R.string.rule_name)) }
                    )
                }
                item {
                    SearchableAppPickerButton(
                        apps = apps,
                        selected = selectedApp,
                        placeholder = stringResource(R.string.rule_any_app),
                        allowNone = true,
                        onSelected = { selectedApp = it }
                    )
                }
                item {
                    OutlinedTextField(
                        value = sender,
                        onValueChange = { sender = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.rule_sender_contains)) }
                    )
                }
                item {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.rule_text_contains)) }
                    )
                }
                item {
                    Text(
                        stringResource(R.string.rule_match_mode),
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = matchMode == "ALL",
                            onClick = { matchMode = "ALL" },
                            label = { Text(stringResource(R.string.rule_match_all)) }
                        )
                        FilterChip(
                            selected = matchMode == "ANY",
                            onClick = { matchMode = "ANY" },
                            label = { Text(stringResource(R.string.rule_match_any)) }
                        )
                    }
                }
                item {
                    ActionToggle(stringResource(R.string.rule_action_flash), flash) {
                        flash = it
                    }
                    ActionToggle(stringResource(R.string.rule_action_overlay), overlay) {
                        overlay = it
                    }
                    ActionToggle(stringResource(R.string.rule_action_critical), critical) {
                        critical = it
                    }
                    ActionToggle(stringResource(R.string.rule_action_follow_up), followUp) {
                        followUp = it
                    }
                    if (followUp) {
                        Text(
                            stringResource(
                                R.string.rule_follow_up_delay,
                                followUpMinutes
                            )
                        )
                        Slider(
                            value = followUpMinutes.toFloat(),
                            onValueChange = {
                                followUpMinutes = it.toInt().coerceIn(15, 1_440)
                            },
                            valueRange = 15f..1_440f
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val actions = buildList<Pair<String, String?>> {
                        if (flash) add(RuleActionType.FLASH to null)
                        if (overlay) add(RuleActionType.OVERLAY to null)
                        if (critical) add(RuleActionType.CRITICAL to null)
                        if (followUp) {
                            add(
                                RuleActionType.FOLLOW_UP to
                                    followUpMinutes.toString()
                            )
                        }
                    }
                    onSave(
                        existing?.rule?.id,
                        name,
                        selectedApp?.packageName,
                        sender,
                        keyword,
                        matchMode,
                        actions
                    )
                },
                enabled =
                    (selectedApp != null || sender.isNotBlank() || keyword.isNotBlank()) &&
                        (flash || overlay || critical || followUp)
            ) {
                Text(
                    if (existing == null) {
                        stringResource(R.string.create)
                    } else {
                        stringResource(R.string.save)
                    }
                )
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
private fun ActionToggle(
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

@Composable
private fun AddCriticalRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onAdd: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(label) }
        )
        Button(onClick = onAdd, enabled = value.isNotBlank()) {
            Text("+")
        }
    }
}

@Composable
private fun AppPatternPicker(
    apps: List<InstalledApp>,
    selected: InstalledApp?,
    onSelected: (InstalledApp?) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            SearchableAppPickerButton(
                apps = apps,
                selected = selected,
                placeholder = stringResource(R.string.critical_app),
                allowNone = false,
                onSelected = onSelected
            )
        }
        Button(onClick = onAdd, enabled = selected != null) {
            Text("+")
        }
    }
}

@Composable
private fun ruleSummary(rule: RuleWithActions): String {
    val conditions = listOfNotNull(
        rule.rule.packageName,
        rule.rule.senderQuery,
        rule.rule.textQuery
    ).joinToString(" · ")
    val flashLabel = stringResource(R.string.rule_action_flash)
    val overlayLabel = stringResource(R.string.rule_action_overlay)
    val criticalLabel = stringResource(R.string.rule_action_critical)
    val followUpLabel = stringResource(R.string.rule_action_follow_up)
    val actions = rule.actions.joinToString(", ") {
        when (it.actionType) {
            RuleActionType.FLASH -> flashLabel
            RuleActionType.OVERLAY -> overlayLabel
            RuleActionType.CRITICAL -> criticalLabel
            RuleActionType.FOLLOW_UP -> followUpLabel
            else -> it.actionType
        }
    }
    val mode = if (rule.rule.matchMode == "ANY") {
        stringResource(R.string.rule_match_any)
    } else {
        stringResource(R.string.rule_match_all)
    }
    return mode + " · " + conditions + " → " + actions
}

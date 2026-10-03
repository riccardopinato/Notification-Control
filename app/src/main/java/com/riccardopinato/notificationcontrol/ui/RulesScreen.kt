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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.data.CriticalPatternEntity
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity
import com.riccardopinato.notificationcontrol.data.RuleWithActions
import com.riccardopinato.notificationcontrol.domain.CriticalPatternType
import com.riccardopinato.notificationcontrol.domain.RuleActionType
import java.util.Locale

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
        Int?,
        Int?,
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
        Int?,
        Int?,
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
    onPausePingAppCooldown: (String, Int) -> Unit,
    onRemovePausePingAppCooldown: (String) -> Unit,
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
    var pausePingApp by remember { mutableStateOf<InstalledApp?>(null) }
    var pausePingAppSeconds by remember { mutableStateOf(30f) }
    var pausePingGlobalSeconds by remember(state.pausePingCooldownSeconds) {
        mutableStateOf(state.pausePingCooldownSeconds.toFloat())
    }
    val appLabels = remember(apps) {
        apps.associate { it.packageName to it.label }
    }

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
                            onCheckedChange = { onRuleEnabled(item.rule.id, it) },
                            modifier = Modifier.semantics {
                                contentDescription = item.rule.name
                            }
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
                            pausePingGlobalSeconds.toInt()
                        )
                    )
                    Slider(
                        value = pausePingGlobalSeconds,
                        onValueChange = { pausePingGlobalSeconds = it },
                        onValueChangeFinished = {
                            onPausePingCooldown(pausePingGlobalSeconds.toInt())
                        },
                        valueRange = 5f..120f
                    )

                    if (state.isPremium) {
                        Text(
                            stringResource(R.string.pause_ping_per_app_title),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            stringResource(R.string.pause_ping_per_app_body),
                            style = MaterialTheme.typography.bodySmall
                        )
                        SearchableAppPickerButton(
                            apps = apps,
                            selected = pausePingApp,
                            placeholder = stringResource(R.string.choose_app),
                            allowNone = false,
                            onSelected = {
                                pausePingApp = it
                                pausePingAppSeconds =
                                    it?.packageName
                                        ?.let(state.pausePingPerAppCooldowns::get)
                                        ?.toFloat()
                                        ?: 30f
                            }
                        )
                        pausePingApp?.let { selected ->
                            Text(
                                if (pausePingAppSeconds.toInt() == 0) {
                                    stringResource(R.string.pause_ping_no_cooldown)
                                } else {
                                    stringResource(
                                        R.string.pause_ping_cooldown,
                                        pausePingAppSeconds.toInt()
                                    )
                                },
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Slider(
                                value = pausePingAppSeconds,
                                onValueChange = { pausePingAppSeconds = it },
                                valueRange = 0f..120f
                            )
                            FilledTonalButton(
                                onClick = {
                                    onPausePingAppCooldown(
                                        selected.packageName,
                                        pausePingAppSeconds.toInt()
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.save))
                            }
                        }

                        state.pausePingPerAppCooldowns.forEach { (packageName, seconds) ->
                            val label = appLabels[packageName] ?: packageName
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (seconds == 0) {
                                            stringResource(R.string.pause_ping_no_cooldown)
                                        } else {
                                            stringResource(
                                                R.string.pause_ping_cooldown,
                                                seconds
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        onRemovePausePingAppCooldown(packageName)
                                        if (pausePingApp?.packageName == packageName) {
                                            pausePingApp = null
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
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
            premium = state.isPremium,
            onDismiss = { addRule = false },
            onSave = {
                    _, name, appPackage, sender, text, matchMode,
                    timeStart, timeEnd, screenState, actions ->
                onCreateRule(
                    name,
                    appPackage,
                    sender,
                    text,
                    matchMode,
                    timeStart,
                    timeEnd,
                    screenState,
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
            premium = state.isPremium,
            onDismiss = { editingRule = null },
            onSave = {
                    id, name, appPackage, sender, text, matchMode,
                    timeStart, timeEnd, screenState, actions ->
                if (id != null) {
                    onUpdateRule(
                        id,
                        name,
                        appPackage,
                        sender,
                        text,
                        matchMode,
                        timeStart,
                        timeEnd,
                        screenState,
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
    premium: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        Long?,
        String,
        String?,
        String?,
        String?,
        String,
        Int?,
        Int?,
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
    var timeEnabled by remember(existing?.rule?.id) {
        mutableStateOf(
            existing?.rule?.timeStartMinutes != null &&
                existing.rule.timeEndMinutes != null
        )
    }
    var timeStart by remember(existing?.rule?.id) {
        mutableStateOf((existing?.rule?.timeStartMinutes ?: 22 * 60).toFloat())
    }
    var timeEnd by remember(existing?.rule?.id) {
        mutableStateOf((existing?.rule?.timeEndMinutes ?: 7 * 60).toFloat())
    }
    var screenState by remember(existing?.rule?.id) {
        mutableStateOf(existing?.rule?.screenState ?: "ANY")
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
                    Text(
                        stringResource(R.string.rule_advanced_conditions),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!premium) {
                        Text(
                            stringResource(R.string.rule_advanced_premium),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        ActionToggle(
                            stringResource(R.string.rule_time_window),
                            timeEnabled
                        ) { timeEnabled = it }

                        if (timeEnabled) {
                            Text(
                                stringResource(
                                    R.string.rule_time_start,
                                    formatRuleMinutes(timeStart.toInt())
                                )
                            )
                            Slider(
                                value = timeStart,
                                onValueChange = {
                                    timeStart = ((it.toInt() / 30) * 30)
                                        .coerceIn(0, 1410)
                                        .toFloat()
                                },
                                valueRange = 0f..1410f,
                                steps = 46
                            )
                            Text(
                                stringResource(
                                    R.string.rule_time_end,
                                    formatRuleMinutes(timeEnd.toInt())
                                )
                            )
                            Slider(
                                value = timeEnd,
                                onValueChange = {
                                    timeEnd = ((it.toInt() / 30) * 30)
                                        .coerceIn(0, 1410)
                                        .toFloat()
                                },
                                valueRange = 0f..1410f,
                                steps = 46
                            )
                        }

                        Text(
                            stringResource(R.string.rule_screen_state),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "ANY" to R.string.rule_screen_any,
                                "SCREEN_ON" to R.string.rule_screen_on,
                                "SCREEN_OFF" to R.string.rule_screen_off
                            ).forEach { (value, labelRes) ->
                                FilterChip(
                                    selected = screenState == value,
                                    onClick = { screenState = value },
                                    label = { Text(stringResource(labelRes)) }
                                )
                            }
                        }
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
                        if (premium && timeEnabled) timeStart.toInt() else null,
                        if (premium && timeEnabled) timeEnd.toInt() else null,
                        if (premium) screenState else "ANY",
                        actions
                    )
                },
                enabled =
                    (
                        selectedApp != null ||
                            sender.isNotBlank() ||
                            keyword.isNotBlank() ||
                            (premium && timeEnabled) ||
                            (premium && screenState != "ANY")
                        ) &&
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
    val conditions = buildList {
        rule.rule.packageName?.let(::add)
        rule.rule.senderQuery?.let(::add)
        rule.rule.textQuery?.let(::add)
        val start = rule.rule.timeStartMinutes
        val end = rule.rule.timeEndMinutes
        if (start != null && end != null) {
            add(
                stringResource(
                    R.string.rule_summary_time,
                    formatRuleMinutes(start),
                    formatRuleMinutes(end)
                )
            )
        }
        when (rule.rule.screenState) {
            "SCREEN_ON" -> add(stringResource(R.string.rule_screen_on))
            "SCREEN_OFF" -> add(stringResource(R.string.rule_screen_off))
        }
    }.joinToString(" · ")
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


private fun formatRuleMinutes(minutes: Int): String {
    val safe = minutes.coerceIn(0, 1439)
    return String.format(Locale.getDefault(), "%02d:%02d", safe / 60, safe % 60)
}

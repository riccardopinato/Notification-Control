package com.riccardopinato.notificationcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.riccardopinato.notificationcontrol.BuildConfig
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.domain.OnboardingCompletionPolicy
import com.riccardopinato.notificationcontrol.domain.ProductLimits

@Composable
fun OnboardingScreen(
    settings: SettingsUiState,
    apps: List<InstalledApp>,
    onToggleApp: (String) -> Boolean,
    requestNotificationAccess: () -> Unit,
    openAppInfo: () -> Unit,
    onDone: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    when (step) {
        0 -> OnboardingIntro { step = 1 }
        else -> CoreSetupPage(
            apps = apps,
            selected = settings.monitoredPackages,
            premium = settings.isPremium,
            requestNotificationAccess = requestNotificationAccess,
            openAppInfo = openAppInfo,
            onToggle = onToggleApp,
            onDone = onDone
        )
    }
}

@Composable
private fun OnboardingIntro(onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_core_only_body),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.onboarding_core_only_title),
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.privacy_local_body))
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.start))
        }
    }
}

@Composable
private fun CoreSetupPage(
    apps: List<InstalledApp>,
    selected: Set<String>,
    premium: Boolean,
    requestNotificationAccess: () -> Unit,
    openAppInfo: () -> Unit,
    onToggle: (String) -> Boolean,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val access = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
    var attemptedAccess by remember { mutableStateOf(false) }
    var limitError by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(apps, query) {
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

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            stringResource(R.string.notification_access_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (access) {
                stringResource(R.string.onboarding_ready_body)
            } else {
                stringResource(R.string.notification_access_body)
            }
        )

        TextButton(
            onClick = {
                attemptedAccess = true
                requestNotificationAccess()
            },
            enabled = !access
        ) {
            Text(
                if (access) {
                    stringResource(R.string.access_granted)
                } else {
                    stringResource(R.string.grant_access)
                }
            )
        }

        if (attemptedAccess && !access) {
            Card(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.onboarding_access_recovery_title),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.onboarding_access_recovery_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    FilledTonalButton(
                        onClick = openAppInfo,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_open_app_info))
                    }
                }
            }
        }

        Text(
            stringResource(R.string.choose_apps_title),
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(
                R.string.choose_apps_body,
                ProductLimits.FREE_MONITORED_APPS
            )
        )
        Text(
            stringResource(
                R.string.selected_count,
                selected.size,
                if (premium) apps.size else ProductLimits.FREE_MONITORED_APPS
            ),
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.search_apps)) }
        )

        if (limitError) {
            Text(
                stringResource(
                    R.string.free_limit_reached,
                    ProductLimits.FREE_MONITORED_APPS
                ),
                color = MaterialTheme.colorScheme.error
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(filteredApps, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                val monitorLabel = stringResource(
                    R.string.monitor_app_accessibility,
                    app.label
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Switch,
                            onValueChange = {
                                limitError = !onToggle(app.packageName)
                            }
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = monitorLabel
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        app.label,
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Switch(
                        checked = checked,
                        onCheckedChange = null
                    )
                }
                HorizontalDivider()
            }
        }

        Text(
            stringResource(R.string.onboarding_optional_later),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Button(
            onClick = onDone,
            enabled = OnboardingCompletionPolicy.canFinish(
                notificationAccessGranted = access,
                selectedAppCount = selected.size,
                qaValidationBuild = BuildConfig.QA_PREMIUM_UNLOCKED
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.finish_setup))
        }
    }
}

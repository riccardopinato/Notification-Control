package com.riccardopinato.notificationcontrol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riccardopinato.notificationcontrol.ui.MainViewModel
import com.riccardopinato.notificationcontrol.ui.theme.LuminousBackground
import com.riccardopinato.notificationcontrol.ui.theme.LuminousPrimary
import com.riccardopinato.notificationcontrol.ui.theme.LuminousSecondary
import com.riccardopinato.notificationcontrol.utils.BillingHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuminousSettingsScreen(
    viewModel: MainViewModel,
    billingHelper: BillingHelper,
    onRequestPermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationListenerPermission: () -> Unit,
    onRestartOnboarding: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun premiumRequired(feature: String) {
        scope.launch {
            snackbar.showSnackbar("$feature · Notification Control Premium")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(LuminousPrimary, LuminousSecondary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black)
                        }
                        Text("Luminous", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onRestartOnboarding) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LuminousBackground,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = LuminousBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.size(2.dp)) }
            item { HeroStatusCard(uiState) }
            item {
                PermissionsCard(
                    uiState = uiState,
                    onRequestPermissions = onRequestPermissions,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestNotificationListenerPermission = onRequestNotificationListenerPermission
                )
            }
            item {
                AlertModesCard(
                    uiState = uiState,
                    onFlashToggle = viewModel::setFlashEnabled,
                    onOverlayToggle = viewModel::setOverlayEnabled,
                    onScreenOffOnlyToggle = viewModel::setScreenOffOnly
                )
            }
            item {
                EventTriggersCard(
                    uiState = uiState,
                    onNotifToggle = viewModel::setFlashOnNotification
                )
            }
            item {
                StrobeCustomizationCard(
                    uiState = uiState,
                    onSpeedChange = viewModel::setStrobeSpeedMs,
                    onCyclesChange = viewModel::setStrobeCycles
                )
            }
            item {
                CircleAestheticsCard(
                    uiState = uiState,
                    onColorSelect = { if (uiState.isAodDeluxeUnlocked) viewModel.setCircleColorHex(it) else premiumRequired("Colori e profili Luminous") },
                    onThicknessChange = { if (uiState.isAodDeluxeUnlocked) viewModel.setCircleThickness(it) else premiumRequired("Spessore Luminous") },
                    onGlowChange = { if (uiState.isAodDeluxeUnlocked) viewModel.setCircleGlow(it) else premiumRequired("Glow Luminous") },
                    onPulseSpeedChange = { if (uiState.isAodDeluxeUnlocked) viewModel.setPulseSpeed(it) else premiumRequired("Animazioni Luminous") },
                    onLockedClick = { premiumRequired("Personalizzazione Luminous") }
                )
            }
            item {
                QuietHoursCard(
                    uiState = uiState,
                    onQuietHoursToggle = { if (uiState.isUtilityProUnlocked) viewModel.setQuietHoursEnabled(it) else premiumRequired("Quiet Hours") },
                    onIntervalChange = viewModel::setQuietHoursInterval,
                    onLockedClick = { premiumRequired("Quiet Hours") }
                )
            }
            item {
                BatteryGuardCard(
                    uiState = uiState,
                    onGuardToggle = { if (uiState.isUtilityProUnlocked) viewModel.setBatteryGuardEnabled(it) else premiumRequired("Battery Guard") },
                    onThresholdChange = { if (uiState.isUtilityProUnlocked) viewModel.setBatteryGuardThreshold(it) else premiumRequired("Battery Guard") },
                    onLockedClick = { premiumRequired("Battery Guard") }
                )
            }
            item {
                UnreadRemindersCard(
                    uiState = uiState,
                    onToggle = { if (uiState.isUtilityProUnlocked) viewModel.setReminderEnabled(it) else premiumRequired("Promemoria periodici") },
                    onIntervalChange = { if (uiState.isUtilityProUnlocked) viewModel.setReminderIntervalMinutes(it) else premiumRequired("Promemoria periodici") },
                    onLockedClick = { premiumRequired("Promemoria periodici") }
                )
            }
            item {
                BatterySavingsCard(
                    totalPrevented = uiState.totalPreventedFlashes,
                    mahSaved = uiState.estimatedMahSaved,
                    onReset = viewModel::resetSavingsStats
                )
            }
            item {
                TestSimulationCard(
                    uiState = uiState,
                    onTestFlash = viewModel::testFlash,
                    onTestOverlay = viewModel::testOverlay,
                    onSimulateCall = viewModel::simulateCall,
                    onStopSimulation = viewModel::stopSimulation,
                    onStartSimulatorTest = viewModel::startSimulatorTest,
                    onStopSimulatorTest = viewModel::stopSimulatorTest
                )
            }
            item {
                val camera = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
                val listener = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                Text(
                    text = "Camera ${if (camera) "✓" else "—"} · Overlay ${if (overlay) "✓" else "—"} · Listener ${if (listener) "✓" else "—"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

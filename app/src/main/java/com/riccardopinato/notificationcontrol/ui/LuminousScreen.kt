package com.riccardopinato.notificationcontrol.ui

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riccardopinato.notificationcontrol.R

@Composable
fun LuminousScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    state: SettingsUiState,
    requestCameraPermission: () -> Unit,
    requestOverlayPermission: () -> Unit,
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
    val context = LocalContext.current
    val cameraGranted =
        context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    val overlayGranted = Settings.canDrawOverlays(context)

    LazyColumn(
        modifier.fillMaxSize().padding(contentPadding).padding(16.dp)
    ) {
        item {
            Text(
                stringResource(R.string.luminous_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.luminous_body),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item {
            ToggleCard(
                stringResource(R.string.flash_alerts),
                stringResource(R.string.flash_alerts_desc),
                state.flashEnabled,
                setFlash
            )
        }
        item {
            ToggleCard(
                stringResource(R.string.overlay_alerts),
                stringResource(R.string.overlay_alerts_desc),
                state.overlayEnabled,
                setOverlay
            )
        }
        item {
            ToggleCard(
                stringResource(R.string.screen_off_only),
                null,
                state.screenOffOnly,
                setScreenOffOnly
            )
        }
        item {
            ToggleCard(
                stringResource(R.string.battery_guard),
                stringResource(R.string.battery_guard_desc),
                state.batteryGuardEnabled,
                setBatteryGuard
            )
        }
        item {
            ToggleCard(
                stringResource(R.string.quiet_hours),
                stringResource(R.string.quiet_hours_desc),
                state.quietHoursEnabled,
                setQuietHours
            )
        }
        item {
            PermissionAction(
                stringResource(R.string.camera_permission),
                cameraGranted,
                requestCameraPermission
            )
            PermissionAction(
                stringResource(R.string.overlay_permission),
                overlayGranted,
                requestOverlayPermission
            )
        }
        item {
            SliderCard(
                stringResource(R.string.strobe_cycles, state.strobeCycles),
                state.strobeCycles.toFloat(),
                1f..10f
            ) { setCycles(it.toInt()) }
            SliderCard(
                stringResource(R.string.strobe_speed, state.strobeSpeedMs),
                state.strobeSpeedMs.toFloat(),
                50f..800f
            ) { setSpeed(it.toLong()) }
            SliderCard(
                stringResource(R.string.circle_thickness),
                state.circleThickness,
                8f..60f,
                setThickness
            )
            SliderCard(
                stringResource(R.string.circle_glow),
                state.circleGlow,
                0f..80f,
                setGlow
            )
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    body: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                body?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun PermissionAction(
    title: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f))
        TextButton(onClick = onClick, enabled = !granted) {
            Text(
                if (granted) {
                    stringResource(R.string.granted)
                } else {
                    stringResource(R.string.request)
                }
            )
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, fontWeight = FontWeight.Medium)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range
        )
    }
}

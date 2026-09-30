package com.riccardopinato.notificationcontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.ui.MainUiState
import com.riccardopinato.notificationcontrol.ui.MainViewModel
import com.riccardopinato.notificationcontrol.ui.onboarding.OnboardingScreen
import com.riccardopinato.notificationcontrol.ui.theme.LuminousBackground
import com.riccardopinato.notificationcontrol.ui.theme.LuminousBorder
import com.riccardopinato.notificationcontrol.ui.theme.LuminousPrimary
import com.riccardopinato.notificationcontrol.ui.theme.LuminousSecondary
import com.riccardopinato.notificationcontrol.ui.theme.LuminousSurface
import com.riccardopinato.notificationcontrol.ui.theme.LuminousSurfaceVariant
import com.riccardopinato.notificationcontrol.ui.theme.MyApplicationTheme
import com.riccardopinato.notificationcontrol.utils.BillingHelper
import com.riccardopinato.notificationcontrol.utils.PremiumManager
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestSimulationCard(
    uiState: MainUiState,
    onTestFlash: () -> Unit,
    onTestOverlay: () -> Unit,
    onSimulateCall: () -> Unit,
    onStopSimulation: () -> Unit,
    onStartSimulatorTest: () -> Unit,
    onStopSimulatorTest: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LuminousSurface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LuminousBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pannello Test & Simulazione",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (!uiState.isTestingSimulator) {
                Button(
                    onClick = onStartSimulatorTest,
                    modifier = Modifier.fillMaxWidth().testTag("btn_test_hardware"),
                    colors = ButtonDefaults.buttonColors(containerColor = LuminousPrimary, contentColor = Color.Black),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = "Avvia test simulatore hardware per 5 secondi", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Avvia Test Simulatore LED/Flash (5s)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            } else {
                Button(
                    onClick = onStopSimulatorTest,
                    modifier = Modifier.fillMaxWidth().alpha(pulseAlpha).testTag("btn_stop_test_hardware"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = "Ferma simulatore", tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test in Corso (Pulsazione LED)... Tocca per Stop", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onTestFlash,
                    modifier = Modifier.weight(1f).testTag("test_flash_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1E4FF))
                ) {
                    Icon(Icons.Default.FlashlightOn, contentDescription = "Test flash singolo", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Flash", fontSize = 12.sp, color = Color.Black)
                }
                Button(
                    onClick = onTestOverlay,
                    modifier = Modifier.weight(1f).testTag("test_overlay_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA6C8FF))
                ) {
                    Icon(Icons.Default.RadioButtonChecked, contentDescription = "Test overlay singolo", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Overlay", fontSize = 12.sp, color = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (!uiState.isSimulatingCall) {
                OutlinedButton(
                    onClick = onSimulateCall,
                    modifier = Modifier.fillMaxWidth().testTag("simulate_call_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LuminousPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LuminousPrimary)
                ) {
                    Icon(Icons.Default.PhoneCallback, contentDescription = "Simula chiamata in arrivo", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simula Chiamata in Arrivo (15s)", fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = onStopSimulation,
                    modifier = Modifier.fillMaxWidth().testTag("stop_simulation_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Interrompi simulazione chiamata", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Interrompi Simulazione", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

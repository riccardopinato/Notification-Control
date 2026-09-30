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
fun AlertModesCard(
    uiState: MainUiState,
    onFlashToggle: (Boolean) -> Unit,
    onOverlayToggle: (Boolean) -> Unit,
    onScreenOffOnlyToggle: (Boolean) -> Unit,
    onLockedOverlayClick: () -> Unit = {}
) {
    val isOverlayLocked = false

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LuminousSurface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LuminousBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Canali di Avviso (Gratis & Deluxe)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            SwitchRow(
                title = "Flash Fotocamera Posteriore (Gratis)",
                subtitle = "Usa il LED hardware per far lampeggiare il telefono",
                checked = uiState.flashEnabled,
                onCheckedChange = onFlashToggle,
                tag = "toggle_flash_led"
            )
            HorizontalDivider(color = LuminousBorder)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isOverlayLocked) 0.65f else 1.0f)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "The Luminous Infinite Circle",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                        if (isOverlayLocked) {
                            Surface(
                                color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "AOD PRO",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Anello luminoso AOD a schermo per display OLED",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = uiState.overlayEnabled && !isOverlayLocked,
                    onCheckedChange = onOverlayToggle,
                    modifier = Modifier.testTag("toggle_screen_overlay"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = LuminousPrimary
                    )
                )
            }
            HorizontalDivider(color = LuminousBorder)
            SwitchRow(
                title = "Solo a Schermo Spento",
                subtitle = "Risparmia energia quando stai usando il telefono",
                checked = uiState.screenOffOnly,
                onCheckedChange = onScreenOffOnlyToggle,
                tag = "toggle_screen_off_only"
            )
        }
    }
}

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
fun QuietHoursCard(
    uiState: MainUiState,
    onQuietHoursToggle: (Boolean) -> Unit,
    onIntervalChange: (Int, Int, Int, Int) -> Unit,
    onLockedClick: () -> Unit = {}
) {
    val isLocked = !uiState.isUtilityProUnlocked

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.65f else 1.0f),
        colors = CardDefaults.cardColors(containerColor = LuminousSurface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LuminousBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NightlightRound,
                        contentDescription = null,
                        tint = Color(0xFFD1E4FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Quiet Hours (Ore di Silenzio)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                if (isLocked) {
                    Surface(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "UTIL PRO",
                            color = Color(0xFFA78BFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            SwitchRow(
                title = "Silenzia di Notte",
                subtitle = "Blocca flash e cerchio luminoso nell'intervallo scelto",
                checked = uiState.quietHoursEnabled && !isLocked,
                onCheckedChange = onQuietHoursToggle,
                tag = "toggle_quiet_hours"
            )

            AnimatedVisibility(visible = uiState.quietHoursEnabled && !isLocked) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (uiState.isQuietHoursCurrentlyActive) {
                        Surface(
                            color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = Color(0xFFD1E4FF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Quiet Hours attualmente attive (app silente)",
                                    fontSize = 12.sp,
                                    color = Color(0xFFD1E4FF),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    val startFormatted = String.format(Locale.getDefault(), "%02d:%02d", uiState.quietHoursStartHour, uiState.quietHoursStartMinute)
                    val endFormatted = String.format(Locale.getDefault(), "%02d:%02d", uiState.quietHoursEndHour, uiState.quietHoursEndMinute)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inizio Silenzio: $startFormatted",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Fine: $endFormatted",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ora di Inizio: ${uiState.quietHoursStartHour}:00",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Slider(
                        value = uiState.quietHoursStartHour.toFloat(),
                        onValueChange = {
                            onIntervalChange(
                                it.toInt(),
                                uiState.quietHoursStartMinute,
                                uiState.quietHoursEndHour,
                                uiState.quietHoursEndMinute
                            )
                        },
                        valueRange = 0f..23f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFD1E4FF),
                            activeTrackColor = Color(0xFFD1E4FF)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ora di Fine: ${uiState.quietHoursEndHour}:00",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Slider(
                        value = uiState.quietHoursEndHour.toFloat(),
                        onValueChange = {
                            onIntervalChange(
                                uiState.quietHoursStartHour,
                                uiState.quietHoursStartMinute,
                                it.toInt(),
                                uiState.quietHoursEndMinute
                            )
                        },
                        valueRange = 0f..23f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFA6C8FF),
                            activeTrackColor = Color(0xFFA6C8FF)
                        )
                    )
                }
            }
        }
    }
}

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
fun UnreadRemindersCard(
    uiState: MainUiState,
    onToggle: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onLockedClick: () -> Unit = {}
) {
    val isLocked = !uiState.isUtilityProUnlocked

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.65f else 1.0f)
            .testTag("card_unread_reminders"),
        colors = CardDefaults.cardColors(containerColor = LuminousSurface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LuminousBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationImportant,
                        contentDescription = null,
                        tint = LuminousPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Promemoria Notifiche Non Lette",
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
                title = "Ripeti avvisi per notifiche perse",
                subtitle = "Esegue flash periodici finché non leggi o rimuovi la notifica",
                checked = uiState.isReminderEnabled && !isLocked,
                onCheckedChange = onToggle,
                tag = "toggle_unread_reminders"
            )

            AnimatedVisibility(visible = uiState.isReminderEnabled && !isLocked) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Intervallo di ripetizione:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${uiState.reminderIntervalMinutes} minut${if (uiState.reminderIntervalMinutes > 1) "i" else "o"}",
                            color = LuminousPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = uiState.reminderIntervalMinutes.toFloat(),
                        onValueChange = { onIntervalChange(it.toInt()) },
                        valueRange = 1f..15f,
                        steps = 13,
                        modifier = Modifier.fillMaxWidth().testTag("slider_reminder_interval"),
                        colors = SliderDefaults.colors(
                            thumbColor = LuminousPrimary,
                            activeTrackColor = LuminousPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 5, 10, 15).forEach { mins ->
                            val isSelected = uiState.reminderIntervalMinutes == mins
                            Surface(
                                onClick = { onIntervalChange(mins) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) LuminousPrimary else LuminousSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) LuminousPrimary else LuminousBorder
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$mins m",
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚡ Sveglie di sistema ad alta precisione (SCHEDULE_EXACT_ALARM): il dispositivo dorme in modalità Doze e si risveglia solo per il micro-avviso, risparmiando il 99% di batteria rispetto a un loop in background.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}

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
fun CircleAestheticsCard(
    uiState: MainUiState,
    onColorSelect: (String) -> Unit,
    onThicknessChange: (Float) -> Unit,
    onGlowChange: (Float) -> Unit,
    onPulseSpeedChange: (Float) -> Unit,
    onLockedClick: () -> Unit = {}
) {
    val isLocked = !uiState.isAodDeluxeUnlocked
    val colors = listOf(
        "#FFFFFF", // Bianco
        "#00E5FF", // Ciano Neon
        "#EF4444", // Rosso
        "#10B981", // Verde
        "#A855F7"  // Viola Neon
    )

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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Personalizzazione Estetica (AOD Deluxe)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                if (isLocked) {
                    Surface(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "AOD PRO",
                            color = Color(0xFFA78BFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Colore del LED virtuale",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                colors.forEach { hex ->
                    val colorInt = try {
                        android.graphics.Color.parseColor(hex)
                    } catch (e: Exception) {
                        android.graphics.Color.WHITE
                    }
                    val isSelected = uiState.circleColorHex.equals(hex, ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(colorInt))
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onColorSelect(hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (hex == "#FFFFFF") Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Spessore dell'anello: ${uiState.circleThickness.toInt()} dp",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Slider(
                value = uiState.circleThickness,
                onValueChange = onThicknessChange,
                valueRange = 10f..60f,
                steps = 24,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD1E4FF),
                    activeTrackColor = Color(0xFFD1E4FF)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Intensità del Bagliore (Glow): ${uiState.circleGlow.toInt()} px",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Slider(
                value = uiState.circleGlow,
                onValueChange = onGlowChange,
                valueRange = 5f..80f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFA6C8FF),
                    activeTrackColor = Color(0xFFA6C8FF)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Velocità della Pulsazione: ${uiState.pulseSpeed.toInt()} ms",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Slider(
                value = uiState.pulseSpeed,
                onValueChange = onPulseSpeedChange,
                valueRange = 300f..2000f,
                steps = 16,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF8E9199),
                    activeTrackColor = Color(0xFF8E9199)
                )
            )
        }
    }
}

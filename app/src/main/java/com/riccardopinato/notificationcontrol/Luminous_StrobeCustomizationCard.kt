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
fun StrobeCustomizationCard(
    uiState: MainUiState,
    onSpeedChange: (Long) -> Unit,
    onCyclesChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LuminousSurface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LuminousBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Personalizzazione Stroboscopica Flash",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Velocità Lampeggio: ${uiState.strobeSpeedMs} ms",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Slider(
                value = uiState.strobeSpeedMs.toFloat(),
                onValueChange = { onSpeedChange(it.toLong()) },
                valueRange = 50f..500f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD1E4FF),
                    activeTrackColor = Color(0xFFD1E4FF)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Numero di Cicli: ${uiState.strobeCycles}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Slider(
                value = uiState.strobeCycles.toFloat(),
                onValueChange = { onCyclesChange(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFA6C8FF),
                    activeTrackColor = Color(0xFFA6C8FF)
                )
            )
        }
    }
}

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
fun HeroStatusCard(uiState: MainUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LuminousBorder)
    ) {
        val isActive = (uiState.flashEnabled || uiState.overlayEnabled) &&
                uiState.hasCameraPermission && uiState.hasOverlayPermission
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Stato Servizio Notifiche",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFF10B981) else Color(0xFFEF4444))
                )
                Text(
                    text = if (isActive) "Pronto e Attivo" else "Richiede Configurazione Permessi",
                    color = Color(0xFFD1E4FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Attiva il flash della fotocamera e l'anello infinito a schermo per chiamate, SMS e app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

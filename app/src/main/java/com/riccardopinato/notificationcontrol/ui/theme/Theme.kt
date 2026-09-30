package com.riccardopinato.notificationcontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary=Color(0xFF6750A4),secondary=Color(0xFF625B71),tertiary=Color(0xFF7D5260),background=Color(0xFFF9F7FC),surface=Color(0xFFF9F7FC))
private val Dark = darkColorScheme(primary=Color(0xFFD0BCFF),secondary=Color(0xFFCCC2DC),tertiary=Color(0xFFEFB8C8),background=Color(0xFF121116),surface=Color(0xFF121116))
@Composable fun NotificationControlTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content) }

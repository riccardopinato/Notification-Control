package com.riccardopinato.notificationcontrol

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.riccardopinato.notificationcontrol.ui.NotificationControlApp
import com.riccardopinato.notificationcontrol.ui.NotificationControlViewModel
import com.riccardopinato.notificationcontrol.ui.theme.NotificationControlTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<NotificationControlViewModel>()
    private var permissionEpoch by mutableIntStateOf(0)

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionEpoch++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotificationControlTheme {
                NotificationControlApp(
                    viewModel = viewModel,
                    permissionEpoch = permissionEpoch,
                    requestCameraPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    requestOverlayPermission = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    },
                    requestNotificationAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionEpoch++
        viewModel.refresh()
    }
}

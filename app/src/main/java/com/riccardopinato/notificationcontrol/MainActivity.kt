package com.riccardopinato.notificationcontrol

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.riccardopinato.notificationcontrol.security.VaultSecurityManager
import com.riccardopinato.notificationcontrol.ui.NotificationControlApp
import com.riccardopinato.notificationcontrol.ui.NotificationControlViewModel
import com.riccardopinato.notificationcontrol.ui.theme.NotificationControlTheme

class MainActivity : FragmentActivity() {
    private val viewModel by viewModels<NotificationControlViewModel>()
    private val vaultSecurity by lazy { VaultSecurityManager(this) }

    private var permissionEpoch by mutableIntStateOf(0)
    private var vaultUnlockEpoch by mutableIntStateOf(0)

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionEpoch++
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
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
                    vaultUnlockEpoch = vaultUnlockEpoch,
                    requestCameraPermission = {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    },
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
                    },
                    requestPostNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            permissionEpoch++
                        }
                    },
                    requestVaultUnlock = ::requestVaultUnlock
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionEpoch++
        viewModel.refresh()
    }

    private fun requestVaultUnlock() {
        if (!vaultSecurity.isEnabled()) {
            vaultSecurity.markUnlocked()
            vaultUnlockEpoch++
            return
        }

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        if (
            BiometricManager.from(this).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    vaultSecurity.markUnlocked()
                    vaultUnlockEpoch++
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_vault))
            .setSubtitle(getString(R.string.unlock_vault_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(info)
    }
}

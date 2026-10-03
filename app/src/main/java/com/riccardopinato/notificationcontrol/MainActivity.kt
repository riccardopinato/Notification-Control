package com.riccardopinato.notificationcontrol

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
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
import com.riccardopinato.notificationcontrol.billing.PlayBillingManager
import com.riccardopinato.notificationcontrol.capture.WhatsAppSafMediaSource
import com.riccardopinato.notificationcontrol.security.VaultSecurityManager
import com.riccardopinato.notificationcontrol.ui.NotificationControlApp
import com.riccardopinato.notificationcontrol.ui.NotificationControlViewModel
import com.riccardopinato.notificationcontrol.ui.theme.NotificationControlTheme

class MainActivity : FragmentActivity() {
    private val viewModel by viewModels<NotificationControlViewModel>()
    private val vaultSecurity by lazy { VaultSecurityManager(this) }
    private val billingManager by lazy { PlayBillingManager.get(this) }

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

    private val mediaPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionEpoch++
        }

    private val whatsAppMediaFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                val persisted = runCatching {
                    contentResolver.takePersistableUriPermission(uri, flags)
                    true
                }.getOrDefault(false)

                if (persisted && WhatsAppSafMediaSource(this).isWhatsAppImagesTree(uri)) {
                    viewModel.setWhatsAppMediaTreeUri(uri.toString())
                } else {
                    runCatching {
                        contentResolver.releasePersistableUriPermission(uri, flags)
                    }
                }
            }
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
                    openAppInfo = {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName")
                            )
                        )
                    },
                    requestPostNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            permissionEpoch++
                        }
                    },
                    requestPhotoLibraryPermission = {
                        if (!BuildConfig.BROAD_MEDIA_RECOVERY_ALLOWED) {
                            permissionEpoch++
                        } else {
                            val requested = when {
                                Build.VERSION.SDK_INT >= 34 -> arrayOf(
                                    Manifest.permission.READ_MEDIA_IMAGES,
                                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                                )
                                Build.VERSION.SDK_INT >= 33 -> arrayOf(
                                    Manifest.permission.READ_MEDIA_IMAGES
                                )
                                else -> arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                )
                            }
                            mediaPermissions.launch(requested)
                        }
                    },
                    requestWhatsAppMediaFolder = {
                        whatsAppMediaFolder.launch(null)
                    },
                    requestVaultUnlock = ::requestVaultUnlock,
                    setSecureWindow = ::setSecureWindow,
                    purchasePremiumOffer = { offerKey ->
                        billingManager.launchPurchase(this, offerKey)
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

    private fun setSecureWindow(enabled: Boolean) {
        // qaPremium is a non-publishable, debug-signed validation build.
        // Keep production screenshot protection intact while allowing AppLab
        // to inspect the real UI instead of receiving FLAG_SECURE black frames.
        val secureWindowEnabled = enabled && !BuildConfig.QA_PREMIUM_UNLOCKED
        if (secureWindowEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
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

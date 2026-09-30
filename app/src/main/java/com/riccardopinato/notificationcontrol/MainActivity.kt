package com.riccardopinato.notificationcontrol

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.riccardopinato.notificationcontrol.data.AppSettings
import com.riccardopinato.notificationcontrol.ui.MainViewModel
import com.riccardopinato.notificationcontrol.ui.onboarding.OnboardingScreen
import com.riccardopinato.notificationcontrol.ui.shell.AppShell
import com.riccardopinato.notificationcontrol.ui.theme.MyApplicationTheme
import com.riccardopinato.notificationcontrol.ui.vault.VaultViewModel
import com.riccardopinato.notificationcontrol.utils.BillingHelper
import com.riccardopinato.notificationcontrol.utils.PremiumManager
import com.riccardopinato.notificationcontrol.work.VaultMaintenanceScheduler

class MainActivity : AppCompatActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val vaultViewModel: VaultViewModel by viewModels()
    private lateinit var billingHelper: BillingHelper

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        mainViewModel.checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appSettings = AppSettings(this)
        VaultMaintenanceScheduler.ensureScheduled(applicationContext)
        billingHelper = BillingHelper(applicationContext, PremiumManager(applicationContext)).apply {
            setListener(object : BillingHelper.BillingListener {
                override fun onPurchaseSuccess(productId: String) {
                    mainViewModel.refreshPremiumState()
                    Toast.makeText(this@MainActivity, getString(R.string.billing_success), Toast.LENGTH_SHORT).show()
                }

                override fun onBillingError(error: String) {
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                }
            })
        }

        setContent {
            MyApplicationTheme {
                AppContent(
                    completed = appSettings.onboardingCompleted,
                    onFinished = {
                        appSettings.onboardingCompleted = true
                        recreate()
                    },
                    content = {
                        AppShell(
                            mainViewModel = mainViewModel,
                            vaultViewModel = vaultViewModel,
                            billingHelper = billingHelper,
                            onRequestPermissions = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            onRequestOverlayPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                }
                            },
                            onRequestNotificationListenerPermission = {
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            onRestartOnboarding = {
                                appSettings.onboardingCompleted = false
                                recreate()
                            }
                        )
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.checkPermissions()
        if (::billingHelper.isInitialized) {
            billingHelper.queryPurchases()
            mainViewModel.refreshPremiumState()
        }
    }

    override fun onDestroy() {
        if (::billingHelper.isInitialized) billingHelper.close()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun AppContent(
    completed: Boolean,
    onFinished: () -> Unit,
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    if (completed) content() else OnboardingScreen(onFinished)
}

package com.riccardopinato.notificationcontrol.ui

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.account.GoogleAccountManager
import com.riccardopinato.notificationcontrol.backup.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GoogleAccountCard() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val manager = remember(context) { GoogleAccountManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.google_profile_title),
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.google_profile_body),
                style = MaterialTheme.typography.bodySmall
            )

            val profile = state.profile
            if (profile != null) {
                Text(
                    profile.displayName?.takeIf { it.isNotBlank() } ?: profile.email,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(profile.email, style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = {
                        val host = activity ?: return@TextButton
                        scope.launch { manager.signOut(host) }
                    }
                ) {
                    Text(stringResource(R.string.google_sign_out))
                }
            } else {
                if (!state.configured) {
                    Text(
                        stringResource(R.string.google_sign_in_not_configured),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Button(
                    onClick = {
                        val host = activity ?: return@Button
                        scope.launch { manager.signIn(host) }
                    },
                    enabled = state.configured && !state.loading && activity != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text(
                        if (state.loading) {
                            stringResource(R.string.google_signing_in)
                        } else {
                            stringResource(R.string.google_sign_in)
                        }
                    )
                }
                state.errorMessage?.let { error ->
                    Text(
                        error.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.google_sign_in_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                stringResource(R.string.google_profile_security_note),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private enum class BackupDialogMode { EXPORT, RESTORE, ROLLBACK }

@Composable
fun EncryptedBackupCard(
    isPremium: Boolean,
    onBackupRestored: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { BackupRepository(context) }
    val scope = rememberCoroutineScope()

    var dialogMode by remember { mutableStateOf<BackupDialogMode?>(null) }
    var pendingPassphrase by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var recoveryPoint by remember { mutableStateOf(repository.recoveryPointInfo()) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val passphrase = pendingPassphrase
        pendingPassphrase = ""
        if (uri == null || passphrase.isBlank()) return@rememberLauncherForActivityResult
        scope.launch {
            val chars = passphrase.toCharArray()
            val result = try {
                withContext(Dispatchers.IO) {
                    repository.exportTo(uri, chars)
                }
            } finally {
                chars.fill('\u0000')
            }
            statusText = result.fold(
                onSuccess = {
                    context.getString(
                        R.string.backup_export_success,
                        it.notifications,
                        it.rules,
                        it.followUps
                    )
                },
                onFailure = { context.getString(R.string.backup_operation_failed) }
            )
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val passphrase = pendingPassphrase
        pendingPassphrase = ""
        if (uri == null || passphrase.isBlank()) return@rememberLauncherForActivityResult
        scope.launch {
            val chars = passphrase.toCharArray()
            val result = try {
                withContext(Dispatchers.IO) {
                    repository.restoreFrom(uri, chars)
                }
            } finally {
                chars.fill('\u0000')
            }
            statusText = result.fold(
                onSuccess = {
                    onBackupRestored()
                    recoveryPoint = repository.recoveryPointInfo()
                    if (it.warnings > 0) {
                        context.getString(
                            R.string.backup_restore_success_with_warnings,
                            it.notifications,
                            it.rules,
                            it.followUps,
                            it.warnings
                        )
                    } else {
                        context.getString(
                            R.string.backup_restore_success,
                            it.notifications,
                            it.rules,
                            it.followUps
                        )
                    }
                },
                onFailure = { context.getString(R.string.backup_operation_failed) }
            )
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.backup_title),
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.backup_body),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { dialogMode = BackupDialogMode.EXPORT },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.backup_export))
                }
                Button(
                    onClick = { dialogMode = BackupDialogMode.RESTORE },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.backup_restore))
                }
            }
            statusText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (isPremium) {
                Text(
                    stringResource(R.string.recovery_point_title),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    if (recoveryPoint.available) {
                        stringResource(R.string.recovery_point_available)
                    } else {
                        stringResource(R.string.recovery_point_empty)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (recoveryPoint.available) {
                    TextButton(
                        onClick = { dialogMode = BackupDialogMode.ROLLBACK },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.recovery_point_rollback))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.recovery_point_premium),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Text(
                stringResource(R.string.backup_password_warning),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    dialogMode?.let { mode ->
        BackupPassphraseDialog(
            mode = mode,
            onDismiss = { dialogMode = null },
            onConfirm = { passphrase ->
                pendingPassphrase = passphrase
                dialogMode = null
                when (mode) {
                    BackupDialogMode.EXPORT ->
                        createDocument.launch("notification-control-backup.ncb")
                    BackupDialogMode.RESTORE ->
                        openDocument.launch(arrayOf("*/*"))
                    BackupDialogMode.ROLLBACK -> {
                        val chars = passphrase.toCharArray()
                        scope.launch {
                            val result = try {
                                withContext(Dispatchers.IO) {
                                    repository.rollbackLastRestore(chars)
                                }
                            } finally {
                                chars.fill('\u0000')
                            }
                            statusText = result.fold(
                                onSuccess = {
                                    onBackupRestored()
                                    recoveryPoint = repository.recoveryPointInfo()
                                    if (it.warnings > 0) {
                                        context.getString(
                                            R.string.recovery_point_rollback_warnings,
                                            it.warnings
                                        )
                                    } else {
                                        context.getString(R.string.recovery_point_rollback_success)
                                    }
                                },
                                onFailure = {
                                    context.getString(R.string.backup_operation_failed)
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun BackupPassphraseDialog(
    mode: BackupDialogMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val export = mode == BackupDialogMode.EXPORT
    val rollback = mode == BackupDialogMode.ROLLBACK
    val valid = passphrase.length >= 8 && (!export || passphrase == confirmation)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    export -> stringResource(R.string.backup_export)
                    rollback -> stringResource(R.string.recovery_point_rollback)
                    else -> stringResource(R.string.backup_restore)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        export -> stringResource(R.string.backup_export_dialog_body)
                        rollback -> stringResource(R.string.recovery_point_rollback_body)
                        else -> stringResource(R.string.backup_restore_dialog_body)
                    }
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.backup_password)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (export) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(stringResource(R.string.backup_password_confirm)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (passphrase.isNotEmpty() && passphrase.length < 8) {
                    Text(
                        stringResource(R.string.backup_password_minimum),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (export && confirmation.isNotEmpty() && passphrase != confirmation) {
                    Text(
                        stringResource(R.string.backup_password_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase) },
                enabled = valid
            ) {
                Text(
                    when {
                        export -> stringResource(R.string.backup_export)
                        rollback -> stringResource(R.string.recovery_point_rollback)
                        else -> stringResource(R.string.backup_restore)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}


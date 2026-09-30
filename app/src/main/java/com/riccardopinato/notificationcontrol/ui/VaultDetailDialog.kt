package com.riccardopinato.notificationcontrol.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.NotificationEntity
import com.riccardopinato.notificationcontrol.data.NotificationRevisionEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun VaultDetailDialog(
    state: VaultDetailUiState,
    onDismiss: () -> Unit,
    onProtect: (String, Boolean) -> Unit,
    onFollowUp: (NotificationEntity) -> Unit
) {
    val notification = state.notification
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(notification.appLabel)
                notification.title?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        },
        text = {
            if (state.loading) {
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        VaultStatusCard(notification)
                    }

                    val currentText = notification.bigText
                        ?.takeIf { it.isNotBlank() }
                        ?: notification.text
                    currentText?.takeIf { it.isNotBlank() }?.let { text ->
                        item {
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    state.pickupCandidate?.let { candidate ->
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        stringResource(R.string.vault_pickup_candidate),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        candidate.code,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Text(
                                        stringResource(R.string.vault_pickup_candidate_free_hint),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    TextButton(
                                        onClick = {
                                            val clipboard =
                                                context.getSystemService(
                                                    Context.CLIPBOARD_SERVICE
                                                ) as ClipboardManager
                                            clipboard.setPrimaryClip(
                                                ClipData.newPlainText(
                                                    context.getString(
                                                        R.string.pickup_code
                                                    ),
                                                    candidate.code
                                                )
                                            )
                                        }
                                    ) {
                                        Text(stringResource(R.string.copy))
                                    }
                                }
                            }
                        }
                    }

                    if (state.messages.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.vault_messages_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(state.messages, key = { it.messageKey }) { message ->
                            VaultMessageRow(message)
                        }
                    }

                    if (state.revisions.size > 1) {
                        item {
                            HorizontalDivider()
                            Text(
                                stringResource(
                                    R.string.vault_versions_title,
                                    state.revisions.size
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                stringResource(R.string.vault_versions_body),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        items(
                            state.revisions.reversed(),
                            key = { it.revisionKey }
                        ) { revision ->
                            VaultRevisionRow(revision)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onProtect(notification.sbnKey, !notification.protected)
                    }
                ) {
                    Text(
                        if (notification.protected) {
                            stringResource(R.string.unprotect)
                        } else {
                            stringResource(R.string.protect)
                        }
                    )
                }
                TextButton(onClick = { onFollowUp(notification) }) {
                    Text(stringResource(R.string.follow_up))
                }
            }
        }
    )
}

@Composable
private fun VaultStatusCard(notification: NotificationEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (notification.removedAt == null) {
                    stringResource(R.string.vault_status_active)
                } else {
                    stringResource(R.string.vault_status_removed)
                },
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(
                    R.string.vault_captured_at,
                    formatDateTime(notification.postedAt)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            notification.removedAt?.let {
                Text(
                    stringResource(
                        R.string.vault_removed_at,
                        formatDateTime(it)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.vault_removed_disclaimer),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun VaultMessageRow(message: MessageEntity) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message.sender?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.vault_unknown_sender),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(message.text)
    }
}

@Composable
private fun VaultRevisionRow(revision: NotificationRevisionEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                formatDateTime(revision.capturedAt),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            revision.title?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontWeight = FontWeight.SemiBold)
            }
            val content = revision.bigText?.takeIf { it.isNotBlank() }
                ?: revision.text
            content?.takeIf { it.isNotBlank() }?.let {
                Text(it)
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(timestamp))

private fun formatTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

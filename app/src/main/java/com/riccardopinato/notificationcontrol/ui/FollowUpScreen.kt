package com.riccardopinato.notificationcontrol.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.data.FollowUpEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun FollowUpScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    followUps: List<FollowUpEntity>,
    requestPostNotifications: () -> Unit,
    onComplete: (Long) -> Unit,
    onSnooze: (Long, Int) -> Unit
) {
    val context = LocalContext.current
    val permissionGranted =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    val notificationsEnabled =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    LazyColumn(
        modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                stringResource(R.string.follow_up_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.follow_up_subtitle))
        }

        if (!permissionGranted || !notificationsEnabled) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.follow_up_permission_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(stringResource(R.string.follow_up_permission_body))
                        FilledTonalButton(onClick = requestPostNotifications) {
                            Text(stringResource(R.string.grant_access))
                        }
                    }
                }
            }
        }

        if (followUps.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            stringResource(R.string.follow_up_empty),
                            fontWeight = FontWeight.Bold
                        )
                        Text(stringResource(R.string.follow_up_empty_hint))
                    }
                }
            }
        }

        items(followUps, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    item.body?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    Text(
                        stringResource(
                            R.string.follow_up_due_at,
                            DateFormat.getDateTimeInstance(
                                DateFormat.SHORT,
                                DateFormat.SHORT
                            ).format(Date(item.dueAt))
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(onClick = { onSnooze(item.id, 15) }) {
                            Text(stringResource(R.string.snooze_15))
                        }
                        TextButton(onClick = { onSnooze(item.id, 60) }) {
                            Text(stringResource(R.string.snooze_hour))
                        }
                        TextButton(onClick = { onSnooze(item.id, 24 * 60) }) {
                            Text(stringResource(R.string.snooze_tomorrow))
                        }
                    }
                    TextButton(onClick = { onComplete(item.id) }) {
                        Text(stringResource(R.string.mark_done))
                    }
                }
            }
        }
    }
}

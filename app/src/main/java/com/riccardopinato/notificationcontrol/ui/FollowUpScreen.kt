package com.riccardopinato.notificationcontrol.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.data.FollowUpEntity
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun FollowUpScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    followUps: List<FollowUpEntity>,
    isPremium: Boolean,
    requestPostNotifications: () -> Unit,
    onComplete: (Long) -> Unit,
    onSnooze: (Long, Int) -> Unit,
    onSchedule: (Long, Long, Int?) -> Unit
) {
    val context = LocalContext.current
    val permissionGranted =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    val notificationsEnabled =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    var editing by remember { mutableStateOf<FollowUpEntity?>(null) }

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
                    item.repeatMinutes?.takeIf { it > 0 }?.let { repeat ->
                        Text(
                            stringResource(
                                R.string.follow_up_repeat_every,
                                formatRepeatMinutes(repeat)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
                    Row {
                        if (isPremium) {
                            TextButton(onClick = { editing = item }) {
                                Text(stringResource(R.string.follow_up_customize))
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

    editing?.let { item ->
        FollowUpScheduleDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { dueAt, repeatMinutes ->
                onSchedule(item.id, dueAt, repeatMinutes)
                editing = null
            }
        )
    }
}

@Composable
private fun FollowUpScheduleDialog(
    item: FollowUpEntity,
    onDismiss: () -> Unit,
    onSave: (Long, Int?) -> Unit
) {
    val context = LocalContext.current
    var dueAt by remember(item.id) {
        mutableStateOf(
            item.dueAt.coerceAtLeast(System.currentTimeMillis() + 60_000L)
        )
    }
    var repeatMinutes by remember(item.id) {
        mutableStateOf(item.repeatMinutes)
    }

    fun openDatePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val next = Calendar.getInstance().apply {
                    timeInMillis = dueAt
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                dueAt = next.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openTimePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val next = Calendar.getInstance().apply {
                    timeInMillis = dueAt
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                dueAt = next.timeInMillis
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    val valid = dueAt > System.currentTimeMillis() + 30_000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.follow_up_schedule_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM,
                        DateFormat.SHORT
                    ).format(Date(dueAt)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = ::openDatePicker) {
                        Text(stringResource(R.string.choose_date))
                    }
                    FilledTonalButton(onClick = ::openTimePicker) {
                        Text(stringResource(R.string.choose_time))
                    }
                }

                Text(
                    stringResource(R.string.follow_up_repeat_title),
                    fontWeight = FontWeight.SemiBold
                )
                val repeatOptions = listOf<Int?>(null, 15, 60, 24 * 60)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeatOptions.forEach { option ->
                        FilterChip(
                            selected = repeatMinutes == option,
                            onClick = { repeatMinutes = option },
                            label = {
                                Text(
                                    when (option) {
                                        null -> stringResource(R.string.follow_up_repeat_off)
                                        15 -> stringResource(R.string.minutes_short, 15)
                                        60 -> stringResource(R.string.follow_up_repeat_hour)
                                        else -> stringResource(R.string.follow_up_repeat_day)
                                    }
                                )
                            }
                        )
                    }
                }
                if (!valid) {
                    Text(
                        stringResource(R.string.follow_up_future_required),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(dueAt, repeatMinutes) },
                enabled = valid
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatRepeatMinutes(minutes: Int): String = when {
    minutes % (24 * 60) == 0 -> (minutes / (24 * 60)).toString() + " d"
    minutes % 60 == 0 -> (minutes / 60).toString() + " h"
    else -> minutes.toString() + " min"
}

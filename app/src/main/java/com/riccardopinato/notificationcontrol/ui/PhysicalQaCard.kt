package com.riccardopinato.notificationcontrol.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riccardopinato.notificationcontrol.BuildConfig
import com.riccardopinato.notificationcontrol.R
import com.riccardopinato.notificationcontrol.diagnostics.PhotoAccessScope
import com.riccardopinato.notificationcontrol.diagnostics.PhysicalQaProbe
import kotlinx.coroutines.delay

@Composable
fun PhysicalQaCard() {
    if (!BuildConfig.DEBUG && !BuildConfig.QA_PREMIUM_UNLOCKED) return

    val context = LocalContext.current
    val probe = remember(context) { PhysicalQaProbe(context) }
    val snapshot by produceState(initialValue = probe.snapshot(), probe) {
        while (true) {
            value = probe.snapshot()
            delay(1_000L)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.physical_qa_title),
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(
                    R.string.physical_qa_device,
                    snapshot.manufacturer,
                    snapshot.model,
                    snapshot.sdkInt
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(
                    R.string.physical_qa_listener,
                    qaStatus(snapshot.notificationListenerEnabled),
                    qaStatus(snapshot.notificationListenerConnected)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(
                    R.string.physical_qa_photo_scope,
                    when (snapshot.photoAccessScope) {
                        PhotoAccessScope.FULL -> stringResource(R.string.photo_scope_full)
                        PhotoAccessScope.SELECTED_ONLY ->
                            stringResource(R.string.photo_scope_selected_only)
                        PhotoAccessScope.NONE -> stringResource(R.string.photo_scope_none)
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(
                    R.string.physical_qa_saf,
                    qaStatus(snapshot.whatsAppSafLinked)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(
                    R.string.physical_qa_mediastore,
                    snapshot.mediaStoreVersion ?: "-",
                    snapshot.mediaStoreGeneration?.toString() ?: "-"
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(
                    R.string.physical_qa_timestamps,
                    snapshot.lastListenerConnectedAt,
                    snapshot.lastNotificationEventAt,
                    snapshot.lastReconciliationAt
                ),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                stringResource(
                    R.string.physical_qa_pipeline_perf,
                    snapshot.runtimePerformance.queueDepth,
                    snapshot.runtimePerformance.queueHighWater,
                    snapshot.runtimePerformance.processedCommands,
                    snapshot.runtimePerformance.averageCommandMs,
                    snapshot.runtimePerformance.maxCommandMs,
                    snapshot.runtimePerformance.slowCommands
                ),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                stringResource(
                    R.string.physical_qa_media_perf,
                    snapshot.runtimePerformance.mediaObserverSignals,
                    snapshot.runtimePerformance.mediaRecoveryRuns,
                    snapshot.runtimePerformance.averageMediaRecoveryMs,
                    snapshot.runtimePerformance.maxMediaRecoveryMs,
                    snapshot.runtimePerformance.lastMediaRecoveryRemaining
                ),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                stringResource(R.string.physical_qa_manual_note),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun qaStatus(value: Boolean): String =
    stringResource(if (value) R.string.physical_qa_yes else R.string.physical_qa_no)

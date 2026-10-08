package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.diagnostics.PipelineCommandType
import com.riccardopinato.notificationcontrol.diagnostics.RuntimePerformanceTelemetry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuntimePerformanceTelemetryTest {
    @Before
    fun setUp() {
        RuntimePerformanceTelemetry.resetForTests()
    }

    @After
    fun tearDown() {
        RuntimePerformanceTelemetry.resetForTests()
    }

    @Test
    fun tracksQueueHighWaterAndCommandLatency() {
        RuntimePerformanceTelemetry.commandQueued(PipelineCommandType.POSTED)
        RuntimePerformanceTelemetry.commandQueued(PipelineCommandType.REMOVED)
        RuntimePerformanceTelemetry.commandQueued(PipelineCommandType.RECONCILE)

        RuntimePerformanceTelemetry.commandProcessed(40_000_000L)
        RuntimePerformanceTelemetry.commandProcessed(260_000_000L)
        RuntimePerformanceTelemetry.commandProcessed(100_000_000L)

        val snapshot = RuntimePerformanceTelemetry.snapshot()
        assertEquals(0, snapshot.queueDepth)
        assertEquals(3, snapshot.queueHighWater)
        assertEquals(1L, snapshot.postedQueued)
        assertEquals(1L, snapshot.removedQueued)
        assertEquals(1L, snapshot.reconcileQueued)
        assertEquals(3L, snapshot.processedCommands)
        assertEquals(133L, snapshot.averageCommandMs)
        assertEquals(260L, snapshot.maxCommandMs)
        assertEquals(1L, snapshot.slowCommands)
    }

    @Test
    fun tracksMediaObserverAndRecoveryCost() {
        RuntimePerformanceTelemetry.mediaObserverSignal()
        RuntimePerformanceTelemetry.mediaObserverSignal()
        RuntimePerformanceTelemetry.mediaRecoveryCompleted(
            durationNs = 20_000_000L,
            remaining = 4
        )
        RuntimePerformanceTelemetry.mediaRecoveryCompleted(
            durationNs = 60_000_000L,
            remaining = 0
        )

        val snapshot = RuntimePerformanceTelemetry.snapshot()
        assertEquals(2L, snapshot.mediaObserverSignals)
        assertEquals(2L, snapshot.mediaRecoveryRuns)
        assertEquals(40L, snapshot.averageMediaRecoveryMs)
        assertEquals(60L, snapshot.maxMediaRecoveryMs)
        assertEquals(0, snapshot.lastMediaRecoveryRemaining)
        assertTrue(snapshot.maxMediaRecoveryMs >= snapshot.averageMediaRecoveryMs)
    }
}

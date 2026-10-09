package com.riccardopinato.notificationcontrol.diagnostics

import com.riccardopinato.notificationcontrol.BuildConfig
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class PipelineCommandType {
    POSTED,
    REMOVED,
    RECONCILE
}

data class RuntimePerformanceSnapshot(
    val queueDepth: Int,
    val queueHighWater: Int,
    val postedQueued: Long,
    val removedQueued: Long,
    val reconcileQueued: Long,
    val processedCommands: Long,
    val averageCommandMs: Long,
    val maxCommandMs: Long,
    val slowCommands: Long,
    val mediaObserverSignals: Long,
    val mediaRecoveryRuns: Long,
    val averageMediaRecoveryMs: Long,
    val maxMediaRecoveryMs: Long,
    val lastMediaRecoveryRemaining: Int
)

object RuntimePerformanceTelemetry {
    private const val SLOW_COMMAND_NS = 250_000_000L

    private val queueDepth = AtomicInteger()
    private val queueHighWater = AtomicInteger()
    private val postedQueued = AtomicLong()
    private val removedQueued = AtomicLong()
    private val reconcileQueued = AtomicLong()
    private val processedCommands = AtomicLong()
    private val totalCommandNs = AtomicLong()
    private val maxCommandNs = AtomicLong()
    private val slowCommands = AtomicLong()
    private val mediaObserverSignals = AtomicLong()
    private val mediaRecoveryRuns = AtomicLong()
    private val totalMediaRecoveryNs = AtomicLong()
    private val maxMediaRecoveryNs = AtomicLong()
    private val lastMediaRecoveryRemaining = AtomicInteger(-1)

    fun commandQueued(type: PipelineCommandType) {
        if (!BuildConfig.PERFORMANCE_DIAGNOSTICS_ENABLED) return

        when (type) {
            PipelineCommandType.POSTED -> postedQueued.incrementAndGet()
            PipelineCommandType.REMOVED -> removedQueued.incrementAndGet()
            PipelineCommandType.RECONCILE -> reconcileQueued.incrementAndGet()
        }
        val depth = queueDepth.incrementAndGet()
        updateMax(queueHighWater, depth)
    }

    fun commandProcessed(durationNs: Long) {
        if (!BuildConfig.PERFORMANCE_DIAGNOSTICS_ENABLED) return

        queueDepth.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        processedCommands.incrementAndGet()
        totalCommandNs.addAndGet(durationNs.coerceAtLeast(0L))
        updateMax(maxCommandNs, durationNs.coerceAtLeast(0L))
        if (durationNs >= SLOW_COMMAND_NS) {
            slowCommands.incrementAndGet()
        }
    }

    fun mediaObserverSignal() {
        if (!BuildConfig.PERFORMANCE_DIAGNOSTICS_ENABLED) return
        mediaObserverSignals.incrementAndGet()
    }

    fun mediaRecoveryCompleted(durationNs: Long, remaining: Int) {
        if (!BuildConfig.PERFORMANCE_DIAGNOSTICS_ENABLED) return

        mediaRecoveryRuns.incrementAndGet()
        totalMediaRecoveryNs.addAndGet(durationNs.coerceAtLeast(0L))
        updateMax(maxMediaRecoveryNs, durationNs.coerceAtLeast(0L))
        lastMediaRecoveryRemaining.set(remaining)
    }

    fun snapshot(): RuntimePerformanceSnapshot {
        val processed = processedCommands.get()
        val recoveryRuns = mediaRecoveryRuns.get()
        return RuntimePerformanceSnapshot(
            queueDepth = queueDepth.get(),
            queueHighWater = queueHighWater.get(),
            postedQueued = postedQueued.get(),
            removedQueued = removedQueued.get(),
            reconcileQueued = reconcileQueued.get(),
            processedCommands = processed,
            averageCommandMs = averageMillis(totalCommandNs.get(), processed),
            maxCommandMs = nanosToMillis(maxCommandNs.get()),
            slowCommands = slowCommands.get(),
            mediaObserverSignals = mediaObserverSignals.get(),
            mediaRecoveryRuns = recoveryRuns,
            averageMediaRecoveryMs = averageMillis(
                totalMediaRecoveryNs.get(),
                recoveryRuns
            ),
            maxMediaRecoveryMs = nanosToMillis(maxMediaRecoveryNs.get()),
            lastMediaRecoveryRemaining = lastMediaRecoveryRemaining.get()
        )
    }

    internal fun resetForTests() {
        queueDepth.set(0)
        queueHighWater.set(0)
        postedQueued.set(0)
        removedQueued.set(0)
        reconcileQueued.set(0)
        processedCommands.set(0)
        totalCommandNs.set(0)
        maxCommandNs.set(0)
        slowCommands.set(0)
        mediaObserverSignals.set(0)
        mediaRecoveryRuns.set(0)
        totalMediaRecoveryNs.set(0)
        maxMediaRecoveryNs.set(0)
        lastMediaRecoveryRemaining.set(-1)
    }

    private fun averageMillis(totalNs: Long, count: Long): Long =
        if (count <= 0L) 0L else nanosToMillis(totalNs / count)

    private fun nanosToMillis(value: Long): Long = value / 1_000_000L

    private fun updateMax(target: AtomicInteger, candidate: Int) {
        while (true) {
            val current = target.get()
            if (candidate <= current || target.compareAndSet(current, candidate)) return
        }
    }

    private fun updateMax(target: AtomicLong, candidate: Long) {
        while (true) {
            val current = target.get()
            if (candidate <= current || target.compareAndSet(current, candidate)) return
        }
    }
}

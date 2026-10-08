package com.riccardopinato.notificationcontrol.hardware

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class FlashCoordinator private constructor(context: Context) {
    private val cameraManager =
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NotificationControl-Stroboscope")
    }
    private val running = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private val unavailableUntilElapsed = AtomicLong(0)
    private var currentTask: Future<*>? = null
    private val cameraId: String? = findBackFlashCamera()

    @Synchronized
    fun startStrobe(
        cycles: Int = 5,
        onMs: Long = 150L,
        offMs: Long = 150L,
        onFirstEmission: (() -> Unit)? = null,
        onNoEmission: (() -> Unit)? = null
    ) {
        val id = cameraId ?: run {
            runCatching { onNoEmission?.invoke() }
            return
        }
        if (SystemClock.elapsedRealtime() < unavailableUntilElapsed.get()) {
            runCatching { onNoEmission?.invoke() }
            return
        }

        stopLocked()
        val safeCycles = cycles.coerceIn(1, 30)
        val safeOn = onMs.coerceIn(20L, 2_000L)
        val safeOff = offMs.coerceIn(20L, 2_000L)
        val myGeneration = generation.incrementAndGet()
        running.set(true)

        try {
            currentTask = executor.submit {
                var emissionSignaled = false
                try {
                    repeat(safeCycles) {
                        if (!isCurrent(myGeneration)) return@submit
                        if (!setTorch(id, true)) return@submit
                        if (!emissionSignaled) {
                            emissionSignaled = true
                            runCatching { onFirstEmission?.invoke() }
                        }
                        if (!sleep(safeOn, myGeneration)) return@submit
                        setTorch(id, false)
                        if (!sleep(safeOff, myGeneration)) return@submit
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    setTorch(id, false, recordFailure = false)
                    if (!emissionSignaled) {
                        runCatching { onNoEmission?.invoke() }
                    }
                    if (generation.get() == myGeneration) running.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            running.set(false)
            runCatching { onNoEmission?.invoke() }
        }
    }

    @Synchronized
    fun stop() {
        stopLocked()
        generation.incrementAndGet()
        running.set(false)
        setTorch(cameraId, false, recordFailure = false)
    }

    fun isAvailable(): Boolean = cameraId != null

    @Synchronized
    private fun stopLocked() {
        currentTask?.cancel(true)
        currentTask = null
    }

    private fun isCurrent(generationValue: Long): Boolean =
        running.get() &&
            generation.get() == generationValue &&
            !Thread.currentThread().isInterrupted

    private fun sleep(ms: Long, generationValue: Long): Boolean {
        Thread.sleep(ms)
        return isCurrent(generationValue)
    }

    private fun findBackFlashCamera(): String? = try {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (error: Exception) {
        Log.w(TAG, "Unable to detect back flash camera", error)
        null
    }

    private fun setTorch(
        id: String?,
        enabled: Boolean,
        recordFailure: Boolean = true
    ): Boolean {
        if (id == null || cameraManager == null) return false
        return try {
            cameraManager.setTorchMode(id, enabled)
            true
        } catch (error: CameraAccessException) {
            onTorchFailure(error, recordFailure)
            false
        } catch (error: SecurityException) {
            onTorchFailure(error, recordFailure)
            false
        } catch (error: IllegalArgumentException) {
            onTorchFailure(error, recordFailure)
            false
        }
    }

    private fun onTorchFailure(error: Exception, recordFailure: Boolean) {
        if (recordFailure) {
            unavailableUntilElapsed.set(
                SystemClock.elapsedRealtime() + CAMERA_FAILURE_BACKOFF_MS
            )
            Log.w(TAG, "Torch temporarily unavailable; applying backoff", error)
        }
    }

    companion object {
        private const val TAG = "FlashCoordinator"
        private const val CAMERA_FAILURE_BACKOFF_MS = 5_000L

        @Volatile
        private var instance: FlashCoordinator? = null

        fun get(context: Context): FlashCoordinator =
            instance ?: synchronized(this) {
                instance ?: FlashCoordinator(context).also { instance = it }
            }
    }
}

package com.riccardopinato.notificationcontrol.hardware

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class FlashCoordinator private constructor(context: Context) {
    private val cameraManager = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "NotificationControl-Stroboscope") }
    private val running = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private var currentTask: Future<*>? = null
    private val cameraId: String? = findBackFlashCamera()

    @Synchronized fun startStrobe(cycles: Int = 5, onMs: Long = 150L, offMs: Long = 150L) {
        val id = cameraId ?: return
        stopLocked()
        val safeCycles = cycles.coerceIn(1, 120); val safeOn = onMs.coerceIn(20L, 5_000L); val safeOff = offMs.coerceIn(20L, 5_000L)
        val myGeneration = generation.incrementAndGet(); running.set(true)
        try {
            currentTask = executor.submit {
                try {
                    repeat(safeCycles) {
                        if (!isCurrent(myGeneration)) return@submit
                        setTorch(id, true); if (!sleep(safeOn, myGeneration)) return@submit
                        setTorch(id, false); if (!sleep(safeOff, myGeneration)) return@submit
                    }
                } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                finally { setTorch(id, false); if (generation.get() == myGeneration) running.set(false) }
            }
        } catch (_: RejectedExecutionException) { running.set(false) }
    }
    @Synchronized fun stop() { stopLocked(); generation.incrementAndGet(); running.set(false); setTorch(cameraId, false) }
    fun isAvailable(): Boolean = cameraId != null
    @Synchronized private fun stopLocked() { currentTask?.cancel(true); currentTask = null }
    private fun isCurrent(g: Long): Boolean = running.get() && generation.get() == g && !Thread.currentThread().isInterrupted
    private fun sleep(ms: Long, g: Long): Boolean { Thread.sleep(ms); return isCurrent(g) }
    private fun findBackFlashCamera(): String? = try {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            val c = cameraManager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (e: Exception) { Log.w(TAG, "Unable to detect back flash camera", e); null }
    private fun setTorch(id: String?, enabled: Boolean) {
        if (id == null || cameraManager == null) return
        try { cameraManager.setTorchMode(id, enabled) } catch (_: CameraAccessException) {} catch (_: SecurityException) {} catch (_: IllegalArgumentException) {}
    }
    companion object {
        private const val TAG = "FlashCoordinator"
        @Volatile private var instance: FlashCoordinator? = null
        fun get(context: Context): FlashCoordinator = instance ?: synchronized(this) { instance ?: FlashCoordinator(context).also { instance = it } }
    }
}

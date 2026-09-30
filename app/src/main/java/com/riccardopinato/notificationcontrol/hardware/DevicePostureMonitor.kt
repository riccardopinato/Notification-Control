package com.riccardopinato.notificationcontrol.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class DevicePostureMonitor(context: Context) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximity = manager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val gravity = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val covered = AtomicBoolean(false)
    private val faceDown = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private var filteredZ = 0f
    private var hasZSample = false

    fun start() {
        if (!started.compareAndSet(false, true)) return
        proximity?.let {
            manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gravity?.let {
            manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        manager?.unregisterListener(this)
        covered.set(false)
        faceDown.set(false)
        filteredZ = 0f
        hasZSample = false
    }

    fun isCoveredOrFaceDown(): Boolean = covered.get() || faceDown.get()

    fun isStarted(): Boolean = started.get()

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val value = event.values.firstOrNull() ?: return
                val nearThreshold = min(event.sensor.maximumRange, 5f)
                covered.set(value < nearThreshold)
            }

            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                val rawZ = event.values.getOrNull(2) ?: return
                filteredZ = if (hasZSample) (filteredZ * 0.75f) + (rawZ * 0.25f) else rawZ
                hasZSample = true
                if (!faceDown.get() && filteredZ < -7.5f) {
                    faceDown.set(true)
                } else if (faceDown.get() && filteredZ > -5.0f) {
                    faceDown.set(false)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

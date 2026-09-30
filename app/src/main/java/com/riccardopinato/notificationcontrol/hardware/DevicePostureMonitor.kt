package com.riccardopinato.notificationcontrol.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.atomic.AtomicBoolean

class DevicePostureMonitor(context: Context) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximity = manager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val gravity = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val covered = AtomicBoolean(false)
    private val faceDown = AtomicBoolean(false)
    fun start() {
        proximity?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gravity?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }
    fun stop() { manager?.unregisterListener(this); covered.set(false); faceDown.set(false) }
    fun isCoveredOrFaceDown(): Boolean = covered.get() || faceDown.get()
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> covered.set((event.values.firstOrNull() ?: return) < event.sensor.maximumRange)
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> faceDown.set((event.values.getOrNull(2) ?: return) < -7.0f)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

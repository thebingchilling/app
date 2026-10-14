package app.quartz.camera.util

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.view.OrientationEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2

/** Device rotation snapped to 0/90/180/270, from the accelerometer (the UI stays portrait). */
class DeviceRotation(context: Context) {
    private val _rotation = MutableStateFlow(0)
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    private val listener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val current = _rotation.value
            // Hysteresis: only switch once clearly past the 45° boundary.
            val diff = ((orientation - current + 540) % 360) - 180
            if (kotlin.math.abs(diff) > 55) _rotation.value = ((orientation + 45) / 90 * 90) % 360
        }
    }

    fun start() {
        if (listener.canDetectOrientation()) listener.enable()
    }

    fun stop() = listener.disable()
}

/** Roll angle for the level overlay, in degrees (0 = level in portrait). */
class LevelSensor(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll.asStateFlow()

    fun start() {
        sensor?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() = manager?.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val angle = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        _roll.value = _roll.value * 0.8f + angle * 0.2f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Last known location from the platform LocationManager (no Play Services). */
class LocationTracker(context: Context) : LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)
    @Volatile var last: Location? = null
        private set

    @SuppressLint("MissingPermission")
    fun start() {
        val m = manager ?: return
        runCatching {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (!m.isProviderEnabled(p)) continue
                m.getLastKnownLocation(p)?.let { if (last == null || it.time > last!!.time) last = it }
                m.requestLocationUpdates(p, 10_000L, 10f, this, Looper.getMainLooper())
            }
        }
    }

    fun stop() {
        runCatching { manager?.removeUpdates(this) }
    }

    override fun onLocationChanged(location: Location) {
        last = location
    }
}

package com.sakina.app.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.sakina.app.core.qibla.AngleFilter
import com.sakina.app.core.qibla.CompassMath
import com.sakina.app.core.qibla.CompassReading
import com.sakina.app.core.qibla.CompassSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/** A compass reading as the sensors gave it, before the age is known. */
data class CompassRaw(
    val source: CompassSource,
    val magneticHeading: Double,
    val tiltDegrees: Double,
    val accuracyStatus: Int,
    val headingAccuracy: Double?,
    val fieldMicroTesla: Double?,
    val atElapsedMs: Long,
) {
    fun reading(nowElapsedMs: Long) = CompassReading(source, magneticHeading, tiltDegrees, accuracyStatus, headingAccuracy, fieldMicroTesla,
        (nowElapsedMs - atElapsedMs).coerceAtLeast(0))
}

/**
 * Reads the device's compass from the best sensors available: the fused rotation vector, the geomagnetic
 * rotation vector (no gyroscope), or raw accelerometer + magnetometer combined here. Sensors are registered
 * only between [start] and [stop]; the qibla screen calls these as it appears and leaves.
 */
class CompassEngine(context: Context, private val displayRotation: () -> Int) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val _raw = MutableStateFlow<CompassRaw?>(null)
    val raw: StateFlow<CompassRaw?> = _raw.asStateFlow()

    var source: CompassSource = CompassSource.NONE
        private set
    var running = false
        private set

    private var magAccuracy = -1
    private var rvAccuracy = -1
    private var field: Double? = null
    private val gravity = FloatArray(3)
    private val geomag = FloatArray(3)
    private var haveGravity = false
    private var haveGeomag = false
    private val filter = AngleFilter(0.25)
    private val r = FloatArray(9)
    private val remapped = FloatArray(9)

    fun best(): CompassSource {
        val forced = forcedSource
        if (forced != null) return forced
        val mag = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sm == null || mag == null) return CompassSource.NONE
        return when {
            sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null -> CompassSource.ROTATION_VECTOR
            sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) != null -> CompassSource.GEOMAGNETIC_ROTATION_VECTOR
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null -> CompassSource.ACCEL_MAGNETOMETER
            else -> CompassSource.NONE
        }
    }

    fun start() {
        if (running) return
        source = best()
        _raw.value = null
        haveGravity = false; haveGeomag = false; field = null; magAccuracy = -1; rvAccuracy = -1
        filter.reset()
        val sm = sm ?: return
        val rate = SensorManager.SENSOR_DELAY_UI
        fun reg(type: Int) = sm.getDefaultSensor(type)?.let { sm.registerListener(this, it, rate) } ?: false
        val ok = when (source) {
            CompassSource.ROTATION_VECTOR -> reg(Sensor.TYPE_ROTATION_VECTOR)
            CompassSource.GEOMAGNETIC_ROTATION_VECTOR -> reg(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
            CompassSource.ACCEL_MAGNETOMETER -> reg(Sensor.TYPE_ACCELEROMETER)
            CompassSource.NONE -> false
        }
        if (!ok) source = CompassSource.NONE
        // The magnetometer itself: its strength reveals interference and its accuracy the calibration state.
        if (source != CompassSource.NONE) reg(Sensor.TYPE_MAGNETIC_FIELD)
        running = true
        Log.i(TAG, "sensors start source=$source")
    }

    fun stop() {
        if (!running) return
        sm?.unregisterListener(this)
        running = false
        Log.i(TAG, "sensors stop")
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        when (sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> magAccuracy = accuracy
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> rvAccuracy = accuracy
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val v = e.values
                field = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
                if (magAccuracy < 0) magAccuracy = e.accuracy
                if (source == CompassSource.ACCEL_MAGNETOMETER) {
                    lowPass(v, geomag, if (haveGeomag) 0.25f else 1f); haveGeomag = true
                    fromRaw(e.timestamp)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(e.values, gravity, if (haveGravity) 0.15f else 1f); haveGravity = true
                fromRaw(e.timestamp)
            }
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                val v = e.values
                SensorManager.getRotationMatrixFromVector(r, if (v.size > 4) v.copyOf(4) else v)
                val acc = if (v.size > 4 && v[4] > 0f) Math.toDegrees(v[4].toDouble()) else null
                if (rvAccuracy < 0) rvAccuracy = e.accuracy
                publish(r, acc, e.timestamp)
            }
        }
    }

    private fun fromRaw(timestampNs: Long) {
        if (!haveGravity || !haveGeomag) return
        val m = CompassMath.rotationFrom(gravity, geomag) ?: return
        System.arraycopy(m, 0, r, 0, 9)
        publish(r, null, timestampNs, smooth = false)
    }

    private fun publish(matrix: FloatArray, headingAccuracy: Double?, @Suppress("UNUSED_PARAMETER") timestampNs: Long, smooth: Boolean = false) {
        val (x, y) = when (displayRotation()) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        val m = if (x == SensorManager.AXIS_X && y == SensorManager.AXIS_Y) matrix
        else { SensorManager.remapCoordinateSystem(matrix, x, y, remapped); remapped }
        val (heading, tilt) = CompassMath.headingAndTilt(m)
        val h = if (source == CompassSource.ACCEL_MAGNETOMETER || smooth) filter.update(heading) else heading
        // The magnetometer's own accuracy says whether it needs calibrating; the fused sensor's is a fallback.
        val status = if (magAccuracy >= 0) magAccuracy else rvAccuracy
        // Arrival time, not the event's timestamp: its time base differs between devices.
        _raw.value = CompassRaw(source, h, tilt, status, headingAccuracy, field, SystemClock.elapsedRealtime())
    }

    private fun lowPass(input: FloatArray, out: FloatArray, a: Float) {
        for (i in 0..2) out[i] += a * (input[i] - out[i])
    }

    companion object {
        const val TAG = "SakinaQibla"

        /** Debug builds can force a sensor path (or none) to exercise every fallback. */
        @Volatile var forcedSource: CompassSource? = null
    }
}

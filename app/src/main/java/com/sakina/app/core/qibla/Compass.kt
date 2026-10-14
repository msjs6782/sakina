package com.sakina.app.core.qibla

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Which sensors the compass is built on, best first. */
enum class CompassSource {
    /** Fused rotation vector: accelerometer, gyroscope and magnetometer. */
    ROTATION_VECTOR,

    /** Accelerometer and magnetometer fused by the system, without a gyroscope. */
    GEOMAGNETIC_ROTATION_VECTOR,

    /** Raw accelerometer and magnetometer, combined and smoothed by the app. */
    ACCEL_MAGNETOMETER,

    /** No magnetometer: no compass, only the computed bearing and the map. */
    NONE,
}

enum class CompassIssue {
    NO_SENSOR,
    LOW_ACCURACY,
    TILT,
    INTERFERENCE,
    STALE,
    POLE_CAUTION,
    POLE_BLACKOUT,
    MODEL_EXPIRED,
}

/** One evaluated compass state. Headings are degrees clockwise from north. */
data class CompassReading(
    val source: CompassSource,
    val magneticHeading: Double,
    val tiltDegrees: Double,
    /** Android accuracy status of the magnetometer/rotation sensor: 0 unreliable … 3 high, −1 unknown. */
    val accuracyStatus: Int,
    /** The rotation vector's own heading accuracy estimate in degrees, or null when the sensor gives none. */
    val headingAccuracy: Double?,
    /** Measured magnetic field strength, microtesla, or null without a magnetometer reading. */
    val fieldMicroTesla: Double?,
    val ageMillis: Long,
)

object CompassMath {
    const val TILT_WARN = 30.0
    const val TILT_LIMIT = 60.0
    const val STALE_MS = 2_000L
    const val INTERFERENCE_RATIO = 0.25
    const val ACCURACY_WARN_DEG = 20.0
    const val ALIGN_DEG = 3.0
    const val REARM_DEG = 8.0

    /**
     * Heading of the device's top edge (its Y axis) and the screen's tilt from level, from a row-major 3×3
     * rotation matrix that maps device coordinates to world (east, north, up) coordinates.
     */
    fun headingAndTilt(r: FloatArray): Pair<Double, Double> {
        // Device Y axis in world coordinates is the second column; device Z (out of the screen) the third.
        val yE = r[1].toDouble()
        val yN = r[4].toDouble()
        val zU = r[8].toDouble().coerceIn(-1.0, 1.0)
        val tilt = Math.toDegrees(acos(zU))
        val heading = if (yE * yE + yN * yN < 1e-6) {
            // Top edge pointing straight up or down: use the screen's normal (the back camera's direction) instead.
            Qibla.norm360(Math.toDegrees(atan2(-r[2].toDouble(), -r[5].toDouble())))
        } else {
            Qibla.norm360(Math.toDegrees(atan2(yE, yN)))
        }
        return heading to tilt
    }

    /**
     * Rotation matrix from gravity and the magnetic field in device coordinates, the same construction as
     * Android's SensorManager.getRotationMatrix. Returns null in free fall or when the vectors are parallel.
     */
    fun rotationFrom(gravity: FloatArray, geomagnetic: FloatArray): FloatArray? {
        val ax = gravity[0].toDouble(); val ay = gravity[1].toDouble(); val az = gravity[2].toDouble()
        val ex = geomagnetic[0].toDouble(); val ey = geomagnetic[1].toDouble(); val ez = geomagnetic[2].toDouble()
        val normsqA = ax * ax + ay * ay + az * az
        if (normsqA < (0.1 * 9.81) * (0.1 * 9.81)) return null
        var hx = ey * az - ez * ay
        var hy = ez * ax - ex * az
        var hz = ex * ay - ey * ax
        val normH = sqrt(hx * hx + hy * hy + hz * hz)
        if (normH < 0.1) return null
        hx /= normH; hy /= normH; hz /= normH
        val invA = 1.0 / sqrt(normsqA)
        val gx = ax * invA; val gy = ay * invA; val gz = az * invA
        val mx = gy * hz - gz * hy
        val my = gz * hx - gx * hz
        val mz = gx * hy - gy * hx
        return floatArrayOf(
            hx.toFloat(), hy.toFloat(), hz.toFloat(),
            mx.toFloat(), my.toFloat(), mz.toFloat(),
            gx.toFloat(), gy.toFloat(), gz.toFloat(),
        )
    }

    fun issues(
        reading: CompassReading?,
        expectedMicroTesla: Double?,
        zone: PoleZone?,
        modelValid: Boolean,
    ): Set<CompassIssue> {
        val out = linkedSetOf<CompassIssue>()
        if (reading == null || reading.source == CompassSource.NONE) {
            out += CompassIssue.NO_SENSOR
        } else {
            if (reading.ageMillis > STALE_MS) out += CompassIssue.STALE
            val lowStatus = reading.accuracyStatus in 0..1
            val lowEstimate = reading.headingAccuracy != null && reading.headingAccuracy > ACCURACY_WARN_DEG
            if (lowStatus || lowEstimate) out += CompassIssue.LOW_ACCURACY
            if (reading.tiltDegrees > TILT_WARN) out += CompassIssue.TILT
            val f = reading.fieldMicroTesla
            if (f != null && expectedMicroTesla != null && expectedMicroTesla > 0 &&
                abs(f - expectedMicroTesla) / expectedMicroTesla > INTERFERENCE_RATIO
            ) out += CompassIssue.INTERFERENCE
        }
        when (zone) {
            PoleZone.BLACKOUT -> out += CompassIssue.POLE_BLACKOUT
            PoleZone.CAUTION -> out += CompassIssue.POLE_CAUTION
            else -> {}
        }
        if (!modelValid) out += CompassIssue.MODEL_EXPIRED
        return out
    }

    /** Issues that make the needle untrustworthy (as opposed to advisories). */
    fun blocksAlignment(issues: Set<CompassIssue>) = issues.any {
        it == CompassIssue.NO_SENSOR || it == CompassIssue.LOW_ACCURACY || it == CompassIssue.TILT ||
            it == CompassIssue.INTERFERENCE || it == CompassIssue.STALE || it == CompassIssue.POLE_BLACKOUT
    }
}

/** Smooths angles through their unit vectors, so 359° and 1° average to 0°, not 180°. */
class AngleFilter(private val alpha: Double = 0.2) {
    private var s = 0.0
    private var c = 0.0
    private var primed = false

    fun reset() { primed = false }

    fun update(degrees: Double): Double {
        val r = Math.toRadians(degrees)
        if (!primed) {
            s = sin(r); c = cos(r); primed = true
        } else {
            s += alpha * (sin(r) - s)
            c += alpha * (cos(r) - c)
        }
        return Qibla.norm360(Math.toDegrees(atan2(s, c)))
    }
}

/** Fires once when the device lines up with the qibla, and re-arms only after it has clearly moved away. */
class AlignmentTracker(private val alignDeg: Double = CompassMath.ALIGN_DEG, private val rearmDeg: Double = CompassMath.REARM_DEG) {
    var aligned = false
        private set
    private var armed = true

    /** Returns true at the moment of a new reliable alignment. */
    fun update(offsetDegrees: Double, reliable: Boolean): Boolean {
        val off = abs(offsetDegrees)
        if (!reliable) {
            aligned = false
            if (off > rearmDeg) armed = true
            return false
        }
        aligned = off <= alignDeg
        if (off > rearmDeg) armed = true
        if (aligned && armed) {
            armed = false
            return true
        }
        return false
    }
}

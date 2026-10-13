package com.sakina.app.core.prayer

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/*
 * Solar position equations from Jean Meeus, "Astronomical Algorithms" (2nd ed.).
 * The structure (transit, corrected hour angle with interpolation, the date-line guard and the
 * afternoon-shadow checks) follows the open-source Adhan library (MIT License, Batoul Apps),
 * which is also used as an independent reference in the unit tests.
 */

private fun Double.rad() = Math.toRadians(this)
private fun Double.deg() = Math.toDegrees(this)

internal fun normalize(value: Double, max: Double) = value - max * floor(value / max)
internal fun unwind(angle: Double) = normalize(angle, 360.0)
internal fun closestAngle(angle: Double) =
    if (angle >= -180 && angle <= 180) angle else angle - 360 * (angle / 360).roundToInt()

internal object Meeus {
    /** Julian day at 0h UT of a Gregorian date (page 60). */
    fun julianDay(year: Int, month: Int, day: Int, hours: Double = 0.0): Double {
        val y = if (month > 2) year else year - 1
        val m = if (month > 2) month else month + 12
        val d = day + hours / 24
        val a = y / 100
        val b = 2 - a + a / 4
        val i0 = (365.25 * (y + 4716)).toInt()
        val i1 = (30.6001 * (m + 1)).toInt()
        return i0 + i1 + d + b - 1524.5
    }

    fun julianCentury(jd: Double) = (jd - 2451545.0) / 36525

    fun meanSolarLongitude(t: Double) = unwind(280.4664567 + 36000.76983 * t + 0.0003032 * t * t)
    fun meanLunarLongitude(t: Double) = unwind(218.3165 + 481267.8813 * t)
    fun ascendingLunarNode(t: Double) = unwind(125.04452 - 1934.136261 * t + 0.0020708 * t * t + t * t * t / 450000)
    fun meanSolarAnomaly(t: Double) = unwind(357.52911 + 35999.05029 * t - 0.0001537 * t * t)

    fun equationOfCenter(t: Double, m: Double): Double {
        val r = m.rad()
        return (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(r) +
            (0.019993 - 0.000101 * t) * sin(2 * r) + 0.000289 * sin(3 * r)
    }

    fun apparentSolarLongitude(t: Double, l0: Double): Double {
        val longitude = l0 + equationOfCenter(t, meanSolarAnomaly(t))
        val omega = 125.04 - 1934.136 * t
        return unwind(longitude - 0.00569 - 0.00478 * sin(omega.rad()))
    }

    fun meanObliquity(t: Double) = 23.439291 - 0.013004167 * t - 0.0000001639 * t * t + 0.0000005036 * t * t * t

    fun apparentObliquity(t: Double, e0: Double) = e0 + 0.00256 * cos((125.04 - 1934.136 * t).rad())

    fun meanSiderealTime(t: Double): Double {
        val jd = t * 36525 + 2451545.0
        return unwind(280.46061837 + 360.98564736629 * (jd - 2451545) + 0.000387933 * t * t - t * t * t / 38710000)
    }

    fun nutationInLongitude(l0: Double, lp: Double, omega: Double) =
        -17.2 / 3600 * sin(omega.rad()) - 1.32 / 3600 * sin(2 * l0.rad()) -
            0.23 / 3600 * sin(2 * lp.rad()) + 0.21 / 3600 * sin(2 * omega.rad())

    fun nutationInObliquity(l0: Double, lp: Double, omega: Double) =
        9.2 / 3600 * cos(omega.rad()) + 0.57 / 3600 * cos(2 * l0.rad()) +
            0.10 / 3600 * cos(2 * lp.rad()) - 0.09 / 3600 * cos(2 * omega.rad())

    fun altitude(lat: Double, dec: Double, hourAngle: Double) =
        asin(sin(lat.rad()) * sin(dec.rad()) + cos(lat.rad()) * cos(dec.rad()) * cos(hourAngle.rad())).deg()

    fun interpolate(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = y2 - y1
        val b = y3 - y2
        return y2 + n / 2 * (a + b + n * (b - a))
    }

    fun interpolateAngles(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = unwind(y2 - y1)
        val b = unwind(y3 - y2)
        return y2 + n / 2 * (a + b + n * (b - a))
    }
}

internal class SolarCoordinates(jd: Double) {
    val declination: Double
    val rightAscension: Double
    val siderealTime: Double

    init {
        val t = Meeus.julianCentury(jd)
        val l0 = Meeus.meanSolarLongitude(t)
        val lp = Meeus.meanLunarLongitude(t)
        val omega = Meeus.ascendingLunarNode(t)
        val lambda = Meeus.apparentSolarLongitude(t, l0).rad()
        val theta0 = Meeus.meanSiderealTime(t)
        val dPsi = Meeus.nutationInLongitude(l0, lp, omega)
        val dEps = Meeus.nutationInObliquity(l0, lp, omega)
        val e0 = Meeus.meanObliquity(t)
        val eApp = Meeus.apparentObliquity(t, e0).rad()
        declination = asin(sin(eApp) * sin(lambda)).deg()
        rightAscension = unwind(atan2(cos(eApp) * sin(lambda), cos(lambda)).deg())
        siderealTime = theta0 + dPsi * cos((e0 + dEps).rad())
    }
}

/**
 * Solar events for one calendar date at one place. All results are hours after 0h UT of [year]-[month]-[day]
 * (they may be negative or above 24 for places far from Greenwich), or NaN when the event does not happen.
 */
internal class SolarDay(year: Int, month: Int, day: Int, private val lat: Double, private val lon: Double) {
    private val solar: SolarCoordinates
    private val prev: SolarCoordinates
    private val next: SolarCoordinates
    private val m0: Double

    val transit: Double
    val sunrise: Double
    val sunset: Double

    init {
        val jd = Meeus.julianDay(year, month, day)
        prev = SolarCoordinates(jd - 1)
        solar = SolarCoordinates(jd)
        next = SolarCoordinates(jd + 1)
        m0 = approximateTransit()
        transit = correctedTransit()
        sunrise = hoursForAltitude(-50.0 / 60.0, afterTransit = false)
        sunset = hoursForAltitude(-50.0 / 60.0, afterTransit = true)
    }

    private fun approximateTransit(): Double {
        val lw = -lon
        val m = normalize((solar.rightAscension + lw - solar.siderealTime) / 360, 1.0)
        // Near the international date line the normalized value can land on the wrong day.
        val expected = normalize((12.0 - lon / 15.0) / 24.0, 1.0)
        return when {
            m - expected > 0.5 -> m - 1.0
            expected - m > 0.5 -> m + 1.0
            else -> m
        }
    }

    private fun correctedTransit(): Double {
        val lw = -lon
        val theta = unwind(solar.siderealTime + 360.985647 * m0)
        val alpha = unwind(Meeus.interpolateAngles(solar.rightAscension, prev.rightAscension, next.rightAscension, m0))
        val h = closestAngle(theta - lw - alpha)
        return (m0 + h / -360) * 24
    }

    /** The sun's altitude at transit, in degrees. */
    val noonAltitudeDegrees: Double get() = Meeus.altitude(lat, solar.declination, 0.0)

    /** Hours (UT) when the sun's centre reaches [altitude] degrees, before or after transit. */
    fun hoursForAltitude(altitude: Double, afterTransit: Boolean): Double {
        val lw = -lon
        val cosH0 = (sin(altitude.rad()) - sin(lat.rad()) * sin(solar.declination.rad())) /
            (cos(lat.rad()) * cos(solar.declination.rad()))
        if (cosH0.isNaN() || cosH0 < -1.0 || cosH0 > 1.0) return Double.NaN
        val h0 = acos(cosH0).deg()
        var m = if (afterTransit) m0 + h0 / 360 else m0 - h0 / 360
        for (i in 0 until 10) {
            val theta = unwind(solar.siderealTime + 360.985647 * m)
            val alpha = unwind(Meeus.interpolateAngles(solar.rightAscension, prev.rightAscension, next.rightAscension, m))
            val delta = Meeus.interpolate(solar.declination, prev.declination, next.declination, m)
            val h = theta - lw - alpha
            val alt = Meeus.altitude(lat, delta, h)
            val denominator = 360 * cos(delta.rad()) * cos(lat.rad()) * sin(h.rad())
            if (denominator == 0.0) return Double.NaN
            val dm = (alt - altitude) / denominator
            m += dm
            if (abs(dm) <= 0.01) break
        }
        return m * 24
    }

    /** Asr: the time when an object's shadow equals [shadowFactor] times its length plus the noon shadow. */
    fun afternoon(shadowFactor: Double): Double {
        val tangent = abs(lat - solar.declination)
        val inverse = shadowFactor + tan(tangent.rad())
        val angle = atan(1.0 / inverse).deg()
        // The whole solar disc (about 32') must be above the horizon.
        if (angle <= 16.0 / 60.0) return Double.NaN
        val maxAltitude = Meeus.altitude(lat, solar.declination, 0.0)
        if (maxAltitude < angle) return Double.NaN
        val result = hoursForAltitude(angle, afterTransit = true)
        if (result.isNaN() || result <= transit) return Double.NaN
        return result
    }
}

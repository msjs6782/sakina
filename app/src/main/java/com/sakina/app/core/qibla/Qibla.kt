package com.sakina.app.core.qibla

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle direction and distance to the Kaaba, on a sphere. */
object Qibla {
    /** The Kaaba, WGS-84. */
    const val KAABA_LAT = 21.422487
    const val KAABA_LON = 39.826206

    /** Mean Earth radius (IUGG), km. */
    const val EARTH_RADIUS_KM = 6371.0088

    /** Within this distance the direction is meaningless (you are at the Kaaba). */
    const val NEAR_KM = 0.05

    /** Initial great-circle bearing from a point to the Kaaba, degrees clockwise from true north, 0..360. */
    fun bearing(lat: Double, lon: Double): Double = initialBearing(lat, lon, KAABA_LAT, KAABA_LON)

    fun distanceKm(lat: Double, lon: Double): Double = distance(lat, lon, KAABA_LAT, KAABA_LON)

    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return norm360(Math.toDegrees(atan2(y, x)))
    }

    /** Haversine distance, km. */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Points along the great circle from (lat1, lon1) to (lat2, lon2), inclusive, as (lat, lon) pairs. */
    fun greatCircle(lat1: Double, lon1: Double, lat2: Double, lon2: Double, steps: Int = 128): List<Pair<Double, Double>> {
        val a = unit(lat1, lon1)
        val b = unit(lat2, lon2)
        val dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1.0, 1.0)
        val omega = acos(dot)
        if (omega < 1e-9) return listOf(lat1 to lon1, lat2 to lon2)
        val s = sin(omega)
        return (0..steps).map { i ->
            val t = i.toDouble() / steps
            val k1 = sin((1 - t) * omega) / s
            val k2 = sin(t * omega) / s
            val x = k1 * a[0] + k2 * b[0]
            val y = k1 * a[1] + k2 * b[1]
            val z = k1 * a[2] + k2 * b[2]
            Math.toDegrees(atan2(z, sqrt(x * x + y * y))) to Math.toDegrees(atan2(y, x))
        }
    }

    private fun unit(lat: Double, lon: Double): DoubleArray {
        val p = Math.toRadians(lat)
        val l = Math.toRadians(lon)
        return doubleArrayOf(cos(p) * cos(l), cos(p) * sin(l), sin(p))
    }

    fun norm360(d: Double): Double {
        val r = d % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** Signed smallest difference a − b in degrees, −180..180. */
    fun diff(a: Double, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    /** One of 16 compass points for a bearing: 0 = N, 1 = NNE, … 15 = NNW. */
    fun compassPoint(bearing: Double): Int = ((norm360(bearing) + 11.25) / 22.5).toInt() % 16
}

/** Map projections for the offline qibla map. Coordinates out are in a unit square-ish plane. */
object Projection {
    /**
     * Azimuthal equidistant projection centred on (lat0, lon0): distances and directions from the centre are
     * true, so the path to the Kaaba is a straight line at the qibla bearing. Returns (x east, y north) in
     * radians of arc, or null for the centre's antipode, which has no single position.
     */
    fun azimuthal(lat0: Double, lon0: Double, lat: Double, lon: Double): Pair<Double, Double>? {
        val p0 = Math.toRadians(lat0)
        val p = Math.toRadians(lat)
        val dl = Math.toRadians(lon - lon0)
        val cosC = (sin(p0) * sin(p) + cos(p0) * cos(p) * cos(dl)).coerceIn(-1.0, 1.0)
        val c = acos(cosC)
        if (c < 1e-12) return 0.0 to 0.0
        if (abs(Math.PI - c) < 1e-6) return null
        val k = c / sin(c)
        val x = k * cos(p) * sin(dl)
        val y = k * (cos(p0) * sin(p) - sin(p0) * cos(p) * cos(dl))
        return x to y
    }

    /** Equirectangular (plate carrée) centred on lon0: x = longitude, y = latitude, degrees. */
    fun equirect(lon0: Double, lat: Double, lon: Double): Pair<Double, Double> {
        var x = lon - lon0
        while (x > 180) x -= 360
        while (x < -180) x += 360
        return x to lat
    }
}

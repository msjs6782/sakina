package com.sakina.app.core

import com.sakina.app.core.qibla.AlignmentTracker
import com.sakina.app.core.qibla.AngleFilter
import com.sakina.app.core.qibla.CompassIssue
import com.sakina.app.core.qibla.CompassMath
import com.sakina.app.core.qibla.CompassReading
import com.sakina.app.core.qibla.CompassSource
import com.sakina.app.core.qibla.PoleZone
import com.sakina.app.core.qibla.Projection
import com.sakina.app.core.qibla.Qibla
import com.sakina.app.core.qibla.Wmm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class QiblaTest {
    private val wmm by lazy { Wmm.parse(File("src/main/assets/qibla/WMM.COF").readText()) }

    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun wmmHeaderAndLife() {
        assertEquals(2025.0, wmm.epoch, 0.0)
        assertEquals("WMM-2025", wmm.name)
        assertTrue(wmm.isValidAt(2026.8))
        assertFalse(wmm.isValidAt(2030.0))
    }

    /** NOAA's official WMM2025 test values (100 points with heights, dates across the model's life). */
    @Test
    fun wmmMatchesNoaaTestValues() {
        var n = 0
        for (line in resource("wmm/WMM2025_TestValues.txt").lines()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val f = t.split(Regex("\\s+")).map { it.toDouble() }
            val (year, alt, lat, lon) = f
            val r = wmm.field(lat, lon, alt, year)
            val where = "at $lat,$lon ${alt}km $year"
            assertEquals("D $where", f[4], r.declination, 0.0051)
            assertEquals("I $where", f[5], r.inclination, 0.0051)
            assertEquals("H $where", f[6], r.horizontal, 0.05)
            assertEquals("X $where", f[7], r.x, 0.05)
            assertEquals("Y $where", f[8], r.y, 0.05)
            assertEquals("Z $where", f[9], r.z, 0.05)
            assertEquals("F $where", f[10], r.total, 0.05)
            assertEquals("dD/dt $where", f[11], r.declinationPerYear, 0.01)
            n++
        }
        assertEquals(100, n)
    }

    /** The second NOAA table (WMM2025 report, 12 rows with rounded values). */
    @Test
    fun wmmMatchesReportTable() {
        var n = 0
        for (line in resource("wmm/WMM2025_TEST_VALUES_2.txt").lines()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val f = t.split(Regex("\\s+")).map { it.toDoubleOrNull() ?: Double.NaN }
            val r = wmm.field(f[2], f[3], f[1], f[0])
            assertEquals(f[4], r.x, 0.051)
            assertEquals(f[5], r.y, 0.051)
            assertEquals(f[6], r.z, 0.051)
            assertEquals(f[8], r.total, 0.051)
            assertEquals(f[9], r.inclination, 0.0051)
            assertEquals(f[10], r.declination, 0.0051)
            n++
        }
        assertEquals(12, n)
    }

    @Test
    fun poleZones() {
        // Close to the north magnetic pole the horizontal field is weak.
        assertEquals(PoleZone.BLACKOUT, wmm.field(85.0, 140.0, 0.0, 2026.0).zone)
        assertEquals(PoleZone.NORMAL, wmm.field(24.7, 46.7, 0.0, 2026.0).zone)
    }

    /** Direction from an independent vector construction: the tangent-plane direction of the Kaaba. */
    private fun vectorBearing(lat: Double, lon: Double): Double {
        fun u(la: Double, lo: Double): DoubleArray {
            val p = Math.toRadians(la); val l = Math.toRadians(lo)
            return doubleArrayOf(cos(p) * cos(l), cos(p) * sin(l), sin(p))
        }
        val a = u(lat, lon)
        val k = u(Qibla.KAABA_LAT, Qibla.KAABA_LON)
        val p = Math.toRadians(lat); val l = Math.toRadians(lon)
        val east = doubleArrayOf(-sin(l), cos(l), 0.0)
        val north = doubleArrayOf(-sin(p) * cos(l), -sin(p) * sin(l), cos(p))
        // Component of k perpendicular to a.
        val d = a[0] * k[0] + a[1] * k[1] + a[2] * k[2]
        val t = DoubleArray(3) { k[it] - d * a[it] }
        val e = t[0] * east[0] + t[1] * east[1] + t[2] * east[2]
        val n = t[0] * north[0] + t[1] * north[1] + t[2] * north[2]
        return Qibla.norm360(Math.toDegrees(atan2(e, n)))
    }

    @Test
    fun bearingsAgreeWithIndependentConstructionAndKnownValues() {
        val places = listOf(
            Triple("New York", 40.7128, -74.0060),
            Triple("London", 51.5074, -0.1278),
            Triple("Jakarta", -6.2088, 106.8456),
            Triple("Riyadh", 24.7136, 46.6753),
            Triple("Cairo", 30.0444, 31.2357),
            Triple("Sydney", -33.8688, 151.2093),
            Triple("Tromsø", 69.6492, 18.9553),
            Triple("Anchorage", 61.2181, -149.9003),
            Triple("Medina", 24.4672, 39.6111),
        )
        for ((name, lat, lon) in places) {
            assertEquals(name, vectorBearing(lat, lon), Qibla.bearing(lat, lon), 1e-6)
        }
        // Widely published great-circle values (rounded to 0.1°).
        assertEquals(58.5, Qibla.bearing(40.7128, -74.0060), 0.1)
        assertEquals(119.0, Qibla.bearing(51.5074, -0.1278), 0.1)
        assertEquals(295.2, Qibla.bearing(-6.2088, 106.8456), 0.1)
        // Medina is almost due south of Mecca.
        val medina = Qibla.bearing(24.4672, 39.6111)
        assertTrue(medina in 170.0..180.0)
    }

    @Test
    fun distances() {
        assertEquals(790.0, Qibla.distanceKm(24.7136, 46.6753), 10.0)
        assertEquals(10_300.0, Qibla.distanceKm(40.7128, -74.0060), 100.0)
        assertTrue(Qibla.distanceKm(Qibla.KAABA_LAT, Qibla.KAABA_LON) < Qibla.NEAR_KM)
    }

    @Test
    fun greatCircleEndsAndPassesNorthFromNewYork() {
        val path = Qibla.greatCircle(40.7128, -74.0060, Qibla.KAABA_LAT, Qibla.KAABA_LON, 64)
        assertEquals(65, path.size)
        assertEquals(40.7128, path.first().first, 1e-9)
        assertEquals(39.826206, path.last().second, 1e-9)
        // The shortest path from New York heads north-east and rises above New York's latitude.
        assertTrue(path.maxOf { it.first } > 45.0)
        // Every point lies on the plane of the great circle: its bearing from the start stays constant on the azimuthal map.
        val first = Projection.azimuthal(40.7128, -74.0060, path[10].first, path[10].second)!!
        val angle = Qibla.norm360(Math.toDegrees(atan2(first.first, first.second)))
        assertEquals(Qibla.bearing(40.7128, -74.0060), angle, 1e-6)
    }

    @Test
    fun azimuthalProjectionKeepsDirectionAndDistanceFromCentre() {
        val lat0 = 24.7136; val lon0 = 46.6753
        val (x, y) = Projection.azimuthal(lat0, lon0, Qibla.KAABA_LAT, Qibla.KAABA_LON)!!
        assertEquals(Qibla.bearing(lat0, lon0), Qibla.norm360(Math.toDegrees(atan2(x, y))), 1e-6)
        assertEquals(Qibla.distanceKm(lat0, lon0), sqrt(x * x + y * y) * Qibla.EARTH_RADIUS_KM, 1e-6)
        assertNull(Projection.azimuthal(lat0, lon0, -lat0, lon0 - 180))
        assertEquals(-170.0, Projection.equirect(100.0, 0.0, -70.0).first, 1e-9)
    }

    @Test
    fun compassHeadingFromSensors() {
        // A level phone whose top edge points to magnetic heading 70°: field (µT) in device coordinates.
        val heading = 70.0
        val bh = 33.0; val bz = 27.0
        val mag = floatArrayOf((-bh * sin(Math.toRadians(heading))).toFloat(), (bh * cos(Math.toRadians(heading))).toFloat(), (-bz).toFloat())
        val r = CompassMath.rotationFrom(floatArrayOf(0f, 0f, 9.81f), mag)
        assertNotNull(r)
        val (h, tilt) = CompassMath.headingAndTilt(r!!)
        assertEquals(heading, h, 1e-3)
        assertEquals(0.0, tilt, 1e-3)
        // Tilted 40° about the X axis (top edge raised): heading of the top edge is unchanged.
        val a = Math.toRadians(40.0)
        val g = floatArrayOf(0f, (9.81 * sin(a)).toFloat(), (9.81 * cos(a)).toFloat())
        // Rotate the field the same way: device frame rotated by +a about X.
        val my = mag[1] * cos(a) + mag[2] * sin(a)
        val mz = -mag[1] * sin(a) + mag[2] * cos(a)
        val r2 = CompassMath.rotationFrom(g, floatArrayOf(mag[0], my.toFloat(), mz.toFloat()))!!
        val (h2, tilt2) = CompassMath.headingAndTilt(r2)
        assertEquals(heading, h2, 1e-3)
        assertEquals(40.0, tilt2, 1e-3)
        assertNull(CompassMath.rotationFrom(floatArrayOf(0f, 0f, 0.1f), mag))
    }

    @Test
    fun issuesAndReliability() {
        fun reading(tilt: Double = 5.0, status: Int = 3, acc: Double? = 5.0, field: Double? = 43.0, age: Long = 50) =
            CompassReading(CompassSource.ROTATION_VECTOR, 10.0, tilt, status, acc, field, age)
        assertEquals(emptySet<CompassIssue>(), CompassMath.issues(reading(), 43.3, PoleZone.NORMAL, true))
        assertEquals(setOf(CompassIssue.NO_SENSOR), CompassMath.issues(null, 43.3, PoleZone.NORMAL, true))
        assertEquals(setOf(CompassIssue.TILT), CompassMath.issues(reading(tilt = 35.0), 43.3, PoleZone.NORMAL, true))
        assertEquals(setOf(CompassIssue.LOW_ACCURACY), CompassMath.issues(reading(status = 1), 43.3, PoleZone.NORMAL, true))
        assertEquals(setOf(CompassIssue.LOW_ACCURACY), CompassMath.issues(reading(acc = 35.0), 43.3, PoleZone.NORMAL, true))
        assertEquals(setOf(CompassIssue.INTERFERENCE), CompassMath.issues(reading(field = 70.0), 43.3, PoleZone.NORMAL, true))
        assertEquals(setOf(CompassIssue.STALE), CompassMath.issues(reading(age = 3_000), 43.3, PoleZone.NORMAL, true))
        val advisory = CompassMath.issues(reading(), 43.3, PoleZone.CAUTION, false)
        assertEquals(setOf(CompassIssue.POLE_CAUTION, CompassIssue.MODEL_EXPIRED), advisory)
        assertFalse(CompassMath.blocksAlignment(advisory))
        assertTrue(CompassMath.blocksAlignment(setOf(CompassIssue.TILT)))
    }

    @Test
    fun angleFilterWrapsAndAlignmentFiresOnce() {
        val f = AngleFilter(0.5)
        f.update(359.0)
        val v = f.update(1.0)
        assertTrue(v > 359.5 || v < 0.5)
        val t = AlignmentTracker()
        assertFalse(t.update(10.0, true))
        assertTrue(t.update(2.0, true))
        assertFalse(t.update(1.0, true))
        assertFalse(t.update(5.0, true)) // still within re-arm distance
        assertFalse(t.update(2.0, true))
        assertFalse(t.update(9.0, true))
        assertTrue(t.update(-2.5, true))
        assertFalse(t.update(20.0, false))
        assertFalse(t.update(1.0, false)) // unreliable never fires
        assertTrue(t.update(1.0, true))
        assertEquals(0, Qibla.compassPoint(359.0))
        assertEquals(4, Qibla.compassPoint(91.0))
        assertEquals(-20.0, Qibla.diff(350.0, 10.0), 1e-9)
    }
}

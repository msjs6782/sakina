package com.sakina.app.core.qibla

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A projected outline: x, y pairs; [closed] outlines can be filled. */
class MapShape(val points: FloatArray, val closed: Boolean)

/**
 * Projects Natural Earth rings for the offline qibla map.
 *
 * Centred (azimuthal equidistant) coordinates are radians of arc: x east, y north of the centre. A polygon
 * that reaches far round the globe is drawn as outline only, because near the centre's antipode the
 * projection tears it apart and a fill would cover the wrong area.
 * World (equirectangular) coordinates are degrees: x longitude, y latitude.
 */
object MapGeometry {
    /** Polygons entirely within this angular distance of the centre are filled. */
    const val FILL_LIMIT = 2.6

    /** Points beyond this distance (near the antipode) are dropped from outlines. */
    const val DROP_LIMIT = 2.9

    /** A step longer than this (radians on the map) is a tear, not a coastline. */
    const val JUMP = 0.6

    fun centered(lat0: Double, lon0: Double, rings: GeoRings, polygons: Boolean): List<MapShape> {
        val p0 = Math.toRadians(lat0)
        val sp0 = sin(p0)
        val cp0 = cos(p0)
        val out = ArrayList<MapShape>()
        for (ring in rings.rings) {
            val n = ring.size / 2
            val xy = FloatArray(n * 2)
            val dist = DoubleArray(n)
            var maxC = 0.0
            for (i in 0 until n) {
                val p = Math.toRadians(ring[2 * i + 1].toDouble())
                val dl = Math.toRadians(ring[2 * i].toDouble() - lon0)
                val sp = sin(p)
                val cp = cos(p)
                val cdl = cos(dl)
                val cosC = (sp0 * sp + cp0 * cp * cdl).coerceIn(-1.0, 1.0)
                val c = acos(cosC)
                val k = if (c < 1e-9) 1.0 else c / sin(c).coerceAtLeast(1e-9)
                xy[2 * i] = (k * cp * sin(dl)).toFloat()
                xy[2 * i + 1] = (k * (cp0 * sp - sp0 * cp * cdl)).toFloat()
                dist[i] = c
                if (c > maxC) maxC = c
            }
            if (polygons && maxC < FILL_LIMIT) {
                out += MapShape(xy, true)
                continue
            }
            // Outline pieces, broken at the antipode and at tears.
            var start = -1
            fun flush(end: Int) {
                if (start >= 0 && end - start >= 2) out += MapShape(xy.copyOfRange(start * 2, end * 2), false)
                start = -1
            }
            for (i in 0 until n) {
                if (dist[i] > DROP_LIMIT) { flush(i); continue }
                if (start >= 0) {
                    val dx = xy[2 * i] - xy[2 * i - 2]
                    val dy = xy[2 * i + 1] - xy[2 * i - 1]
                    if (hypot(dx.toDouble(), dy.toDouble()) > JUMP) flush(i)
                }
                if (start < 0) start = i
            }
            flush(n)
        }
        return out
    }

    fun world(rings: GeoRings, polygons: Boolean): List<MapShape> = rings.rings.map { MapShape(it, polygons) }

    /** The great circle to the Kaaba on the centred map: one straight piece from the centre. */
    fun centeredPath(lat0: Double, lon0: Double, lat1: Double = Qibla.KAABA_LAT, lon1: Double = Qibla.KAABA_LON): FloatArray {
        val pts = Qibla.greatCircle(lat0, lon0, lat1, lon1, 64)
        val out = FloatArray(pts.size * 2)
        pts.forEachIndexed { i, (la, lo) ->
            val p = Projection.azimuthal(lat0, lon0, la, lo) ?: (0.0 to 0.0)
            out[2 * i] = p.first.toFloat(); out[2 * i + 1] = p.second.toFloat()
        }
        return out
    }

    /** The great circle on the world map, split where it crosses the ±180° meridian. */
    fun worldPath(lat0: Double, lon0: Double, lat1: Double = Qibla.KAABA_LAT, lon1: Double = Qibla.KAABA_LON): List<FloatArray> {
        val pts = Qibla.greatCircle(lat0, lon0, lat1, lon1, 256)
        val pieces = ArrayList<FloatArray>()
        var cur = ArrayList<Float>()
        var prevLon: Double? = null
        for ((la, lo) in pts) {
            if (prevLon != null && abs(lo - prevLon) > 180) {
                if (cur.size >= 4) pieces += cur.toFloatArray()
                cur = ArrayList()
            }
            cur += lo.toFloat(); cur += la.toFloat()
            prevLon = lo
        }
        if (cur.size >= 4) pieces += cur.toFloatArray()
        return pieces
    }
}

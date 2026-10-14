package com.sakina.app.core.qibla

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The magnetic field at a point, in the geodetic frame (north, east, down), nanotesla and degrees. */
data class MagneticField(
    val x: Double,
    val y: Double,
    val z: Double,
    /** Declination: angle of magnetic north east of true north. */
    val declination: Double,
    val inclination: Double,
    val horizontal: Double,
    val total: Double,
    /** Declination change per year. */
    val declinationPerYear: Double,
) {
    /** WMM's own reliability zones near the magnetic poles, where a compass's horizontal pull is weak. */
    val zone: PoleZone
        get() = when {
            horizontal < 2000 -> PoleZone.BLACKOUT
            horizontal < 6000 -> PoleZone.CAUTION
            else -> PoleZone.NORMAL
        }
}

enum class PoleZone { NORMAL, CAUTION, BLACKOUT }

/**
 * The World Magnetic Model (spherical harmonics to degree 12), evaluated from NOAA's WMM.COF file.
 * Follows the WMM technical report: geodetic to geocentric conversion on WGS-84, Schmidt semi-normalised
 * Legendre functions, secular variation from the model epoch, and rotation back to the geodetic frame.
 */
class Wmm private constructor(
    val epoch: Double,
    val name: String,
    val releaseDate: String,
    private val g: Array<DoubleArray>,
    private val h: Array<DoubleArray>,
    private val gDot: Array<DoubleArray>,
    private val hDot: Array<DoubleArray>,
) {
    /** The model's stated life: five years from the epoch. */
    val validUntil: Double get() = epoch + 5.0

    fun isValidAt(year: Double) = year >= epoch && year < validUntil

    /**
     * Field at geodetic [latitude]/[longitude] (degrees), [heightKm] above the WGS-84 ellipsoid, at a
     * decimal [year]. Outside the model's life the result is still computed; callers check [isValidAt].
     */
    fun field(latitude: Double, longitude: Double, heightKm: Double, year: Double): MagneticField {
        val now = compute(latitude, longitude, heightKm, year)
        // The field changes linearly with time, so its rate is exact from any step; the declination's rate is
        // the instantaneous one (dD/dt = (X·dY − Y·dX) / H²), as NOAA's software reports it.
        val later = compute(latitude, longitude, heightKm, year + 1.0)
        val dx = later.x - now.x
        val dy = later.y - now.y
        val h2 = now.horizontal * now.horizontal
        val dd = if (h2 > 0) Math.toDegrees((now.x * dy - now.y * dx) / h2) else 0.0
        return now.copy(declinationPerYear = dd)
    }

    private fun compute(latitude: Double, longitude: Double, heightKm: Double, year: Double): MagneticField {
        val dt = year - epoch
        val lat = Math.toRadians(latitude)
        val lon = Math.toRadians(longitude)

        // Geodetic to geocentric spherical coordinates (WGS-84).
        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val rc = A / sqrt(1 - E2 * sinLat * sinLat)
        val p = (rc + heightKm) * cosLat
        val zc = (rc * (1 - E2) + heightKm) * sinLat
        val r = sqrt(p * p + zc * zc)
        val latC = kotlin.math.asin(zc / r)

        // Legendre functions of sin(geocentric latitude) = cos(colatitude), Schmidt semi-normalised.
        val ct = sin(latC)
        val st = cos(latC)
        val pnm = Array(N + 1) { DoubleArray(N + 1) }
        val dpnm = Array(N + 1) { DoubleArray(N + 1) }
        pnm[0][0] = 1.0
        for (n in 1..N) {
            for (m in 0..n) {
                if (n == m) {
                    pnm[n][n] = st * pnm[n - 1][n - 1]
                    dpnm[n][n] = st * dpnm[n - 1][n - 1] + ct * pnm[n - 1][n - 1]
                } else if (n == 1) {
                    pnm[1][0] = ct * pnm[0][0]
                    dpnm[1][0] = ct * dpnm[0][0] - st * pnm[0][0]
                } else {
                    val k = if (n > 1) (((n - 1) * (n - 1) - m * m).toDouble() / ((2 * n - 1) * (2 * n - 3))) else 0.0
                    val p2 = if (n - 2 >= m) pnm[n - 2][m] else 0.0
                    val d2 = if (n - 2 >= m) dpnm[n - 2][m] else 0.0
                    pnm[n][m] = ct * pnm[n - 1][m] - k * p2
                    dpnm[n][m] = ct * dpnm[n - 1][m] - st * pnm[n - 1][m] - k * d2
                }
            }
        }
        // Gauss to Schmidt semi-normalisation.
        val sn = Array(N + 1) { DoubleArray(N + 1) }
        sn[0][0] = 1.0
        for (n in 1..N) {
            sn[n][0] = sn[n - 1][0] * (2 * n - 1) / n
            for (m in 1..n) {
                val j = if (m == 1) 2.0 else 1.0
                sn[n][m] = sn[n][m - 1] * sqrt((n - m + 1) * j / (n + m))
            }
        }

        var bTheta = 0.0 // d/dθ terms → north (−Bθ)
        var bPhi = 0.0 // east
        var bR = 0.0 // −down
        val ratio = RE / r
        var ar = ratio * ratio
        for (n in 1..N) {
            ar *= ratio // (a/r)^(n+2)
            for (m in 0..n) {
                val gnm = g[n][m] + dt * gDot[n][m]
                val hnm = h[n][m] + dt * hDot[n][m]
                val cm = cos(m * lon)
                val sm = sin(m * lon)
                val pS = sn[n][m] * pnm[n][m]
                val dS = sn[n][m] * dpnm[n][m]
                val gc = gnm * cm + hnm * sm
                bTheta += ar * gc * dS
                bPhi += ar * m * (gnm * sm - hnm * cm) * pS
                bR += ar * (n + 1) * gc * pS
            }
        }
        val xPrime = bTheta
        val yPrime = if (st > 1e-10) bPhi / st else bPhi / 1e-10
        val zPrime = -bR

        val psi = latC - lat
        val x = xPrime * cos(psi) - zPrime * sin(psi)
        val z = xPrime * sin(psi) + zPrime * cos(psi)
        val y = yPrime
        val hor = sqrt(x * x + y * y)
        return MagneticField(
            x = x, y = y, z = z,
            declination = Math.toDegrees(atan2(y, x)),
            inclination = Math.toDegrees(atan2(z, hor)),
            horizontal = hor,
            total = sqrt(hor * hor + z * z),
            declinationPerYear = 0.0,
        )
    }

    companion object {
        const val N = 12
        private const val A = 6378.137
        private const val F = 1 / 298.257223563
        private const val E2 = F * (2 - F)
        private const val RE = 6371.2

        /** Parses NOAA's WMM.COF text: a header line, then "n m g h gDot hDot" rows, ended by a line of 9s. */
        fun parse(cof: String): Wmm {
            val lines = cof.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val head = lines.first().split(Regex("\\s+"))
            require(head.size >= 2) { "Bad WMM header" }
            val g = Array(N + 1) { DoubleArray(N + 1) }
            val h = Array(N + 1) { DoubleArray(N + 1) }
            val gd = Array(N + 1) { DoubleArray(N + 1) }
            val hd = Array(N + 1) { DoubleArray(N + 1) }
            var rows = 0
            for (line in lines.drop(1)) {
                if (line.startsWith("9999")) break
                val f = line.split(Regex("\\s+"))
                require(f.size == 6) { "Bad WMM row: $line" }
                val n = f[0].toInt()
                val m = f[1].toInt()
                require(n in 1..N && m in 0..n) { "Bad WMM degree: $line" }
                g[n][m] = f[2].toDouble(); h[n][m] = f[3].toDouble()
                gd[n][m] = f[4].toDouble(); hd[n][m] = f[5].toDouble()
                rows++
            }
            require(rows == (N + 1) * (N + 2) / 2 - 1) { "WMM file has $rows rows" }
            return Wmm(head[0].toDouble(), head[1], head.getOrElse(2) { "" }, g, h, gd, hd)
        }

        /** Decimal year of a date, as WMM uses it. */
        fun decimalYear(year: Int, dayOfYear: Int, lengthOfYear: Int): Double = year + (dayOfYear - 1).toDouble() / lengthOfYear
    }
}

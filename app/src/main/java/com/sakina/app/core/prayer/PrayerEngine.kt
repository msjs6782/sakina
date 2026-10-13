package com.sakina.app.core.prayer

import com.sakina.app.core.calendar.UmmAlQura
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.floor

/**
 * Calculates the day's prayer times on the device. Nothing is guessed: a time that cannot be calculated
 * is returned as [TimeStatus.UNAVAILABLE] together with the reason in [DayTimes.issues].
 */
object PrayerEngine {
    private const val MIN_GAP_SECONDS = 5 * 60L

    fun compute(date: LocalDate, point: GeoPoint, zone: ZoneId, config: CalcConfig): DayTimes {
        require(point.isValid) { "Invalid coordinates" }
        val method = config.method
        val issues = mutableSetOf<DayIssue>()

        // The solar day whose noon falls on this civil date. It differs from the civil date where the
        // time zone is far from the longitude's solar time (for example UTC+14 at 157°W).
        val solarDate = solarDateFor(date, point, zone)
        val today = solarDay(solarDate, point)
        val next = solarDate.plusDays(1)
        val tomorrow = solarDay(next, point)

        val dayStart = solarDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val nextStart = next.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        // Seconds since the epoch, truncated to whole seconds, or null when the event does not happen.
        fun at(hours: Double, base: Long = dayStart): Long? {
            if (hours.isNaN() || hours.isInfinite()) return null
            val h = floor(hours)
            val m = floor((hours - h) * 60.0)
            val s = floor((hours - (h + m / 60.0)) * 3600)
            return base + h.toLong() * 3600 + m.toLong() * 60 + s.toLong()
        }

        val transit = at(today.transit)
        val sunrise = at(today.sunrise)
        val sunset = at(today.sunset)
        val nextSunrise = at(tomorrow.sunrise, nextStart)

        if (sunrise == null || sunset == null) {
            issues += if (today.noonAltitudeDegrees < -50.0 / 60.0) DayIssue.NO_SUNRISE else DayIssue.NO_SUNSET
        } else if (nextSunrise == null) {
            issues += DayIssue.NIGHT_UNKNOWN
        }

        val statuses = mutableMapOf<PrayerKey, TimeStatus>()
        val raw = mutableMapOf<PrayerKey, Long?>()

        raw[PrayerKey.DHUHR] = transit
        raw[PrayerKey.SUNRISE] = sunrise
        raw[PrayerKey.MAGHRIB] = sunset
        raw[PrayerKey.ASR] = at(today.afternoon(config.asr.shadowFactor))

        val night = if (sunset != null && nextSunrise != null) nextSunrise - sunset else null
        val fajrPortion: Double
        val ishaPortion: Double
        when (config.highLatitude) {
            HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> { fajrPortion = 0.5; ishaPortion = 0.5 }
            HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> { fajrPortion = 1.0 / 7.0; ishaPortion = 1.0 / 7.0 }
            HighLatitudeRule.TWILIGHT_ANGLE -> { fajrPortion = method.fajrAngle / 60.0; ishaPortion = method.ishaAngle / 60.0 }
        }

        // Fajr
        if (sunrise != null && sunset != null) {
            val byAngle = at(today.hoursForAltitude(-method.fajrAngle, afterTransit = false))
            val safe = night?.let { sunrise - (fajrPortion * night).toLong() }
            val angleOk = byAngle != null && sunrise - byAngle >= MIN_GAP_SECONDS && (safe == null || byAngle >= safe)
            when {
                angleOk -> { raw[PrayerKey.FAJR] = byAngle; statuses[PrayerKey.FAJR] = TimeStatus.CALCULATED }
                safe != null -> { raw[PrayerKey.FAJR] = safe; statuses[PrayerKey.FAJR] = TimeStatus.HIGH_LATITUDE_RULE }
                else -> raw[PrayerKey.FAJR] = null
            }
        } else {
            raw[PrayerKey.FAJR] = null
        }

        // Isha
        var intervalUsed: Int? = null
        val ramadan: Boolean? = UmmAlQura.isRamadan(date)
        if (sunset != null && sunrise != null) {
            if (method.ishaIntervalMinutes > 0) {
                intervalUsed = when (config.ummAlQuraIsha) {
                    UmmAlQuraIsha.FIXED_90 -> 90
                    UmmAlQuraIsha.FIXED_120 -> 120
                    UmmAlQuraIsha.AUTO -> {
                        if (ramadan == null) issues += DayIssue.HIJRI_OUT_OF_RANGE
                        if (ramadan == true) 120 else 90
                    }
                }
                raw[PrayerKey.ISHA] = sunset + intervalUsed * 60L
                statuses[PrayerKey.ISHA] = TimeStatus.CALCULATED
            } else {
                val byAngle = at(today.hoursForAltitude(-method.ishaAngle, afterTransit = true))
                val safe = night?.let { sunset + (ishaPortion * night).toLong() }
                val angleOk = byAngle != null && byAngle - sunset >= MIN_GAP_SECONDS && (safe == null || byAngle <= safe)
                when {
                    angleOk -> { raw[PrayerKey.ISHA] = byAngle; statuses[PrayerKey.ISHA] = TimeStatus.CALCULATED }
                    safe != null -> { raw[PrayerKey.ISHA] = safe; statuses[PrayerKey.ISHA] = TimeStatus.HIGH_LATITUDE_RULE }
                    else -> raw[PrayerKey.ISHA] = null
                }
            }
        } else {
            raw[PrayerKey.ISHA] = null
        }

        // Withhold everything if the available times are out of order (close to the polar circles).
        val ordered = PrayerKey.entries.mapNotNull { k -> raw[k]?.let { k to it } }
        val inOrder = ordered.zipWithNext().all { (a, b) -> b.second - a.second >= MIN_GAP_SECONDS }
        if (!inOrder) issues += DayIssue.OUT_OF_ORDER

        val times = PrayerKey.entries.map { key ->
            val r = raw[key]
            if (r == null || !inOrder) {
                PrayerTime(key, null, TimeStatus.UNAVAILABLE)
            } else {
                val methodMinutes = if (key == PrayerKey.DHUHR) method.dhuhrMinutes else 0
                val adjusted = r + (methodMinutes + config.offsets[key]) * 60L
                PrayerTime(key, Instant.ofEpochSecond(roundToMinute(adjusted)), statuses[key] ?: TimeStatus.CALCULATED)
            }
        }

        return DayTimes(
            date = date,
            zone = zone,
            config = config,
            times = times,
            sunrise = if (inOrder) sunrise?.let { Instant.ofEpochSecond(roundToMinute(it)) } else null,
            sunset = if (inOrder) sunset?.let { Instant.ofEpochSecond(roundToMinute(it)) } else null,
            ishaIntervalMinutes = intervalUsed,
            ramadan = ramadan,
            issues = issues,
        )
    }

    private fun solarDay(d: LocalDate, p: GeoPoint) = SolarDay(d.year, d.monthValue, d.dayOfMonth, p.latitude, p.longitude)

    internal fun solarDateFor(date: LocalDate, point: GeoPoint, zone: ZoneId): LocalDate {
        var candidate = date
        repeat(3) {
            val transit = solarDay(candidate, point).transit
            val start = candidate.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
            val local = Instant.ofEpochSecond(start + (transit * 3600).toLong()).atZone(zone).toLocalDate()
            val shift = java.time.temporal.ChronoUnit.DAYS.between(local, date)
            if (shift == 0L) return candidate
            candidate = candidate.plusDays(shift)
        }
        return candidate
    }

    /** Nearest minute; exactly 30 seconds rounds up. */
    internal fun roundToMinute(epochSecond: Long): Long {
        val s = Math.floorMod(epochSecond, 60L)
        val base = epochSecond - s
        return if (s >= 30) base + 60 else base
    }
}
